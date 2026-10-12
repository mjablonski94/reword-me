package dev.mjablonski.rewordme.android.features.settings.presentation

import androidx.lifecycle.viewModelScope
import dev.mjablonski.rewordme.android.arch.MviViewModel
import dev.mjablonski.rewordme.android.common.UiError
import dev.mjablonski.rewordme.android.features.floating.data.FloatingIconSettings
import dev.mjablonski.rewordme.domain.ProviderAvailability
import dev.mjablonski.rewordme.domain.usecase.HasApiKeyUseCase
import dev.mjablonski.rewordme.domain.usecase.ListModelsParams
import dev.mjablonski.rewordme.domain.usecase.ListModelsUseCase
import dev.mjablonski.rewordme.domain.usecase.LoadConfigUseCase
import dev.mjablonski.rewordme.domain.usecase.SaveApiKeyParams
import dev.mjablonski.rewordme.domain.usecase.SaveApiKeyUseCase
import dev.mjablonski.rewordme.domain.usecase.UpdateConfigUseCase
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordConfig
import dev.mjablonski.rewordme.models.RewriteRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The desktop SettingsViewModel's provider/rewriting/general behaviour,
 * minus what the platform cannot have (hotkey, launch at login, account
 * CLIs, managed local models).
 *
 * Persistence discipline: text fields edit state drafts synchronously and
 * commit debounced; every write runs on [appScope] so leaving the screen
 * mid-save cannot lose it, and pending debounces flush in onCleared.
 */
class SettingsViewModel(
    private val loadConfig: LoadConfigUseCase,
    private val updateConfig: UpdateConfigUseCase,
    private val hasApiKey: HasApiKeyUseCase,
    private val saveApiKey: SaveApiKeyUseCase,
    private val listModels: ListModelsUseCase,
    availability: ProviderAvailability,
    private val floatingIcon: FloatingIconSettings,
    private val appScope: CoroutineScope
) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>(
    SettingsState(providers = availability.available)
) {

    private var modelLoadId = 0L
    private var userEdited = false
    private val pendingCommits = mutableMapOf<String, Job>()

    init {
        refreshFloatingIcon()
        viewModelScope.launch {
            val config = loadConfig.safeAction(Unit).getOrDefault(RewordConfig())
            val storedKey = hasApiKey.safeAction(config.provider).getOrDefault(false)
            // A snapshot loaded before the user's first edit must not undo
            // that edit: apply only while the screen is still pristine.
            if (!userEdited) {
                setState {
                    copy(
                        config = config,
                        hasStoredKey = storedKey,
                        basePromptDraft = config.basePrompt,
                        ollamaHostDraft = config.ollamaHost
                    )
                }
            }
        }
    }

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SelectTab -> setState { copy(tab = intent.tab) }
            is SettingsIntent.SelectProvider -> selectProvider(intent.provider)
            is SettingsIntent.KeyChanged ->
                setState { copy(keyDraft = intent.value, keySaved = false, keySaveFailed = false) }
            SettingsIntent.SaveKey -> saveKey(currentState.keyDraft.trim())
            SettingsIntent.RemoveKey -> removeKey()
            SettingsIntent.LoadModels -> loadModels()
            is SettingsIntent.SelectModel -> {
                userEdited = true
                persist { it.selectingModel(intent.model) }
            }
            is SettingsIntent.OllamaHostChanged -> ollamaHostChanged(intent.host)
            is SettingsIntent.BasePromptChanged -> basePromptChanged(intent.prompt)
            is SettingsIntent.NewRuleChanged -> setState { copy(newRuleDraft = intent.value) }
            SettingsIntent.AddRule -> addRule()
            is SettingsIntent.ToggleRule -> {
                userEdited = true
                updateRule(intent.id) { it.copy(isEnabled = intent.enabled) }
            }
            is SettingsIntent.RuleTextChanged -> ruleTextChanged(intent.id, intent.text)
            is SettingsIntent.RemoveRule -> {
                userEdited = true
                setState { copy(ruleDrafts = ruleDrafts - intent.id) }
                pendingCommits.remove(commitKeyForRule(intent.id))?.cancel()
                persist { it.copy(rules = it.rules.filterNot { rule -> rule.id == intent.id }) }
            }
            is SettingsIntent.OpenUrl -> sendEffect(SettingsEffect.OpenUrl(intent.url))
            is SettingsIntent.FloatingIconToggled -> floatingIconToggled(intent.on)
            SettingsIntent.FloatingDisclosureAccepted -> {
                floatingIcon.iconEnabled = true
                setState { copy(floatingDisclosure = false, floatingIconOn = true) }
                sendEffect(SettingsEffect.OpenAccessibilitySettings)
            }
            SettingsIntent.FloatingDisclosureDismissed ->
                setState { copy(floatingDisclosure = false) }
            SettingsIntent.RefreshFloatingIcon -> refreshFloatingIcon()
            SettingsIntent.OpenAccessibilitySettings ->
                sendEffect(SettingsEffect.OpenAccessibilitySettings)
            SettingsIntent.OpenAppInfo -> sendEffect(SettingsEffect.OpenAppInfo)
        }
    }

    /**
     * Switching on needs Android's accessibility switch, which only the user
     * can flip: when it is off, explain first what the service can see, and
     * only then send them there. Switching off is RewordMe's own switch -
     * immediate, and no trip through Android settings.
     */
    private fun floatingIconToggled(on: Boolean) {
        when {
            !on -> {
                floatingIcon.iconEnabled = false
                setState { copy(floatingIconOn = false) }
            }
            currentState.floatingServiceOn -> {
                floatingIcon.iconEnabled = true
                setState { copy(floatingIconOn = true) }
            }
            else -> setState { copy(floatingDisclosure = true) }
        }
    }

    private fun refreshFloatingIcon() {
        val serviceOn = floatingIcon.serviceEnabled()
        val iconOn = floatingIcon.iconEnabled
        setState { copy(floatingServiceOn = serviceOn, floatingIconOn = iconOn) }
    }

    private fun selectProvider(provider: ProviderKind) {
        // Re-picking the current entry is a no-op, exactly like the desktop:
        // without this, the dropdown's onSelect wipes drafts and abandons an
        // in-flight model load.
        if (provider == currentState.config.provider) return
        userEdited = true
        modelLoadId++
        setState {
            copy(
                keyDraft = "", keySaved = false, keySaveFailed = false,
                models = emptyList(), isLoadingModels = false, modelsError = null
            )
        }
        persist(
            onSuccess = {
                viewModelScope.launch {
                    val storedKey = hasApiKey.safeAction(provider).getOrDefault(false)
                    setState { copy(hasStoredKey = storedKey) }
                }
            }
        ) { it.selectingProvider(provider) }
    }

    private fun saveKey(value: String) {
        val provider = currentState.config.provider
        // The write itself runs on the app scope: tapping Save and leaving
        // the screen immediately must still persist the key.
        val work = appScope.async {
            saveApiKey.safeAction(SaveApiKeyParams(provider, value)).getOrDefault(false)
        }
        viewModelScope.launch {
            val stored = work.await()
            setState {
                copy(
                    keySaved = stored && value.isNotEmpty(),
                    keySaveFailed = !stored,
                    hasStoredKey = if (stored) value.isNotEmpty() else hasStoredKey,
                    keyDraft = if (stored) "" else keyDraft
                )
            }
        }
    }

    private fun removeKey() = saveKey("")

    private fun loadModels() {
        val requestId = ++modelLoadId
        val provider = currentState.config.provider
        val draft = currentState.keyDraft
        setState { copy(isLoadingModels = true, modelsError = null) }
        viewModelScope.launch {
            val outcome = listModels.safeAction(ListModelsParams(provider, draft))
            if (requestId != modelLoadId) return@launch
            outcome.fold(
                onSuccess = { models ->
                    setState { copy(models = models, isLoadingModels = false) }
                },
                onFailure = { failure ->
                    setState {
                        copy(isLoadingModels = false, modelsError = UiError.of(failure))
                    }
                }
            )
        }
    }

    private fun ollamaHostChanged(host: String) {
        userEdited = true
        // A selected model and a loaded catalog belong to the old server.
        setState { copy(ollamaHostDraft = host, models = emptyList(), modelsError = null) }
        scheduleCommit("ollamaHost") {
            val value = currentState.ollamaHostDraft
            persist { it.selectingModel(null).copy(ollamaHost = value) }
        }
    }

    private fun basePromptChanged(prompt: String) {
        userEdited = true
        setState { copy(basePromptDraft = prompt) }
        scheduleCommit("basePrompt") {
            val value = currentState.basePromptDraft
            persist { it.copy(basePrompt = value) }
        }
    }

    private fun ruleTextChanged(id: String, text: String) {
        userEdited = true
        setState { copy(ruleDrafts = ruleDrafts + (id to text)) }
        scheduleCommit(commitKeyForRule(id)) {
            val value = currentState.ruleDrafts[id] ?: return@scheduleCommit
            updateRule(id) { it.copy(text = value) }
        }
    }

    private fun addRule() {
        val text = currentState.newRuleDraft.trim()
        if (text.isEmpty()) return
        userEdited = true
        setState { copy(newRuleDraft = "") }
        persist { it.copy(rules = it.rules + RewriteRule(text = text)) }
    }

    private fun updateRule(id: String, transform: (RewriteRule) -> RewriteRule) {
        persist { config ->
            config.copy(
                rules = config.rules.map { rule -> if (rule.id == id) transform(rule) else rule }
            )
        }
    }

    /** Debounce per field: one write per typing pause, not per keystroke. */
    private fun scheduleCommit(key: String, commit: () -> Unit) {
        pendingCommits.remove(key)?.cancel()
        pendingCommits[key] = viewModelScope.launch {
            delay(COMMIT_DEBOUNCE_MILLIS)
            pendingCommits.remove(key)
            commit()
        }
    }

    /**
     * All config writes go through here: executed on [appScope] so they
     * survive the view model, serialized by UpdateConfigUseCase's mutex,
     * state updated only if the screen is still alive. The transform comes
     * last so call sites read as `persist { it.copy(...) }`.
     */
    private fun persist(
        onSuccess: (RewordConfig) -> Unit = {},
        transform: (RewordConfig) -> RewordConfig
    ) {
        val work = appScope.async { updateConfig.safeAction(transform) }
        viewModelScope.launch {
            work.await().onSuccess { updated ->
                setState { copy(config = updated) }
                onSuccess(updated)
            }
        }
    }

    override fun onCleared() {
        // Debounces still pending die with the scope; their latest values
        // must not. Snapshot and write them out on the app scope.
        val state = currentState
        val dirtyKeys = pendingCommits.keys.toList()
        pendingCommits.values.forEach(Job::cancel)
        dirtyKeys.forEach { key ->
            when {
                key == "basePrompt" -> appScope.launch {
                    updateConfig.safeAction { it.copy(basePrompt = state.basePromptDraft) }
                }
                key == "ollamaHost" -> appScope.launch {
                    updateConfig.safeAction {
                        it.selectingModel(null).copy(ollamaHost = state.ollamaHostDraft)
                    }
                }
                key.startsWith(RULE_COMMIT_PREFIX) -> {
                    val id = key.removePrefix(RULE_COMMIT_PREFIX)
                    val text = state.ruleDrafts[id] ?: return@forEach
                    appScope.launch {
                        updateConfig.safeAction { config ->
                            config.copy(
                                rules = config.rules.map { rule ->
                                    if (rule.id == id) rule.copy(text = text) else rule
                                }
                            )
                        }
                    }
                }
            }
        }
        super.onCleared()
    }

    private fun commitKeyForRule(id: String) = RULE_COMMIT_PREFIX + id

    private companion object {
        const val COMMIT_DEBOUNCE_MILLIS = 400L
        const val RULE_COMMIT_PREFIX = "rule:"
    }
}

package dev.mjablonski.rewordme.android.features.settings.presentation

import androidx.lifecycle.viewModelScope
import dev.mjablonski.rewordme.android.arch.MviViewModel
import dev.mjablonski.rewordme.android.common.UiError
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
import kotlinx.coroutines.launch

/**
 * The desktop SettingsViewModel's provider/rewriting/general behaviour,
 * minus what the platform cannot have (hotkey, launch at login, account
 * CLIs, managed local models). Every config edit persists immediately,
 * exactly like the desktop.
 */
class SettingsViewModel(
    private val loadConfig: LoadConfigUseCase,
    private val updateConfig: UpdateConfigUseCase,
    private val hasApiKey: HasApiKeyUseCase,
    private val saveApiKey: SaveApiKeyUseCase,
    private val listModels: ListModelsUseCase,
    availability: ProviderAvailability
) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>(
    SettingsState(providers = availability.available)
) {

    private var modelLoadId = 0L

    init {
        viewModelScope.launch {
            val config = loadConfig.safeAction(Unit).getOrDefault(RewordConfig())
            val storedKey = hasApiKey.safeAction(config.provider).getOrDefault(false)
            setState { copy(config = config, hasStoredKey = storedKey) }
        }
    }

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SelectTab -> setState { copy(tab = intent.tab) }
            is SettingsIntent.SelectProvider -> selectProvider(intent.provider)
            is SettingsIntent.KeyChanged ->
                setState { copy(keyDraft = intent.value, keySaved = false, keySaveFailed = false) }
            SettingsIntent.SaveKey -> saveKey()
            SettingsIntent.LoadModels -> loadModels()
            is SettingsIntent.SelectModel -> update { it.selectingModel(intent.model) }
            is SettingsIntent.OllamaHostChanged -> update { it.copy(ollamaHost = intent.host) }
            is SettingsIntent.BasePromptChanged -> update { it.copy(basePrompt = intent.prompt) }
            is SettingsIntent.NewRuleChanged -> setState { copy(newRuleDraft = intent.value) }
            SettingsIntent.AddRule -> addRule()
            is SettingsIntent.ToggleRule ->
                updateRule(intent.id) { it.copy(isEnabled = intent.enabled) }
            is SettingsIntent.RuleTextChanged ->
                updateRule(intent.id) { it.copy(text = intent.text) }
            is SettingsIntent.RemoveRule ->
                update { it.copy(rules = it.rules.filterNot { rule -> rule.id == intent.id }) }
            is SettingsIntent.OpenUrl -> sendEffect(SettingsEffect.OpenUrl(intent.url))
        }
    }

    private fun selectProvider(provider: ProviderKind) {
        // Stale model lists and key drafts belong to the previous provider.
        modelLoadId++
        setState {
            copy(
                keyDraft = "", keySaved = false, keySaveFailed = false,
                models = emptyList(), isLoadingModels = false, modelsError = null
            )
        }
        viewModelScope.launch {
            val updated = updateConfig
                .safeAction { it.selectingProvider(provider) }
                .getOrElse { return@launch }
            val storedKey = hasApiKey.safeAction(provider).getOrDefault(false)
            setState { copy(config = updated, hasStoredKey = storedKey) }
        }
    }

    private fun saveKey() {
        val provider = currentState.config.provider
        val draft = currentState.keyDraft.trim()
        viewModelScope.launch {
            val stored = saveApiKey
                .safeAction(SaveApiKeyParams(provider, draft))
                .getOrDefault(false)
            setState {
                copy(
                    keySaved = stored,
                    keySaveFailed = !stored,
                    hasStoredKey = if (stored) draft.isNotEmpty() else hasStoredKey,
                    keyDraft = if (stored) "" else keyDraft
                )
            }
        }
    }

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

    private fun addRule() {
        val text = currentState.newRuleDraft.trim()
        if (text.isEmpty()) return
        setState { copy(newRuleDraft = "") }
        update { it.copy(rules = it.rules + RewriteRule(text = text)) }
    }

    private fun updateRule(id: String, transform: (RewriteRule) -> RewriteRule) {
        update { config ->
            config.copy(
                rules = config.rules.map { rule -> if (rule.id == id) transform(rule) else rule }
            )
        }
    }

    private fun update(transform: (RewordConfig) -> RewordConfig) {
        viewModelScope.launch {
            updateConfig.safeAction(transform).onSuccess { updated ->
                setState { copy(config = updated) }
            }
        }
    }
}

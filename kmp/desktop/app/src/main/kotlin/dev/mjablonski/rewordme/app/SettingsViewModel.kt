package dev.mjablonski.rewordme.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.mjablonski.rewordme.domain.ModelSelection
import dev.mjablonski.rewordme.models.HotkeyConfig
import dev.mjablonski.rewordme.models.ModelInfo
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordConfig
import dev.mjablonski.rewordme.models.RewordError
import dev.mjablonski.rewordme.models.RewriteRule
import dev.mjablonski.rewordme.models.RuleKind
import dev.mjablonski.rewordme.platform.StartupRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings state. Every edit is written straight to disk - there is no OK or
 * Cancel button, matching the macOS app.
 */
class SettingsViewModel(
    private val dependencies: AppDependencies,
    private val scope: CoroutineScope,
    private val hotkeys: HotkeyBinder
) {
    var config by mutableStateOf(dependencies.configStore.load())
        private set
    var apiKey by mutableStateOf("")
        private set
    var keySaved by mutableStateOf(false)
        private set

    var availableModels by mutableStateOf(emptyList<ModelInfo>())
        private set
    var isLoadingModels by mutableStateOf(false)
        private set
    var modelsError by mutableStateOf<String?>(null)
        private set

    var hotkeyStatus by mutableStateOf(HotkeyStatus.Active)
        private set
    var isRecordingHotkey by mutableStateOf(false)
        private set

    private var modelLoad: Job? = null

    init {
        apiKey = dependencies.keyStore.apiKey(config.provider) ?: ""
    }

    val launchAtLoginSupported: Boolean get() = StartupRegistration.isSupported

    var launchAtLogin by mutableStateOf(StartupRegistration.isEnabled)
        private set

    /** Reads the registry back, so the switch snaps to whatever actually stuck. */
    fun toggleLaunchAtLogin(enabled: Boolean) {
        StartupRegistration.isEnabled = enabled
        launchAtLogin = StartupRegistration.isEnabled
    }

    /** The model "Automatic" would pick right now, once the list is known. */
    val automaticModelHint: String?
        get() = ModelSelection.defaultModel(config.provider, availableModels)?.id

    fun selectProvider(provider: ProviderKind) {
        if (provider == config.provider) return
        // A model id from one provider is meaningless to another.
        update(config.copy(provider = provider, model = null))
        apiKey = dependencies.keyStore.apiKey(provider) ?: ""
        keySaved = false
        availableModels = emptyList()
        modelsError = null
    }

    fun editApiKey(value: String) {
        apiKey = value
        keySaved = false
    }

    fun saveApiKey() {
        dependencies.keyStore.setApiKey(config.provider, apiKey)
        keySaved = true
    }

    fun setOllamaHost(host: String) = update(config.copy(ollamaHost = host))

    fun selectModel(model: String?) = update(config.copy(model = model))

    fun setBasePrompt(prompt: String) = update(config.copy(basePrompt = prompt))

    fun addRule() =
        update(config.copy(rules = config.rules + RewriteRule(kind = RuleKind.DO, text = "")))

    fun removeRule(id: String) =
        update(config.copy(rules = config.rules.filterNot { it.id == id }))

    fun updateRule(id: String, transform: (RewriteRule) -> RewriteRule) =
        update(config.copy(rules = config.rules.map { if (it.id == id) transform(it) else it }))

    /**
     * The live shortcut is released while recording: the OS would otherwise
     * swallow the very combination the user is trying to re-record.
     */
    fun beginRecording() {
        isRecordingHotkey = true
        hotkeys.release()
    }

    fun cancelRecording() {
        isRecordingHotkey = false
        hotkeyStatus = hotkeys.bind(config)
    }

    fun applyRecorded(hotkey: HotkeyConfig) {
        isRecordingHotkey = false
        val candidate = config.copy(hotkey = hotkey)
        val status = hotkeys.bind(candidate)
        if (status == HotkeyStatus.Conflict || status == HotkeyStatus.Failed) {
            // Saving a combination we could not claim would leave the user with
            // no working shortcut, so keep the old one and explain.
            hotkeys.bind(config)
            hotkeyStatus = status
        } else {
            update(candidate)
            hotkeyStatus = status
        }
    }

    fun reportHotkeyStatus(status: HotkeyStatus) {
        hotkeyStatus = status
    }

    fun loadModels() {
        modelLoad?.cancel()
        isLoadingModels = true
        modelsError = null
        modelLoad = scope.launch {
            try {
                val models = withContext(Dispatchers.IO) {
                    dependencies.rewordService.listModels(
                        config.provider, apiKey, config.endpointOverride
                    )
                }
                availableModels = models.sortedBy { it.id }
                if (models.isEmpty()) modelsError = Strings["provider.noModels"]
            } catch (error: RewordError) {
                modelsError = error.localized()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                modelsError = error.message ?: Strings["provider.noModels"]
            } finally {
                isLoadingModels = false
            }
        }
    }

    private fun update(updated: RewordConfig) {
        config = updated
        dependencies.configStore.save(updated)
    }
}

enum class HotkeyStatus { Active, Conflict, Failed, Unsupported }

/** Lets the settings screen rebind the global shortcut without owning it. */
interface HotkeyBinder {
    fun bind(config: RewordConfig): HotkeyStatus
    fun release()
}

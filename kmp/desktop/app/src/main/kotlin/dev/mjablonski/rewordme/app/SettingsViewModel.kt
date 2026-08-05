package dev.mjablonski.rewordme.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.mjablonski.rewordme.domain.ApiKeyStore
import dev.mjablonski.rewordme.domain.ConfigStore
import dev.mjablonski.rewordme.domain.ModelSelection
import dev.mjablonski.rewordme.domain.Rewording
import dev.mjablonski.rewordme.data.AccountProviderService
import dev.mjablonski.rewordme.data.AccountProviderStatus
import dev.mjablonski.rewordme.data.LocalModelManager
import dev.mjablonski.rewordme.data.LocalModelProgress
import dev.mjablonski.rewordme.data.LocalModelState
import dev.mjablonski.rewordme.models.HotkeyConfig
import dev.mjablonski.rewordme.models.ModelInfo
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.ProviderAccess
import dev.mjablonski.rewordme.models.LocalModelCatalog
import dev.mjablonski.rewordme.models.OfflineModelManifest
import dev.mjablonski.rewordme.models.RewordConfig
import dev.mjablonski.rewordme.models.RewordError
import dev.mjablonski.rewordme.models.RewriteRule
import dev.mjablonski.rewordme.platform.StartupRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI
import java.util.Locale

/**
 * Settings state. Every edit is written straight to disk - there is no OK or
 * Cancel button, matching the macOS app.
 */
class SettingsViewModel(
    private val configStore: ConfigStore,
    private val keyStore: ApiKeyStore,
    private val rewordService: Rewording,
    private val accountProviders: AccountProviderService = AccountProviderService(),
    private val localModel: LocalModelManager = LocalModelManager(),
    private val scope: CoroutineScope,
    private val hotkeys: HotkeyBinder
) {
    /**
     * Convenience for the composition root. Tests build the primary
     * constructor with fakes instead, so that nothing has to stand up
     * AppDependencies - which would open the real config file and the real
     * credential store.
     */
    constructor(
        dependencies: AppDependencies,
        scope: CoroutineScope,
        hotkeys: HotkeyBinder
    ) : this(
        dependencies.configStore,
        dependencies.keyStore,
        dependencies.rewordService,
        dependencies.accountProviderService,
        dependencies.localModelManager,
        scope,
        hotkeys
    )

    var config by mutableStateOf(configStore.load())
        private set
    var apiKey by mutableStateOf("")
        private set
    var keySaved by mutableStateOf(false)
        private set
    var keySaveError by mutableStateOf<String?>(null)
        private set

    var availableModels by mutableStateOf(emptyList<ModelInfo>())
        private set
    var isLoadingModels by mutableStateOf(false)
        private set
    var modelsError by mutableStateOf<String?>(null)
        private set

    var accountStatus by mutableStateOf<AccountProviderStatus?>(null)
        private set
    var isCheckingAccount by mutableStateOf(false)
        private set
    var isSigningIn by mutableStateOf(false)
        private set
    var accountError by mutableStateOf<String?>(null)
        private set

    var localModelState by mutableStateOf<LocalModelState>(LocalModelState.NotDownloaded)
        private set
    var localDownloadProgress by mutableStateOf(
        LocalModelProgress(0, LocalModelCatalog.DEFAULT.byteCount)
    )
        private set

    var hotkeyStatus by mutableStateOf(HotkeyStatus.Active)
        private set
    var isRecordingHotkey by mutableStateOf(false)
        private set

    private var modelLoad: Job? = null
    private var modelLoadId = 0L
    private var accountJob: Job? = null
    private var accountRequestId = 0L
    private var localDownloadJob: Job? = null
    private var localDownloadId = 0L
    private var localDownloadModelId: String? = null
    private var persistedApiKey = ""

    init {
        apiKey = keyStore.apiKey(config.provider) ?: ""
        persistedApiKey = apiKey
        refreshProviderSetup()
        if (config.provider.access in setOf(ProviderAccess.ACCOUNT, ProviderAccess.MANAGED_LOCAL)) {
            loadModels()
        }
    }

    val launchAtLoginSupported: Boolean get() = StartupRegistration.isSupported

    var launchAtLogin by mutableStateOf(StartupRegistration.isEnabled)
        private set

    /**
     * Reads the registry back, so the switch snaps to whatever actually stuck,
     * and records the answer so first-run registration never overrides it.
     */
    fun toggleLaunchAtLogin(enabled: Boolean) {
        launchAtLogin = if (StartupRegistration.isSupported) {
            StartupRegistration.isEnabled = enabled
            StartupRegistration.isEnabled
        } else {
            enabled
        }
        update(config.copy(launchAtLogin = launchAtLogin))
    }

    /** The model "Automatic" would pick right now, once the list is known. */
    val automaticModelHint: String?
        get() = ModelSelection.defaultModel(config.provider, availableModels)?.id
            ?.takeUnless { it == "automatic" }

    val canSaveApiKey: Boolean
        get() = apiKey.trim() != persistedApiKey

    fun selectProvider(provider: ProviderKind) {
        if (provider == config.provider) return
        update(config.selectingProvider(provider))
        apiKey = keyStore.apiKey(provider) ?: ""
        persistedApiKey = apiKey
        keySaved = false
        keySaveError = null
        clearModelResults()
        refreshProviderSetup()
        if (provider.access in setOf(ProviderAccess.ACCOUNT, ProviderAccess.MANAGED_LOCAL)) {
            loadModels()
        }
    }

    fun editApiKey(value: String) {
        if (value == apiKey) return
        apiKey = value
        keySaved = false
        keySaveError = null
        clearModelResults()
    }

    fun saveApiKey() {
        val normalized = apiKey.trim()
        val saved = keyStore.setApiKey(config.provider, normalized)
        if (saved) {
            apiKey = normalized
            persistedApiKey = normalized
        }
        keySaved = saved
        keySaveError = if (saved) null else Strings["provider.saveFailed"]
    }

    fun setOllamaHost(host: String) {
        if (host == config.ollamaHost) return
        // A selected model and loaded catalog belong to the old server.
        update(config.selectingModel(null).copy(ollamaHost = host))
        clearModelResults()
    }

    fun selectModel(model: String?) = update(config.selectingModel(model))

    val selectedLocalModel: OfflineModelManifest
        get() = LocalModelCatalog.model(config.selectedModel)

    val isLocalDownloadActive: Boolean
        get() = localDownloadModelId != null

    fun selectLocalModel(modelId: String?) {
        val manifest = LocalModelCatalog.ALL.firstOrNull { it.id == modelId } ?: return
        if (manifest.id == selectedLocalModel.id) return
        update(config.selectingModel(manifest.id))
        refreshLocalModelState()
    }

    fun setBasePrompt(prompt: String) = update(config.copy(basePrompt = prompt))

    fun addRule() =
        update(config.copy(rules = config.rules + RewriteRule(text = "")))

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
        if (!isRecordingHotkey) return
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
        val requestId = ++modelLoadId
        val provider = config.provider
        val key = apiKey
        val endpoint = config.endpointOverride
        isLoadingModels = true
        modelsError = null
        modelLoad = scope.launch {
            try {
                val models = withContext(Dispatchers.IO) {
                    rewordService.listModels(provider, key, endpoint)
                }
                if (requestId != modelLoadId) return@launch
                availableModels = models
                if (models.isEmpty()) modelsError = Strings["provider.noModels"]
            } catch (error: RewordError) {
                if (requestId == modelLoadId) modelsError = error.localized()
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (requestId == modelLoadId) {
                    modelsError = error.message ?: Strings["provider.noModels"]
                }
            } finally {
                if (requestId == modelLoadId) isLoadingModels = false
            }
        }
    }

    fun refreshProviderSetup() {
        accountJob?.cancel()
        accountJob = null
        val requestId = ++accountRequestId
        accountStatus = null
        accountError = null
        isCheckingAccount = false
        isSigningIn = false
        val provider = config.provider
        when (provider.access) {
            ProviderAccess.ACCOUNT -> {
                isCheckingAccount = true
                accountJob = scope.launch {
                    try {
                        val status = withContext(Dispatchers.IO) { accountProviders.status(provider) }
                        if (requestId != accountRequestId || config.provider != provider) return@launch
                        accountStatus = status
                    } catch (error: Exception) {
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        if (requestId == accountRequestId && config.provider == provider) {
                            accountError = userMessage(error)
                        }
                    } finally {
                        if (requestId == accountRequestId) {
                            isCheckingAccount = false
                            isSigningIn = false
                            accountJob = null
                        }
                    }
                }
            }
            ProviderAccess.MANAGED_LOCAL -> refreshLocalModelState()
            ProviderAccess.API_KEY, ProviderAccess.EXTERNAL_LOCAL -> Unit
        }
    }

    fun setUpAccountProvider() {
        val provider = config.provider
        if (!provider.isAccountProvider) return
        accountJob?.cancel()
        val requestId = ++accountRequestId
        isCheckingAccount = true
        isSigningIn = false
        accountError = null
        accountJob = scope.launch {
            try {
                val status = withContext(Dispatchers.IO) { accountProviders.status(provider) }
                if (requestId != accountRequestId || config.provider != provider) return@launch
                accountStatus = status
                isCheckingAccount = false
                if (!status.isInstalled) {
                    runCatching { Desktop.getDesktop().browse(URI(provider.apiKeyConsoleUrl)) }
                        .onFailure {
                            if (requestId == accountRequestId) accountError = userMessage(it)
                        }
                    return@launch
                }
                isSigningIn = true
                withContext(Dispatchers.IO) { accountProviders.signIn(provider) }
                if (requestId != accountRequestId || config.provider != provider) return@launch
                val refreshed = withContext(Dispatchers.IO) { accountProviders.status(provider) }
                if (requestId != accountRequestId || config.provider != provider) return@launch
                accountStatus = refreshed
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (requestId == accountRequestId && config.provider == provider) {
                    accountError = userMessage(error)
                }
            } finally {
                if (requestId == accountRequestId) {
                    isCheckingAccount = false
                    isSigningIn = false
                    accountJob = null
                }
            }
        }
    }

    fun downloadLocalModel() {
        if (localDownloadJob != null) return
        val manifest = selectedLocalModel
        val requestId = ++localDownloadId
        localDownloadModelId = manifest.id
        val initial = LocalModelProgress(0, manifest.byteCount)
        localDownloadProgress = initial
        localModelState = LocalModelState.Downloading(initial)
        localDownloadJob = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    localModel.download(manifest.id) { progress ->
                        scope.launch {
                            if (requestId == localDownloadId) {
                                localDownloadProgress = progress
                                localModelState = LocalModelState.Downloading(progress)
                            }
                        }
                    }
                }
                val state = withContext(Dispatchers.IO) { localModel.state(manifest.id) }
                if (requestId == localDownloadId) localModelState = state
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) {
                    if (requestId == localDownloadId) {
                        localModelState = LocalModelState.NotDownloaded
                    }
                    throw error
                }
                if (requestId == localDownloadId) {
                    localModelState = LocalModelState.Failed(
                        userMessage(error)
                    )
                }
            } finally {
                if (localDownloadModelId == manifest.id) localDownloadModelId = null
                localDownloadJob = null
            }
        }
    }

    fun cancelLocalModelDownload() {
        val job = localDownloadJob ?: return
        ++localDownloadId
        localDownloadModelId = null
        localModelState = LocalModelState.NotDownloaded
        // Mark the coroutine cancelled before closing its blocking stream so
        // the resulting IOException is correctly treated as cancellation.
        job.cancel()
        localModel.cancelDownload()
    }

    fun removeLocalModel() {
        val manifest = selectedLocalModel
        cancelLocalModelDownload()
        scope.launch {
            try {
                withContext(Dispatchers.IO) { localModel.removeModel(manifest.id) }
                if (selectedLocalModel.id == manifest.id) {
                    localModelState = LocalModelState.NotDownloaded
                }
            } catch (error: Exception) {
                if (selectedLocalModel.id == manifest.id) {
                    localModelState = LocalModelState.Failed(userMessage(error))
                }
            }
        }
    }

    val localProgressText: String
        get() = "${formatBytes(localDownloadProgress.receivedBytes)} / ${formatBytes(localDownloadProgress.totalBytes)}"

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format(Locale.getDefault(), "%.2f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }

    private fun refreshLocalModelState() {
        val provider = config.provider
        val modelId = selectedLocalModel.id
        scope.launch {
            val state = withContext(Dispatchers.IO) { localModel.state(modelId) }
            if (config.provider == provider && selectedLocalModel.id == modelId) {
                localModelState = state
            }
        }
    }

    private fun clearModelResults() {
        modelLoad?.cancel()
        modelLoad = null
        modelLoadId++
        isLoadingModels = false
        availableModels = emptyList()
        modelsError = null
    }

    private fun userMessage(error: Throwable): String = when (error) {
        is RewordError -> error.localized()
        else -> error.message ?: error.javaClass.simpleName
    }

    private fun update(updated: RewordConfig) {
        config = updated
        configStore.save(updated)
    }
}

enum class HotkeyStatus { Active, Conflict, Failed, Unsupported }

/** Lets the settings screen rebind the global shortcut without owning it. */
interface HotkeyBinder {
    fun bind(config: RewordConfig): HotkeyStatus
    fun release()
}

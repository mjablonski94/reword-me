import AppKit
import Foundation
import RewordMeData
import RewordMeDomain
import RewordMeModels
import RewordMePlatform
import ServiceManagement
import SwiftUI

extension Notification.Name {
    /// Posted after every config save so the app delegate can re-apply
    /// behavior that lives outside the Settings window (the hotkey).
    static let rewordConfigChanged = Notification.Name("RewordMeConfigChanged")
}

/// View model for the Settings window. Config changes save to disk
/// immediately; API keys go straight to the injected key store.
@MainActor
final class SettingsViewModel: ObservableObject {
    @Published var config: RewordConfig {
        didSet {
            try? configStore.save(config)
            NotificationCenter.default.post(name: .rewordConfigChanged, object: nil)
            if oldValue.provider != config.provider {
                apiKey = keyStore.apiKey(for: config.provider) ?? ""
                persistedAPIKey = apiKey
                keySavedFeedback = false
                keySaveError = nil
                clearModelResults()
                refreshProviderSetup()
                if config.provider.access == .account || config.provider.access == .managedLocal {
                    loadModels()
                }
            }
            if oldValue.ollamaHost != config.ollamaHost {
                invalidateResolvedModel()
            }
            if oldValue.provider == .local,
               config.provider == .local,
               oldValue.model != config.model {
                refreshLocalModelState()
                invalidateResolvedModel()
            }
        }
    }

    @Published var apiKey: String = ""
    @Published var availableModels: [ModelInfo] = []
    @Published var isLoadingModels = false
    @Published var modelsError: String?
    @Published var keySavedFeedback = false
    @Published var keySaveError: String?
    @Published var accountStatus: AccountProviderStatus?
    @Published var isCheckingAccount = false
    @Published var isSigningIn = false
    @Published var accountError: String?
    @Published var localModelState: LocalModelState = .notDownloaded
    @Published var localDownloadProgress = LocalModelProgress(
        receivedBytes: 0,
        totalBytes: LocalModelManifest.byteCount
    )
    @Published var accessibilityTrusted: Bool
    @Published var launchAtLogin: Bool {
        didSet { updateLaunchAtLogin() }
    }

    private let configStore: ConfigStore
    private let keyStore: any APIKeyStore
    private let service: RewordService
    private let modelResolver: ModelResolver
    private let accountProviders: AccountProviderService
    private let localModel: LocalModelManager
    private let accessibility: any AccessibilityChecking
    private let defaults: UserDefaults
    private var feedbackTask: Task<Void, Never>?
    private var modelLoadTask: Task<Void, Never>?
    private var modelLoadID: UUID?
    private var accountTask: Task<Void, Never>?
    private var accountRequestID: UUID?
    private var localDownloadTask: Task<Void, Never>?
    private var localDownloadID: UUID?
    private var localDownloadModelID: String?
    private var persistedAPIKey = ""

    init(dependencies: AppDependencies, defaults: UserDefaults = .standard) {
        self.configStore = dependencies.configStore
        self.keyStore = dependencies.keyStore
        self.service = dependencies.rewordService
        self.modelResolver = dependencies.modelResolver
        self.accountProviders = dependencies.accountProviderService
        self.localModel = dependencies.localModelManager
        self.accessibility = dependencies.accessibility
        self.defaults = defaults

        let loaded = dependencies.configStore.load()
        config = loaded
        launchAtLogin = SMAppService.mainApp.status == .enabled
        accessibilityTrusted = dependencies.accessibility.isTrusted
        apiKey = keyStore.apiKey(for: loaded.provider) ?? ""
        persistedAPIKey = apiKey

        Task { [weak self] in
            self?.refreshProviderSetup()
            if loaded.provider.access == .account || loaded.provider.access == .managedLocal {
                self?.loadModels()
            }
        }
    }

    func saveAPIKey() {
        explainKeychainPromptOnce()
        let normalized = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let saved = keyStore.setAPIKey(normalized, for: config.provider)
        if saved {
            apiKey = normalized
            persistedAPIKey = normalized
        }
        keySavedFeedback = saved
        keySaveError = saved ? nil : Loc.saveKeyFailed
        guard saved else {
            feedbackTask?.cancel()
            return
        }

        invalidateResolvedModel()
        feedbackTask?.cancel()
        feedbackTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(2.5))
            guard !Task.isCancelled else { return }
            self?.keySavedFeedback = false
        }
    }

    func selectProvider(_ provider: ProviderKind) {
        guard provider != config.provider else { return }
        var updated = config
        updated.provider = provider
        config = updated
    }

    var selectedLocalModel: OfflineModelManifest {
        LocalModelCatalog.model(id: config.model)
    }

    var isLocalDownloadActive: Bool { localDownloadModelID != nil }

    func selectLocalModel(_ modelID: String) {
        guard LocalModelCatalog.all.contains(where: { $0.id == modelID }) else { return }
        var updated = config
        updated.model = modelID
        config = updated
    }

    func editAPIKey(_ value: String) {
        guard value != apiKey else { return }
        apiKey = value
        keySavedFeedback = false
        keySaveError = nil
        clearModelResults()
    }

    var canSaveAPIKey: Bool {
        apiKey.trimmingCharacters(in: .whitespacesAndNewlines) != persistedAPIKey
    }

    func setOllamaHost(_ host: String) {
        guard host != config.ollamaHost else { return }
        var updated = config
        updated.ollamaHost = host
        // A pinned model belongs to the old local server's catalog.
        updated.model = nil
        config = updated
    }

    func loadModels() {
        modelLoadTask?.cancel()
        let requestID = UUID()
        modelLoadID = requestID
        isLoadingModels = true
        modelsError = nil
        let provider = config.provider
        let key = apiKey
        let endpoint = config.endpointOverride
        modelLoadTask = Task { [weak self] in
            guard let self else { return }
            defer {
                if self.modelLoadID == requestID {
                    self.isLoadingModels = false
                    self.modelLoadTask = nil
                }
            }
            do {
                let models = try await service.listModels(
                    provider: provider, apiKey: key, endpoint: endpoint
                )
                guard !Task.isCancelled,
                      self.modelLoadID == requestID,
                      self.config.provider == provider,
                      self.apiKey == key,
                      self.config.endpointOverride == endpoint else { return }
                // Preserve server order: Ollama's first item is its automatic
                // choice. The Picker sorts only its visual presentation.
                self.availableModels = models
                if models.isEmpty {
                    self.modelsError = Loc.noModels
                }
            } catch is CancellationError {
                return
            } catch {
                guard self.modelLoadID == requestID else { return }
                self.modelsError = (error as? RewordError).map(Loc.message(for:))
                    ?? error.localizedDescription
            }
        }
    }

    // MARK: - Account and managed-local setup

    func refreshProviderSetup() {
        accountTask?.cancel()
        accountTask = nil
        accountRequestID = nil
        accountStatus = nil
        accountError = nil
        isCheckingAccount = false
        isSigningIn = false

        let provider = config.provider
        switch provider.access {
        case .account:
            let requestID = UUID()
            accountRequestID = requestID
            isCheckingAccount = true
            accountTask = Task { [weak self] in
                guard let self else { return }
                defer {
                    if self.accountRequestID == requestID {
                        self.isCheckingAccount = false
                        self.isSigningIn = false
                        self.accountTask = nil
                        self.accountRequestID = nil
                    }
                }
                let status = await accountProviders.status(for: provider)
                guard !Task.isCancelled,
                      self.accountRequestID == requestID,
                      self.config.provider == provider else { return }
                self.accountStatus = status
            }
        case .managedLocal:
            refreshLocalModelState()
        case .apiKey, .externalLocal:
            break
        }
    }

    /// Installed CLIs open their official browser sign-in. Missing CLIs open
    /// the provider's official setup page instead of failing silently.
    func setUpAccountProvider() {
        guard config.provider.isAccountProvider else { return }
        accountTask?.cancel()
        let provider = config.provider
        let requestID = UUID()
        accountRequestID = requestID
        isCheckingAccount = true
        isSigningIn = false
        accountError = nil
        accountTask = Task { [weak self] in
            guard let self else { return }
            defer {
                if self.accountRequestID == requestID {
                    self.isCheckingAccount = false
                    self.isSigningIn = false
                    self.accountTask = nil
                    self.accountRequestID = nil
                }
            }
            let status = await accountProviders.status(for: provider)
            guard !Task.isCancelled,
                  self.accountRequestID == requestID,
                  self.config.provider == provider else { return }
            self.accountStatus = status
            self.isCheckingAccount = false
            guard status.isInstalled else {
                NSWorkspace.shared.open(provider.apiKeyConsoleURL)
                return
            }
            self.isSigningIn = true
            do {
                try await accountProviders.signIn(to: provider)
                guard !Task.isCancelled,
                      self.accountRequestID == requestID,
                      self.config.provider == provider else { return }
                let refreshed = await accountProviders.status(for: provider)
                guard !Task.isCancelled,
                      self.accountRequestID == requestID,
                      self.config.provider == provider else { return }
                self.accountStatus = refreshed
            } catch is CancellationError {
                return
            } catch {
                guard self.accountRequestID == requestID,
                      self.config.provider == provider else { return }
                self.accountError = self.localizedMessage(for: error)
            }
        }
    }

    func downloadLocalModel() {
        guard localDownloadTask == nil else { return }
        let manifest = selectedLocalModel
        let downloadID = UUID()
        localDownloadID = downloadID
        localDownloadModelID = manifest.id
        localDownloadProgress = LocalModelProgress(
            receivedBytes: 0,
            totalBytes: manifest.byteCount
        )
        localModelState = .downloading(localDownloadProgress)
        localDownloadTask = Task { [weak self] in
            guard let self else { return }
            do {
                try await localModel.download(modelID: manifest.id) { [weak self] progress in
                    Task { @MainActor in
                        guard let self else { return }
                        guard self.localDownloadID == downloadID else { return }
                        self.localDownloadProgress = progress
                        self.localModelState = .downloading(progress)
                    }
                }
                let state = await localModel.state(modelID: manifest.id)
                if self.localDownloadID == downloadID {
                    self.localModelState = state
                }
            } catch is CancellationError {
                if self.localDownloadID == downloadID {
                    self.localModelState = .notDownloaded
                }
            } catch {
                if self.localDownloadID == downloadID {
                    self.localModelState = .failed(self.localizedMessage(for: error))
                }
            }
            if self.localDownloadID == downloadID { self.localDownloadID = nil }
            if self.localDownloadModelID == manifest.id { self.localDownloadModelID = nil }
            self.localDownloadTask = nil
        }
    }

    func cancelLocalModelDownload() {
        guard localDownloadTask != nil else { return }
        // Invalidate already queued progress callbacks before signalling the
        // downloader so Cancel cannot be followed by stale progress UI.
        localDownloadID = nil
        localDownloadModelID = nil
        localModelState = .notDownloaded
        localDownloadTask?.cancel()
        Task { [localModel] in await localModel.cancelDownload() }
    }

    func removeLocalModel() {
        let manifest = selectedLocalModel
        localDownloadTask?.cancel()
        localDownloadTask = nil
        localDownloadID = nil
        Task { [weak self] in
            guard let self else { return }
            do {
                try await localModel.removeModel(modelID: manifest.id)
                if selectedLocalModel.id == manifest.id { localModelState = .notDownloaded }
            } catch {
                if selectedLocalModel.id == manifest.id {
                    localModelState = .failed(localizedMessage(for: error))
                }
            }
        }
    }

    private func refreshLocalModelState() {
        let provider = config.provider
        let modelID = selectedLocalModel.id
        Task { [weak self] in
            guard let self else { return }
            let state = await localModel.state(modelID: modelID)
            guard self.config.provider == provider,
                  self.selectedLocalModel.id == modelID else { return }
            self.localModelState = state
        }
    }

    var localProgressText: String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        let received = formatter.string(fromByteCount: localDownloadProgress.receivedBytes)
        let total = formatter.string(fromByteCount: localDownloadProgress.totalBytes)
        return "\(received) / \(total)"
    }

    func formattedBytes(_ bytes: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }

    var automaticModelHint: String {
        guard !availableModels.isEmpty else { return "" }
        guard let pick = ModelSelection.defaultModel(for: config.provider, from: availableModels) else {
            return ""
        }
        // Account CLIs expose an internal "automatic" sentinel. The picker
        // already has a localized Automatic row, so repeating the sentinel as
        // a resolved model would be both redundant and misleading.
        guard pick.id != "automatic" else { return "" }
        return Loc.automaticHint(pick.id)
    }

    // MARK: - Rules

    func addRule() {
        config.rules.append(RewriteRule(text: ""))
    }

    func rule(id: UUID) -> RewriteRule? {
        config.rules.first { $0.id == id }
    }

    func updateRule(id: UUID, _ update: (inout RewriteRule) -> Void) {
        guard let index = config.rules.firstIndex(where: { $0.id == id }) else { return }
        var rule = config.rules[index]
        update(&rule)
        config.rules[index] = rule
    }

    func removeRule(id: UUID) {
        config.rules.removeAll { $0.id == id }
    }

    // MARK: - Accessibility

    func refreshAccessibilityStatus() {
        accessibilityTrusted = accessibility.isTrusted
    }

    func openAccessibilitySettings() {
        accessibility.openSystemSettings()
    }

    // MARK: - Private

    private func invalidateResolvedModel() {
        Task { [modelResolver] in await modelResolver.invalidate() }
        clearModelResults()
    }

    private func clearModelResults() {
        modelLoadTask?.cancel()
        modelLoadTask = nil
        modelLoadID = nil
        isLoadingModels = false
        availableModels = []
        modelsError = nil
    }

    private func localizedMessage(for error: Error) -> String {
        (error as? RewordError).map(Loc.message(for:)) ?? error.localizedDescription
    }

    /// Shown once, before the first key ever lands in the Keychain, so the
    /// system's later "RewordMe wants to access your keychain" password
    /// prompt is expected instead of alarming.
    private func explainKeychainPromptOnce() {
        let flag = "keychainPromptExplained"
        guard !defaults.bool(forKey: flag) else { return }
        defaults.set(true, forKey: flag)

        let alert = NSAlert()
        alert.messageText = Loc.keychainAlertTitle
        alert.informativeText = Loc.keychainAlertBody
        alert.alertStyle = .informational
        alert.addButton(withTitle: Loc.gotIt)
        alert.runModal()
    }

    private func updateLaunchAtLogin() {
        do {
            if launchAtLogin {
                try SMAppService.mainApp.register()
            } else {
                try SMAppService.mainApp.unregister()
            }
        } catch {
            launchAtLogin = SMAppService.mainApp.status == .enabled
        }
        // Recorded so first-run registration never overrides an explicit "off".
        config.launchAtLogin = launchAtLogin
    }
}

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
                keySavedFeedback = false
                keySaveError = nil
                clearModelResults()
            }
            if oldValue.ollamaHost != config.ollamaHost {
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
    @Published var accessibilityTrusted: Bool
    @Published var launchAtLogin: Bool {
        didSet { updateLaunchAtLogin() }
    }

    private let configStore: ConfigStore
    private let keyStore: any APIKeyStore
    private let service: RewordService
    private let modelResolver: ModelResolver
    private let accessibility: any AccessibilityChecking
    private let defaults: UserDefaults
    private var feedbackTask: Task<Void, Never>?
    private var modelLoadTask: Task<Void, Never>?
    private var modelLoadID: UUID?

    init(dependencies: AppDependencies, defaults: UserDefaults = .standard) {
        self.configStore = dependencies.configStore
        self.keyStore = dependencies.keyStore
        self.service = dependencies.rewordService
        self.modelResolver = dependencies.modelResolver
        self.accessibility = dependencies.accessibility
        self.defaults = defaults

        let loaded = dependencies.configStore.load()
        config = loaded
        launchAtLogin = SMAppService.mainApp.status == .enabled
        accessibilityTrusted = dependencies.accessibility.isTrusted
        apiKey = keyStore.apiKey(for: loaded.provider) ?? ""
    }

    func saveAPIKey() {
        explainKeychainPromptOnce()
        let normalized = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let saved = keyStore.setAPIKey(normalized, for: config.provider)
        if saved { apiKey = normalized }
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
        // A model id from one provider is meaningless to another.
        updated.model = nil
        config = updated
    }

    func editAPIKey(_ value: String) {
        guard value != apiKey else { return }
        apiKey = value
        keySavedFeedback = false
        keySaveError = nil
        clearModelResults()
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

    var automaticModelHint: String {
        guard !availableModels.isEmpty else { return "" }
        guard let pick = ModelSelection.defaultModel(for: config.provider, from: availableModels) else {
            return ""
        }
        return Loc.automaticHint(pick.id)
    }

    // MARK: - Rules

    func addRule() {
        config.rules.append(RewriteRule(kind: .dontRule, text: ""))
    }

    func removeRule(_ rule: RewriteRule) {
        config.rules.removeAll { $0.id == rule.id }
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

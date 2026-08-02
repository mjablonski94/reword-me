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
                availableModels = []
                modelsError = nil
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
        keyStore.setAPIKey(apiKey, for: config.provider)
        invalidateResolvedModel()

        keySavedFeedback = true
        feedbackTask?.cancel()
        feedbackTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(2.5))
            guard !Task.isCancelled else { return }
            self?.keySavedFeedback = false
        }
    }

    func loadModels() {
        isLoadingModels = true
        modelsError = nil
        let provider = config.provider
        let key = apiKey
        let endpoint = config.endpointOverride
        Task { [weak self] in
            guard let self else { return }
            do {
                let models = try await service.listModels(
                    provider: provider, apiKey: key, endpoint: endpoint
                )
                self.availableModels = models.sorted { $0.id < $1.id }
                if models.isEmpty {
                    self.modelsError = Loc.noModels
                }
            } catch {
                self.modelsError = (error as? RewordError).map(Loc.message(for:))
                    ?? error.localizedDescription
            }
            self.isLoadingModels = false
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

import Foundation
import RewordMeCore
import ServiceManagement
import SwiftUI

extension Notification.Name {
    /// Posted after every config save so the app delegate can re-apply
    /// behavior that lives outside the Settings window (trigger mode).
    static let rewordConfigChanged = Notification.Name("RewordMeConfigChanged")
}

/// Backing model for the Settings window. Config changes save to disk
/// immediately; API keys go straight to the Keychain.
@MainActor
final class SettingsModel: ObservableObject {
    @Published var config: RewordConfig {
        didSet {
            try? configStore.save(config)
            NotificationCenter.default.post(name: .rewordConfigChanged, object: nil)
            if oldValue.provider != config.provider {
                apiKey = KeychainStore.apiKey(for: config.provider) ?? ""
                availableModels = []
                modelsError = nil
            }
            if oldValue.ollamaHost != config.ollamaHost {
                Task { await ModelResolver.shared.invalidate() }
                availableModels = []
                modelsError = nil
            }
        }
    }

    @Published var apiKey: String = ""
    @Published var availableModels: [ModelInfo] = []
    @Published var isLoadingModels = false
    @Published var modelsError: String?
    @Published var keySavedFeedback = false
    @Published var launchAtLogin: Bool {
        didSet { updateLaunchAtLogin() }
    }

    private let configStore = ConfigStore()
    private let service = RewordService()
    private var feedbackTask: Task<Void, Never>?

    init() {
        let loaded = ConfigStore().load()
        config = loaded
        launchAtLogin = SMAppService.mainApp.status == .enabled
        apiKey = KeychainStore.apiKey(for: loaded.provider) ?? ""
    }

    func saveAPIKey() {
        explainKeychainPromptOnce()
        KeychainStore.setAPIKey(apiKey, for: config.provider)
        Task { await ModelResolver.shared.invalidate() }
        availableModels = []
        modelsError = nil

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

    /// Shown once, before the first key ever lands in the Keychain, so the
    /// system's later "RewordMe wants to access your keychain" password
    /// prompt is expected instead of alarming.
    private func explainKeychainPromptOnce() {
        let defaults = UserDefaults.standard
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
    }
}

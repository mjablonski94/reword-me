import Foundation
import RewordMeCore
import ServiceManagement
import SwiftUI

/// Backing model for the Settings window. Config changes save to disk
/// immediately; API keys go straight to the Keychain.
@MainActor
final class SettingsModel: ObservableObject {
    @Published var config: RewordConfig {
        didSet {
            try? configStore.save(config)
            if oldValue.provider != config.provider {
                apiKey = KeychainStore.apiKey(for: config.provider) ?? ""
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
        Task { [weak self] in
            guard let self else { return }
            do {
                let models = try await service.listModels(provider: provider, apiKey: key)
                self.availableModels = models.sorted { $0.id < $1.id }
                if models.isEmpty {
                    self.modelsError = "The provider returned no usable models."
                }
            } catch {
                self.modelsError = (error as? RewordError)?.errorDescription
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
        return "Automatic currently resolves to \(pick.id)"
    }

    // MARK: - Rules

    func addRule() {
        config.rules.append(RewriteRule(kind: .dontRule, text: ""))
    }

    func removeRule(_ rule: RewriteRule) {
        config.rules.removeAll { $0.id == rule.id }
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

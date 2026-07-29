import AppKit
import Foundation
import RewordMeCore
import SwiftUI

/// State behind one popup: the original selection, the generated rewrite,
/// the steering line, and the actions on them.
@MainActor
final class RewordSession: ObservableObject {
    @Published var original: String
    @Published var result: String = ""
    @Published var steering: String = ""
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var modelLabel: String = ""

    var onClose: (() -> Void)?

    private let service = RewordService()
    private let configStore = ConfigStore()
    private var generationTask: Task<Void, Never>?

    init(original: String) {
        self.original = original
    }

    func generate() {
        generationTask?.cancel()
        isLoading = true
        errorMessage = nil
        let config = configStore.load()
        let steering = steering

        generationTask = Task { [weak self] in
            guard let self else { return }
            do {
                let apiKey: String
                if config.provider.requiresAPIKey {
                    guard let stored = KeychainStore.apiKey(for: config.provider) else {
                        throw RewordError.missingAPIKey
                    }
                    apiKey = stored
                } else {
                    apiKey = ""
                }
                let model = try await ModelResolver.shared.model(
                    for: config, apiKey: apiKey, service: service
                )
                let systemPrompt = PromptBuilder.systemPrompt(
                    rules: config.rules,
                    basePrompt: config.basePrompt,
                    steering: steering
                )
                let reworded = try await service.reword(
                    provider: config.provider,
                    apiKey: apiKey,
                    model: model,
                    systemPrompt: systemPrompt,
                    text: original,
                    endpoint: config.endpointOverride
                )
                guard !Task.isCancelled else { return }
                self.result = reworded
                self.modelLabel = "\(config.provider.displayName) - \(model)"
                self.isLoading = false
            } catch {
                guard !Task.isCancelled else { return }
                self.errorMessage = (error as? RewordError)?.errorDescription
                    ?? error.localizedDescription
                self.isLoading = false
            }
        }
    }

    func copyResult() {
        guard !result.isEmpty else { return }
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(result, forType: .string)
        onClose?()
    }

    func replaceSelection() {
        guard !result.isEmpty else { return }
        let text = result
        onClose?()
        // Give the panel a moment to close so focus settles back
        // on the host app before we touch its selection.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
            TextReplacer.replaceSelection(with: text)
        }
    }

    func cancel() {
        generationTask?.cancel()
    }
}

import AppKit
import Foundation
import RewordMeCore
import SwiftUI

/// View model behind one popup. Menu-first, like Apple's Writing Tools:
/// nothing is generated until the user picks an action, so the popup can
/// appear on every invocation without costing a single token.
@MainActor
final class RewordViewModel: ObservableObject {
    enum Stage: Equatable {
        case menu
        case loading
        case result
        case failed(String)
    }

    struct Preset: Identifiable {
        var id: String { title }
        let title: String
        let icon: String
        let instruction: String
    }

    // Preset titles are localized; the instructions sent to the model stay
    // English - the core prompt preserves the text's own language anyway.
    static let proofread = Preset(
        title: Loc.proofread,
        icon: "text.magnifyingglass",
        instruction: "Only fix grammar, spelling and punctuation. Keep the wording and tone unchanged otherwise."
    )

    static let rewrite = Preset(
        title: Loc.rewrite,
        icon: "arrow.trianglehead.2.clockwise.rotate.90",
        instruction: ""
    )

    static let tonePresets: [Preset] = [
        Preset(title: Loc.friendly, icon: "face.smiling",
               instruction: "Make it warmer and more friendly."),
        Preset(title: Loc.professional, icon: "briefcase",
               instruction: "Make it more professional and polished."),
        Preset(title: Loc.concise, icon: "text.badge.minus",
               instruction: "Make it more concise without losing meaning.")
    ]

    @Published var original: String
    /// For the empty-selection hint; resolved once so the view never
    /// touches the config store.
    let hotkeyDisplay: String
    @Published var stage: Stage = .menu
    @Published var result: String = ""
    @Published var steering: String = ""
    @Published var modelLabel: String = ""

    var onClose: (() -> Void)?
    var onReplace: ((String) -> Void)?

    private let configStore: ConfigStore
    private let keyStore: any APIKeyStore
    private let service: RewordService
    private let modelResolver: ModelResolver
    private var generationTask: Task<Void, Never>?
    private var lastInstruction: String?

    init(original: String, dependencies: AppDependencies) {
        self.original = original
        self.configStore = dependencies.configStore
        self.keyStore = dependencies.keyStore
        self.service = dependencies.rewordService
        self.modelResolver = dependencies.modelResolver
        self.hotkeyDisplay = dependencies.configStore.load().hotkey.display
    }

    /// Runs a rewrite. An explicit preset instruction wins; otherwise
    /// whatever the user typed into the describe field is the steering.
    func reword(instruction explicit: String? = nil) {
        let typed = steering.trimmingCharacters(in: .whitespacesAndNewlines)
        let instruction = explicit ?? (typed.isEmpty ? nil : typed)
        lastInstruction = instruction
        run(instruction: instruction)
    }

    func regenerate() {
        run(instruction: lastInstruction)
    }

    func backToMenu() {
        generationTask?.cancel()
        stage = .menu
    }

    private func run(instruction: String?) {
        generationTask?.cancel()
        stage = .loading
        let config = configStore.load()

        generationTask = Task { [weak self] in
            guard let self else { return }
            do {
                let apiKey: String
                if config.provider.requiresAPIKey {
                    guard let stored = keyStore.apiKey(for: config.provider) else {
                        throw RewordError.missingAPIKey
                    }
                    apiKey = stored
                } else {
                    apiKey = ""
                }
                let model = try await modelResolver.model(
                    for: config, apiKey: apiKey, service: service
                )
                let systemPrompt = PromptBuilder.systemPrompt(
                    rules: config.rules,
                    basePrompt: config.basePrompt,
                    steering: instruction?.isEmpty == true ? nil : instruction
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
                self.stage = .result
            } catch {
                guard !Task.isCancelled else { return }
                let message = (error as? RewordError).map(Loc.message(for:))
                    ?? error.localizedDescription
                self.stage = .failed(message)
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
        onReplace?(result)
    }

    func cancel() {
        generationTask?.cancel()
    }
}

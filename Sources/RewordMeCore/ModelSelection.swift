import Foundation

/// Picks the default model for a provider: the least costly tier,
/// preferring stable releases, newest first.
public enum ModelSelection {
    public static func defaultModel(for kind: ProviderKind, from models: [ModelInfo]) -> ModelInfo? {
        guard !models.isEmpty else { return nil }
        let lowestTier = models.map { costTier(kind: kind, id: $0.id) }.min()!
        let candidates = models.filter { costTier(kind: kind, id: $0.id) == lowestTier }
        let stable = candidates.filter { !isPreview($0.id) }
        let pool = stable.isEmpty ? candidates : stable
        return pool.max { $0.id < $1.id }
    }

    /// Lower tier = cheaper. Unknown names land mid-tier so a cheap
    /// family keyword always wins when present.
    static func costTier(kind: ProviderKind, id: String) -> Int {
        let lower = id.lowercased()
        switch kind {
        case .anthropic:
            if lower.contains("haiku") { return 0 }
            if lower.contains("sonnet") { return 1 }
            if lower.contains("opus") { return 2 }
            if lower.contains("fable") || lower.contains("mythos") { return 3 }
            return 2
        case .openai:
            if lower.contains("nano") { return 0 }
            if lower.contains("mini") { return 1 }
            return 2
        case .gemini:
            if lower.contains("flash") && lower.contains("lite") { return 0 }
            if lower.contains("flash") { return 1 }
            if lower.contains("pro") { return 2 }
            return 3
        case .mistral:
            if lower.contains("ministral") || lower.contains("tiny") { return 0 }
            if lower.contains("nemo") || lower.contains("small") { return 1 }
            if lower.contains("medium") { return 2 }
            if lower.contains("large") { return 3 }
            return 2
        case .xai:
            if lower.contains("mini") { return 0 }
            if lower.contains("fast") { return 1 }
            return 2
        case .deepseek:
            if lower.contains("chat") { return 0 }
            return 1
        }
    }

    static func isPreview(_ id: String) -> Bool {
        let lower = id.lowercased()
        return lower.contains("preview") || lower.contains("-exp") || lower.contains("latest")
    }
}

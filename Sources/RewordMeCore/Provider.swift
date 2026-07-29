import Foundation

/// The LLM providers RewordMe can talk to. One API key per provider.
public enum ProviderKind: String, Codable, CaseIterable, Sendable, Identifiable {
    case anthropic
    case openai
    case gemini

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .anthropic: return "Claude (Anthropic)"
        case .openai: return "ChatGPT (OpenAI)"
        case .gemini: return "Gemini (Google)"
        }
    }

    public var keyPlaceholder: String {
        switch self {
        case .anthropic: return "sk-ant-..."
        case .openai: return "sk-..."
        case .gemini: return "AIza..."
        }
    }
}

/// A model as reported by the provider's model-listing endpoint.
public struct ModelInfo: Equatable, Sendable, Identifiable, Hashable {
    public let id: String
    public let displayName: String

    public init(id: String, displayName: String? = nil) {
        self.id = id
        self.displayName = displayName ?? id
    }
}

public enum RewordError: Error, Equatable, LocalizedError {
    case missingAPIKey
    case invalidAPIKey
    case rateLimited(retryAfterSeconds: Int?)
    case refused(String?)
    case apiError(status: Int, message: String)
    case emptyResponse
    case invalidResponse
    case noModelAvailable

    public var errorDescription: String? {
        switch self {
        case .missingAPIKey:
            return "No API key configured. Add one in Settings."
        case .invalidAPIKey:
            return "The API key was rejected by the provider. Check it in Settings."
        case let .rateLimited(retryAfter):
            if let retryAfter {
                return "Rate limit reached. Try again in \(retryAfter)s."
            }
            return "Rate limit reached. Try again in a moment."
        case let .refused(explanation):
            return explanation ?? "The provider declined to rewrite this text."
        case let .apiError(status, message):
            return "Provider error (\(status)): \(message)"
        case .emptyResponse:
            return "The provider returned an empty response."
        case .invalidResponse:
            return "Could not read the provider's response."
        case .noModelAvailable:
            return "No usable model found for this provider."
        }
    }
}

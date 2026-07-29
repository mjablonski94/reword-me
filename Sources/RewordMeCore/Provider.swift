import Foundation

/// The LLM providers RewordMe can talk to. One API key per provider.
public enum ProviderKind: String, Codable, CaseIterable, Sendable, Identifiable {
    case anthropic
    case openai
    case gemini
    case mistral
    case xai
    case deepseek
    case ollama

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .anthropic: return "Claude (Anthropic)"
        case .openai: return "ChatGPT (OpenAI)"
        case .gemini: return "Gemini (Google)"
        case .mistral: return "Mistral"
        case .xai: return "Grok (xAI)"
        case .deepseek: return "DeepSeek"
        case .ollama: return "Ollama (local)"
        }
    }

    /// Ollama runs on the user's machine and needs no key at all.
    public var requiresAPIKey: Bool {
        self != .ollama
    }

    public var keyPlaceholder: String {
        switch self {
        case .anthropic: return "sk-ant-..."
        case .openai: return "sk-..."
        case .gemini: return "AIza..."
        case .mistral: return "API key from console.mistral.ai"
        case .xai: return "xai-..."
        case .deepseek: return "sk-..."
        case .ollama: return ""
        }
    }

    /// Where the user creates an API key for this provider.
    public var apiKeyConsoleURL: URL {
        switch self {
        case .anthropic: return URL(string: "https://platform.claude.com/settings/keys")!
        case .openai: return URL(string: "https://platform.openai.com/api-keys")!
        case .gemini: return URL(string: "https://aistudio.google.com/apikey")!
        case .mistral: return URL(string: "https://console.mistral.ai/api-keys")!
        case .xai: return URL(string: "https://console.x.ai")!
        case .deepseek: return URL(string: "https://platform.deepseek.com/api_keys")!
        case .ollama: return URL(string: "https://ollama.com/download")!
        }
    }

    public var apiKeyConsoleName: String {
        switch self {
        case .anthropic: return "platform.claude.com"
        case .openai: return "platform.openai.com"
        case .gemini: return "aistudio.google.com"
        case .mistral: return "console.mistral.ai"
        case .xai: return "console.x.ai"
        case .deepseek: return "platform.deepseek.com"
        case .ollama: return "ollama.com"
        }
    }

    /// Default base URL for providers that speak the OpenAI
    /// chat-completions dialect; nil for providers with their own wire
    /// format. Ollama's is only a default - the host is configurable.
    public var openAICompatibleBaseURL: URL? {
        switch self {
        case .anthropic, .gemini: return nil
        case .openai: return URL(string: "https://api.openai.com/v1")!
        case .mistral: return URL(string: "https://api.mistral.ai/v1")!
        case .xai: return URL(string: "https://api.x.ai/v1")!
        case .deepseek: return URL(string: "https://api.deepseek.com/v1")!
        case .ollama: return OllamaEndpoint.baseURL(host: OllamaEndpoint.defaultHost)
        }
    }

    /// Model-list filter: OpenAI-style listings mix chat models with
    /// embeddings, audio and image models; keep chat-capable text models.
    public func includesModel(_ modelID: String) -> Bool {
        let lower = modelID.lowercased()
        switch self {
        case .anthropic, .gemini:
            return true // their APIs are filtered during parsing
        case .openai:
            let excluded = [
                "embedding", "whisper", "tts", "audio", "realtime", "image",
                "dall-e", "moderation", "transcribe", "computer-use", "search", "instruct"
            ]
            if excluded.contains(where: lower.contains) { return false }
            if lower.hasPrefix("gpt-") { return true }
            return lower.range(of: "^o[0-9]", options: .regularExpression) != nil
        case .mistral:
            let excluded = ["embed", "moderation", "ocr", "transcribe", "voxtral"]
            return !excluded.contains(where: lower.contains)
        case .xai:
            return !lower.contains("image")
        case .deepseek:
            return true
        case .ollama:
            return !lower.contains("embed")
        }
    }
}

/// Ollama's server address is user-configurable (OLLAMA_HOST, Docker port
/// mappings, another machine on the LAN). This normalizes whatever the
/// user typed into a usable OpenAI-compatible base URL.
public enum OllamaEndpoint {
    public static let defaultHost = "http://localhost:11434"

    public static func baseURL(host: String) -> URL? {
        var trimmed = host.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { trimmed = defaultHost }
        if !trimmed.contains("://") { trimmed = "http://" + trimmed }
        while trimmed.hasSuffix("/") { trimmed.removeLast() }
        if !trimmed.hasSuffix("/v1") { trimmed += "/v1" }
        return URL(string: trimmed)
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

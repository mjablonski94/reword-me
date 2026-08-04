import Foundation

/// How RewordMe authenticates and executes one provider.
public enum ProviderAccess: Sendable, Equatable {
    /// RewordMe calls the provider's public API with a key from Keychain.
    case apiKey
    /// RewordMe delegates to an official, locally authenticated agent CLI.
    case account
    /// RewordMe owns both the downloadable model and its bundled runtime.
    case managedLocal
    /// A separately installed local service, currently Ollama.
    case externalLocal
}

/// The LLM providers RewordMe can talk to. Existing API raw values deliberately
/// stay unchanged so the new account-backed choices never reinterpret a saved
/// key or configuration. Declaration order is the order shown in Settings.
public enum ProviderKind: String, Codable, CaseIterable, Sendable, Identifiable {
    case gemini
    case local
    case openai
    case codex
    case anthropic
    case claudeAccount
    case mistral
    case xai
    case deepseek
    case ollama

    public var id: String { rawValue }

    public var displayName: String {
        switch self {
        case .gemini: return "Gemini (Recommended)"
        case .local: return "Offline models (Local)"
        case .openai: return "OpenAI API"
        case .codex: return "Codex via ChatGPT"
        case .anthropic: return "Claude API"
        case .claudeAccount: return "Claude via Claude account"
        case .mistral: return "Mistral"
        case .xai: return "Grok (xAI)"
        case .deepseek: return "DeepSeek"
        case .ollama: return "Ollama (External local)"
        }
    }

    public var access: ProviderAccess {
        switch self {
        case .gemini, .openai, .anthropic, .mistral, .xai, .deepseek:
            return .apiKey
        case .codex, .claudeAccount:
            return .account
        case .local:
            return .managedLocal
        case .ollama:
            return .externalLocal
        }
    }

    public var requiresAPIKey: Bool {
        access == .apiKey
    }

    public var isAccountProvider: Bool {
        access == .account
    }

    public var keyPlaceholder: String {
        switch self {
        case .gemini: return "AIza..."
        case .local, .codex, .claudeAccount, .ollama: return ""
        case .openai: return "sk-..."
        case .anthropic: return "sk-ant-..."
        case .mistral: return "API key from console.mistral.ai"
        case .xai: return "xai-..."
        case .deepseek: return "sk-..."
        }
    }

    /// Where the user creates an API key for this provider.
    public var apiKeyConsoleURL: URL {
        switch self {
        case .gemini: return URL(string: "https://aistudio.google.com/apikey")!
        case .local: return LocalModelCatalog.defaultModel.informationURL
        case .openai: return URL(string: "https://platform.openai.com/api-keys")!
        case .codex: return URL(string: "https://developers.openai.com/codex/cli")!
        case .anthropic: return URL(string: "https://platform.claude.com/settings/keys")!
        case .claudeAccount:
            return URL(string: "https://docs.anthropic.com/en/docs/claude-code/getting-started")!
        case .mistral: return URL(string: "https://console.mistral.ai/api-keys")!
        case .xai: return URL(string: "https://console.x.ai")!
        case .deepseek: return URL(string: "https://platform.deepseek.com/api_keys")!
        case .ollama: return URL(string: "https://ollama.com/download")!
        }
    }

    public var apiKeyConsoleName: String {
        switch self {
        case .gemini: return "aistudio.google.com"
        case .local: return "huggingface.co"
        case .openai: return "platform.openai.com"
        case .codex: return "developers.openai.com"
        case .anthropic: return "platform.claude.com"
        case .claudeAccount: return "docs.anthropic.com"
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
        case .anthropic, .gemini, .codex, .claudeAccount: return nil
        // The managed server always supplies an endpoint override. A non-nil
        // value only declares that the provider speaks this wire format.
        case .local: return URL(string: "http://127.0.0.1:1/v1")!
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
        case .codex, .claudeAccount, .local:
            return true // these catalogs are supplied locally, not parsed here
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

/// One pinned GGUF download. Exact revisions, byte counts and digests prevent
/// a mutable model-hosting URL from silently changing underneath RewordMe.
public struct OfflineModelManifest: Equatable, Sendable, Identifiable {
    public let id: String
    public let displayName: String
    public let maker: String
    public let tier: String
    public let fileName: String
    public let byteCount: Int64
    public let sha256: String
    public let revision: String
    public let repository: String
    public let licenseName: String
    public let licenseURL: URL

    public var downloadURL: URL {
        URL(string: "https://huggingface.co/\(repository)/resolve/\(revision)/\(fileName)")!
    }

    public var informationURL: URL {
        URL(string: "https://huggingface.co/\(repository)")!
    }
}

public enum LocalModelCatalog {
    private static let apache = URL(string: "https://www.apache.org/licenses/LICENSE-2.0")!

    public static let qwen35Small = OfflineModelManifest(
        id: "qwen3.5-0.8b-q4_0", displayName: "Qwen 3.5 0.8B Q4",
        maker: "Qwen", tier: "Fastest", fileName: "Qwen3.5-0.8B-Q4_0.gguf",
        byteCount: 563_036_064,
        sha256: "57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf",
        revision: "8fea620810c4afa23dd6443f999a48574c1611a3",
        repository: "ggml-org/Qwen3.5-0.8B-GGUF",
        licenseName: "Apache 2.0", licenseURL: apache
    )
    public static let gemma3 = OfflineModelManifest(
        id: "gemma-3-1b-it-q4_k_m", displayName: "Gemma 3 1B IT Q4",
        maker: "Google", tier: "Compact", fileName: "gemma-3-1b-it-Q4_K_M.gguf",
        byteCount: 806_058_240,
        sha256: "8ccc5cd1f1b3602548715ae25a66ed73fd5dc68a210412eea643eb20eb75a135",
        revision: "f9c28bcd85737ffc5aef028638d3341d49869c27",
        repository: "ggml-org/gemma-3-1b-it-GGUF",
        licenseName: "Gemma Terms", licenseURL: URL(string: "https://ai.google.dev/gemma/terms")!
    )
    public static let qwen3Balanced = OfflineModelManifest(
        id: "qwen3-1.7b-q4_k_m", displayName: "Qwen 3 1.7B Q4",
        maker: "Qwen", tier: "Balanced", fileName: "Qwen3-1.7B-Q4_K_M.gguf",
        byteCount: 1_282_439_264,
        sha256: "d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5",
        revision: "daeb8e2d528a760970442092f6bf1e55c3b659eb",
        repository: "ggml-org/Qwen3-1.7B-GGUF",
        licenseName: "Apache 2.0", licenseURL: apache
    )
    public static let smolLM3 = OfflineModelManifest(
        id: "smollm3-3b-q4_k_m", displayName: "SmolLM3 3B Q4",
        maker: "Hugging Face", tier: "English-focused", fileName: "SmolLM3-Q4_K_M.gguf",
        byteCount: 1_915_305_312,
        sha256: "8334b850b7bd46238c16b0c550df2138f0889bf433809008cc17a8b05761863e",
        revision: "4965cb60b150737b68a0408c36aeefb65078f894",
        repository: "ggml-org/SmolLM3-3B-GGUF",
        licenseName: "Apache 2.0", licenseURL: apache
    )
    public static let ministral3 = OfflineModelManifest(
        id: "ministral-3-3b-instruct-q4_k_m", displayName: "Ministral 3 3B Instruct Q4",
        maker: "Mistral AI", tier: "Quality alternative",
        fileName: "Ministral-3-3B-Instruct-2512-Q4_K_M.gguf",
        byteCount: 2_147_023_008,
        sha256: "9ed150d4367e68df0ac8e1540f6ddc65b42d0ee26378329d1ecbca60f93fc5f8",
        revision: "eb599d408350ea2bb60452cb86be7c7b2fc28227",
        repository: "mistralai/Ministral-3-3B-Instruct-2512-GGUF",
        licenseName: "Apache 2.0", licenseURL: apache
    )
    public static let qwen3Quality = OfflineModelManifest(
        id: "qwen3-4b-q4_k_m", displayName: "Qwen 3 4B Q4",
        maker: "Qwen", tier: "Best multilingual quality", fileName: "Qwen3-4B-Q4_K_M.gguf",
        byteCount: 2_497_280_640,
        sha256: "ab27b9bfa375a178d6cba48f3ad892b94b7739659dcc7aae8058ce0ffed6b328",
        revision: "2f3b082b1356a6123f7ed71e65aea340da25d53c",
        repository: "ggml-org/Qwen3-4B-GGUF",
        licenseName: "Apache 2.0", licenseURL: apache
    )

    public static let all = [qwen35Small, gemma3, qwen3Balanced, smolLM3, ministral3, qwen3Quality]
    public static let defaultModel = qwen35Small

    public static func model(id: String?) -> OfflineModelManifest {
        all.first { $0.id == id } ?? defaultModel
    }
}

/// Compatibility aliases for the original single-model implementation.
public enum LocalModelManifest {
    public static var id: String { LocalModelCatalog.defaultModel.id }
    public static var displayName: String { LocalModelCatalog.defaultModel.displayName }
    public static var fileName: String { LocalModelCatalog.defaultModel.fileName }
    public static var byteCount: Int64 { LocalModelCatalog.defaultModel.byteCount }
    public static var sha256: String { LocalModelCatalog.defaultModel.sha256 }
    public static var revision: String { LocalModelCatalog.defaultModel.revision }
    public static var downloadURL: URL { LocalModelCatalog.defaultModel.downloadURL }
    public static var informationURL: URL { LocalModelCatalog.defaultModel.informationURL }
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
    case usageLimitReached
    case refused(String?)
    case apiError(status: Int, message: String)
    case emptyResponse
    case invalidResponse
    case noModelAvailable
    case providerNotInstalled(String)
    case accountNotSignedIn(String)
    case accountUsesAPIKey(String)
    case accountCommandFailed(message: String, retryable: Bool)
    case localModelNotDownloaded
    case localRuntimeUnavailable
    case localModelDownloadFailed(String)

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
        case .usageLimitReached:
            return "API credits or usage limit reached. Check the provider's billing and limits."
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
        case let .providerNotInstalled(name):
            return "\(name) is not installed. Open Settings to install it."
        case let .accountNotSignedIn(name):
            return "\(name) is not signed in. Open Settings to connect your account."
        case let .accountUsesAPIKey(name):
            return "\(name) is using API-key billing. Sign in with your subscription account instead."
        case let .accountCommandFailed(message, _):
            return message
        case .localModelNotDownloaded:
            return "The local model is not downloaded. Download it in Settings."
        case .localRuntimeUnavailable:
            return "The bundled local AI runtime is unavailable. Reinstall RewordMe."
        case let .localModelDownloadFailed(message):
            return "Local model download failed: \(message)"
        }
    }

    /// Whether repeating the same request can reasonably succeed without the
    /// user changing credentials, billing, limits or provider configuration.
    public var isRetryable: Bool {
        switch self {
        case .rateLimited, .emptyResponse, .invalidResponse:
            return true
        case let .apiError(status, _):
            return status == 408 || status == 409 || status == 425 || (500...599).contains(status)
        case let .accountCommandFailed(_, retryable):
            return retryable
        case .missingAPIKey, .invalidAPIKey, .usageLimitReached, .refused, .noModelAvailable,
             .providerNotInstalled, .accountNotSignedIn, .accountUsesAPIKey,
             .localModelNotDownloaded, .localRuntimeUnavailable:
            return false
        case .localModelDownloadFailed:
            return true
        }
    }
}

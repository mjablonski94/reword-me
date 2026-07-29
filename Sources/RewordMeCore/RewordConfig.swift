import Foundation

/// Everything except API keys (those live in the Keychain).
public struct RewordConfig: Codable, Equatable, Sendable {
    public var provider: ProviderKind
    /// nil means automatic: the least costly model the provider lists.
    public var model: String?
    public var rules: [RewriteRule]
    public var basePrompt: String
    /// Where the local Ollama server listens; only used by the ollama provider.
    public var ollamaHost: String

    public init(
        provider: ProviderKind = .anthropic,
        model: String? = nil,
        rules: [RewriteRule] = [],
        basePrompt: String = "",
        ollamaHost: String = OllamaEndpoint.defaultHost
    ) {
        self.provider = provider
        self.model = model
        self.rules = rules
        self.basePrompt = basePrompt
        self.ollamaHost = ollamaHost
    }

    public static let `default` = RewordConfig()

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        provider = try container.decodeIfPresent(ProviderKind.self, forKey: .provider) ?? .anthropic
        model = try container.decodeIfPresent(String.self, forKey: .model)
        rules = try container.decodeIfPresent([RewriteRule].self, forKey: .rules) ?? []
        basePrompt = try container.decodeIfPresent(String.self, forKey: .basePrompt) ?? ""
        ollamaHost = try container.decodeIfPresent(String.self, forKey: .ollamaHost)
            ?? OllamaEndpoint.defaultHost
    }

    /// Endpoint override for providers whose server address is user
    /// configurable; nil means "use the provider's default".
    public var endpointOverride: URL? {
        provider == .ollama ? OllamaEndpoint.baseURL(host: ollamaHost) : nil
    }
}

/// Loads and saves the config as JSON in Application Support.
public struct ConfigStore: Sendable {
    public let url: URL

    public init(url: URL = ConfigStore.defaultURL) {
        self.url = url
    }

    public static let defaultURL: URL = {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("RewordMe/config.json")
    }()

    public func load() -> RewordConfig {
        guard let data = try? Data(contentsOf: url),
              let config = try? JSONDecoder().decode(RewordConfig.self, from: data) else {
            return .default
        }
        return config
    }

    public func save(_ config: RewordConfig) throws {
        try FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        try encoder.encode(config).write(to: url, options: .atomic)
    }
}

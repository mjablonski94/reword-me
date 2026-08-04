import Foundation

/// The global shortcut that summons the popup. Carbon masks and virtual
/// key codes are stored as raw values so the core stays framework-free.
public struct HotkeyConfig: Codable, Equatable, Sendable {
    public static let carbonCommand: UInt32 = 0x0100
    public static let carbonShift: UInt32 = 0x0200
    public static let carbonOption: UInt32 = 0x0800
    public static let carbonControl: UInt32 = 0x1000

    /// Virtual key code (kVK_*); 15 is the R key.
    public var keyCode: UInt32
    public var carbonModifiers: UInt32
    /// Lowercase character for menu key equivalents ("r"); empty when the
    /// key has no simple character (function keys etc.).
    public var character: String
    /// Human-readable form, e.g. "⌥⌘R".
    public var display: String

    public init(
        keyCode: UInt32 = 15,
        carbonModifiers: UInt32 = HotkeyConfig.carbonCommand | HotkeyConfig.carbonOption,
        character: String = "r",
        display: String = "⌥⌘R"
    ) {
        self.keyCode = keyCode
        self.carbonModifiers = carbonModifiers
        self.character = character
        self.display = display
    }

    public static let `default` = HotkeyConfig()
}

/// Everything except API keys (those live in the Keychain).
public struct RewordConfig: Codable, Equatable, Sendable {
    public var provider: ProviderKind
    /// A provider owns its model choice. Keeping the choices in one map means
    /// switching away and back restores the right selection without ever
    /// sending (for example) a Gemini model id to OpenAI.
    public var modelsByProvider: [String: String]
    /// The model selected for `provider`; nil means automatic. This computed
    /// compatibility property keeps the rest of the app and older call sites
    /// source-compatible while persistence uses `modelsByProvider`.
    public var model: String? {
        get { modelsByProvider[provider.rawValue] }
        set {
            let normalized = newValue?.trimmingCharacters(in: .whitespacesAndNewlines)
            if let normalized, !normalized.isEmpty {
                modelsByProvider[provider.rawValue] = normalized
            } else {
                modelsByProvider.removeValue(forKey: provider.rawValue)
            }
        }
    }
    public var rules: [RewriteRule]
    public var basePrompt: String
    /// Where the local Ollama server listens; only used by the ollama provider.
    public var ollamaHost: String
    public var hotkey: HotkeyConfig
    /// nil until the user has had a say. The app registers itself for startup
    /// on first run - a menu-bar app that is not running cannot answer its
    /// shortcut - and records the answer here, so switching it off sticks.
    public var launchAtLogin: Bool?

    public init(
        provider: ProviderKind = .gemini,
        model: String? = nil,
        modelsByProvider: [String: String] = [:],
        rules: [RewriteRule] = [],
        basePrompt: String = "",
        ollamaHost: String = OllamaEndpoint.defaultHost,
        hotkey: HotkeyConfig = .default,
        launchAtLogin: Bool? = nil
    ) {
        self.provider = provider
        self.modelsByProvider = modelsByProvider
        if let model = model?.trimmingCharacters(in: .whitespacesAndNewlines), !model.isEmpty {
            self.modelsByProvider[provider.rawValue] = model
        }
        self.rules = rules
        self.basePrompt = basePrompt
        self.ollamaHost = ollamaHost
        self.hotkey = hotkey
        self.launchAtLogin = launchAtLogin
    }

    public static let `default` = RewordConfig()

    private enum CodingKeys: String, CodingKey {
        case provider
        /// Written for compatibility with 1.0.1 and used as a migration input.
        case model
        case modelsByProvider
        case rules
        case basePrompt
        case ollamaHost
        case hotkey
        case launchAtLogin
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        provider = try container.decodeIfPresent(ProviderKind.self, forKey: .provider) ?? .gemini
        modelsByProvider = try container.decodeIfPresent(
            [String: String].self, forKey: .modelsByProvider
        ) ?? [:]
        // 1.0.1 stored one global model. On first read, bind that value to the
        // provider saved beside it so it cannot leak into a different provider.
        if modelsByProvider[provider.rawValue] == nil,
           let legacy = try container.decodeIfPresent(String.self, forKey: .model)?
            .trimmingCharacters(in: .whitespacesAndNewlines),
           !legacy.isEmpty {
            modelsByProvider[provider.rawValue] = legacy
        }
        rules = try container.decodeIfPresent([RewriteRule].self, forKey: .rules) ?? []
        basePrompt = try container.decodeIfPresent(String.self, forKey: .basePrompt) ?? ""
        ollamaHost = try container.decodeIfPresent(String.self, forKey: .ollamaHost)
            ?? OllamaEndpoint.defaultHost
        hotkey = try container.decodeIfPresent(HotkeyConfig.self, forKey: .hotkey) ?? .default
        launchAtLogin = try container.decodeIfPresent(Bool.self, forKey: .launchAtLogin)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(provider, forKey: .provider)
        try container.encodeIfPresent(model, forKey: .model)
        try container.encode(modelsByProvider, forKey: .modelsByProvider)
        try container.encode(rules, forKey: .rules)
        try container.encode(basePrompt, forKey: .basePrompt)
        try container.encode(ollamaHost, forKey: .ollamaHost)
        try container.encode(hotkey, forKey: .hotkey)
        try container.encodeIfPresent(launchAtLogin, forKey: .launchAtLogin)
    }

    /// Endpoint override for providers whose server address is user
    /// configurable; nil means "use the provider's default".
    public var endpointOverride: URL? {
        provider == .ollama ? OllamaEndpoint.baseURL(host: ollamaHost) : nil
    }
}

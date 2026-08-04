import Foundation

/// Legacy persisted classification from RewordMe 1.0.1. The current UI treats
/// every entry as a literal rule, but decoding this value preserves upgrades.
public enum RuleKind: String, Codable, Sendable {
    case doRule = "do"
    case dontRule = "dont"
}

/// A user-defined instruction, toggleable per rule. `kind` remains only so
/// older configuration files round-trip without losing information.
public struct RewriteRule: Codable, Equatable, Sendable, Identifiable, Hashable {
    public var id: UUID
    public var kind: RuleKind
    public var text: String
    public var isEnabled: Bool

    public init(
        id: UUID = UUID(),
        kind: RuleKind = .doRule,
        text: String,
        isEnabled: Bool = true
    ) {
        self.id = id
        self.kind = kind
        self.text = text
        self.isEnabled = isEnabled
    }
}

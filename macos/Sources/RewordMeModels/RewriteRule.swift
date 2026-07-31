import Foundation

public enum RuleKind: String, Codable, Sendable, CaseIterable {
    case doRule = "do"
    case dontRule = "dont"
}

/// A user-defined do/don't rule, toggleable per rule.
public struct RewriteRule: Codable, Equatable, Sendable, Identifiable, Hashable {
    public var id: UUID
    public var kind: RuleKind
    public var text: String
    public var isEnabled: Bool

    public init(id: UUID = UUID(), kind: RuleKind, text: String, isEnabled: Bool = true) {
        self.id = id
        self.kind = kind
        self.text = text
        self.isEnabled = isEnabled
    }
}

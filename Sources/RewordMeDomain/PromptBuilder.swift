import Foundation
import RewordMeModels

/// Assembles the system prompt from four layers:
/// 1. the fixed core instruction, 2. enabled do/don't rules,
/// 3. the user's freeform base prompt, 4. one-shot steering from the popup.
public enum PromptBuilder {
    public static let coreInstruction = """
    You rewrite text. Rewrite the text the user gives you while preserving its meaning \
    and its language. Match the original register and tone unless instructed otherwise. \
    Keep formatting, line breaks, placeholders, code, URLs and @mentions intact. \
    Output only the rewritten text - no preamble, no surrounding quotes, no explanations.
    """

    public static func systemPrompt(
        rules: [RewriteRule],
        basePrompt: String,
        steering: String?
    ) -> String {
        var sections: [String] = [coreInstruction]

        let dos = enabledTexts(rules, kind: .doRule)
        if !dos.isEmpty {
            sections.append("Do:\n" + bulleted(dos))
        }
        let donts = enabledTexts(rules, kind: .dontRule)
        if !donts.isEmpty {
            sections.append("Don't:\n" + bulleted(donts))
        }

        let base = basePrompt.trimmingCharacters(in: .whitespacesAndNewlines)
        if !base.isEmpty {
            sections.append(base)
        }

        if let steering = steering?.trimmingCharacters(in: .whitespacesAndNewlines), !steering.isEmpty {
            sections.append("Additional instruction for this rewrite only: \(steering)")
        }

        return sections.joined(separator: "\n\n")
    }

    private static func enabledTexts(_ rules: [RewriteRule], kind: RuleKind) -> [String] {
        rules
            .filter { $0.isEnabled && $0.kind == kind }
            .map { $0.text.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    private static func bulleted(_ lines: [String]) -> String {
        lines.map { "- \($0)" }.joined(separator: "\n")
    }
}

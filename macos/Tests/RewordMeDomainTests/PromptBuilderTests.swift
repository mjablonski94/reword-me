import XCTest
import RewordMeModels
@testable import RewordMeDomain

final class PromptBuilderTests: XCTestCase {
    func testCoreOnlyWhenNothingElseConfigured() {
        let prompt = PromptBuilder.systemPrompt(rules: [], basePrompt: "", steering: nil)
        XCTAssertEqual(prompt, PromptBuilder.coreInstruction)
    }

    func testCoreInstructionForbidsPreamble() {
        XCTAssertTrue(PromptBuilder.coreInstruction.contains("Output only the rewritten text"))
    }

    func testEnabledRulesShareOneLiteralSectionRegardlessOfLegacyKind() {
        let rules = [
            RewriteRule(kind: .doRule, text: "Keep it under two sentences"),
            RewriteRule(kind: .dontRule, text: "Never use exclamation marks"),
            RewriteRule(kind: .doRule, text: "Use British spelling")
        ]
        let prompt = PromptBuilder.systemPrompt(rules: rules, basePrompt: "", steering: nil)
        XCTAssertTrue(prompt.contains(
            "Rules:\n- Keep it under two sentences\n- Never use exclamation marks\n- Use British spelling"
        ))
        XCTAssertFalse(prompt.contains("Do:"))
        XCTAssertFalse(prompt.contains("Don't:"))
    }

    func testDisabledAndEmptyRulesAreSkipped() {
        let rules = [
            RewriteRule(kind: .doRule, text: "Be formal", isEnabled: false),
            RewriteRule(kind: .dontRule, text: "   ")
        ]
        let prompt = PromptBuilder.systemPrompt(rules: rules, basePrompt: "", steering: nil)
        XCTAssertFalse(prompt.contains("Rules:"))
        XCTAssertFalse(prompt.contains("Be formal"))
    }

    func testBasePromptIsAppendedAfterRules() {
        let rules = [RewriteRule(kind: .doRule, text: "Be brief")]
        let prompt = PromptBuilder.systemPrompt(
            rules: rules,
            basePrompt: "I am a non-native speaker, keep my voice.",
            steering: nil
        )
        let rulesIndex = prompt.range(of: "Rules:")!.lowerBound
        let baseIndex = prompt.range(of: "non-native speaker")!.lowerBound
        XCTAssertLessThan(rulesIndex, baseIndex)
    }

    func testSteeringIsAppendedLastAndMarkedOneShot() {
        let prompt = PromptBuilder.systemPrompt(
            rules: [],
            basePrompt: "Base.",
            steering: "make it more formal"
        )
        XCTAssertTrue(prompt.hasSuffix("Additional instruction for this rewrite only: make it more formal"))
    }

    func testBlankSteeringIsIgnored() {
        let prompt = PromptBuilder.systemPrompt(rules: [], basePrompt: "", steering: "  \n ")
        XCTAssertEqual(prompt, PromptBuilder.coreInstruction)
    }
}

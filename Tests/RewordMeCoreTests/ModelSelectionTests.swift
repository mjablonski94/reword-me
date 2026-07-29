import XCTest
@testable import RewordMeCore

final class ModelSelectionTests: XCTestCase {
    func testAnthropicPicksNewestHaiku() {
        let models = [
            ModelInfo(id: "claude-opus-4-8"),
            ModelInfo(id: "claude-sonnet-4-6"),
            ModelInfo(id: "claude-3-5-haiku-20241022"),
            ModelInfo(id: "claude-haiku-4-5")
        ]
        let pick = ModelSelection.defaultModel(for: .anthropic, from: models)
        XCTAssertEqual(pick?.id, "claude-haiku-4-5")
    }

    func testOpenAIPicksNanoOverMiniOverBase() {
        let models = [
            ModelInfo(id: "gpt-4o"),
            ModelInfo(id: "gpt-4o-mini"),
            ModelInfo(id: "gpt-4.1-nano"),
            ModelInfo(id: "gpt-5-nano")
        ]
        let pick = ModelSelection.defaultModel(for: .openai, from: models)
        XCTAssertEqual(pick?.id, "gpt-5-nano")
    }

    func testGeminiPicksFlashLiteOverFlashAndPro() {
        let models = [
            ModelInfo(id: "gemini-2.5-pro"),
            ModelInfo(id: "gemini-2.5-flash"),
            ModelInfo(id: "gemini-2.5-flash-lite"),
            ModelInfo(id: "gemini-2.0-flash-lite")
        ]
        let pick = ModelSelection.defaultModel(for: .gemini, from: models)
        XCTAssertEqual(pick?.id, "gemini-2.5-flash-lite")
    }

    func testPreviewModelsAreDeprioritizedWithinATier() {
        let models = [
            ModelInfo(id: "gemini-3.0-flash-lite-preview"),
            ModelInfo(id: "gemini-2.5-flash-lite")
        ]
        let pick = ModelSelection.defaultModel(for: .gemini, from: models)
        XCTAssertEqual(pick?.id, "gemini-2.5-flash-lite")
    }

    func testPreviewModelIsUsedWhenNothingStableExistsInTier() {
        let models = [ModelInfo(id: "gemini-3.0-flash-lite-preview")]
        let pick = ModelSelection.defaultModel(for: .gemini, from: models)
        XCTAssertEqual(pick?.id, "gemini-3.0-flash-lite-preview")
    }

    func testEmptyListReturnsNil() {
        XCTAssertNil(ModelSelection.defaultModel(for: .anthropic, from: []))
    }
}

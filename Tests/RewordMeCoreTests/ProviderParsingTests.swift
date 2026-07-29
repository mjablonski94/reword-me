import XCTest
@testable import RewordMeCore

final class ProviderParsingTests: XCTestCase {
    // MARK: - Anthropic

    func testAnthropicParseModels() throws {
        let json = """
        {"data":[
          {"id":"claude-haiku-4-5","display_name":"Claude Haiku 4.5","created_at":"2025-10-01T00:00:00Z"},
          {"id":"claude-opus-5","display_name":"Claude Opus 5","created_at":"2026-05-01T00:00:00Z"}
        ],"has_more":false}
        """
        let models = try AnthropicAPI.parseModels(Data(json.utf8))
        XCTAssertEqual(models.map(\.id), ["claude-haiku-4-5", "claude-opus-5"])
        XCTAssertEqual(models[0].displayName, "Claude Haiku 4.5")
    }

    func testAnthropicParseRewordJoinsTextBlocks() throws {
        let json = """
        {"content":[{"type":"text","text":"Hello "},{"type":"text","text":"world."}],
         "stop_reason":"end_turn"}
        """
        XCTAssertEqual(try AnthropicAPI.parseReword(Data(json.utf8)), "Hello world.")
    }

    func testAnthropicRefusalThrows() {
        let json = """
        {"content":[],"stop_reason":"refusal",
         "stop_details":{"type":"refusal","category":"cyber","explanation":"declined"}}
        """
        XCTAssertThrowsError(try AnthropicAPI.parseReword(Data(json.utf8))) { error in
            XCTAssertEqual(error as? RewordError, .refused("declined"))
        }
    }

    func testAnthropicRewordRequestShape() throws {
        let request = try AnthropicAPI.rewordRequest(
            apiKey: "k", model: "claude-haiku-4-5", systemPrompt: "sys", text: "hi"
        )
        XCTAssertEqual(request.httpMethod, "POST")
        XCTAssertEqual(request.value(forHTTPHeaderField: "x-api-key"), "k")
        XCTAssertEqual(request.value(forHTTPHeaderField: "anthropic-version"), "2023-06-01")
        let body = try JSONSerialization.jsonObject(with: request.httpBody!) as! [String: Any]
        XCTAssertEqual(body["model"] as? String, "claude-haiku-4-5")
        XCTAssertEqual(body["system"] as? String, "sys")
        XCTAssertEqual(body["max_tokens"] as? Int, 1024)
        let messages = body["messages"] as! [[String: Any]]
        XCTAssertEqual(messages.count, 1)
        XCTAssertEqual(messages[0]["role"] as? String, "user")
        XCTAssertEqual(messages[0]["content"] as? String, "hi")
    }

    func testAnthropicOutputBudgetScalesWithInputAndIsCapped() {
        XCTAssertEqual(AnthropicAPI.outputTokenBudget(for: "short"), 1024)
        XCTAssertEqual(
            AnthropicAPI.outputTokenBudget(for: String(repeating: "a", count: 2000)),
            2000
        )
        XCTAssertEqual(
            AnthropicAPI.outputTokenBudget(for: String(repeating: "a", count: 100_000)),
            8192
        )
    }

    // MARK: - OpenAI

    func testOpenAIModelFilterKeepsChatModelsOnly() {
        XCTAssertTrue(OpenAIAPI.isChatModel("gpt-4o-mini"))
        XCTAssertTrue(OpenAIAPI.isChatModel("gpt-5-nano"))
        XCTAssertTrue(OpenAIAPI.isChatModel("o3-mini"))
        XCTAssertFalse(OpenAIAPI.isChatModel("text-embedding-3-small"))
        XCTAssertFalse(OpenAIAPI.isChatModel("whisper-1"))
        XCTAssertFalse(OpenAIAPI.isChatModel("gpt-4o-realtime-preview"))
        XCTAssertFalse(OpenAIAPI.isChatModel("dall-e-3"))
        XCTAssertFalse(OpenAIAPI.isChatModel("gpt-4o-audio-preview"))
    }

    func testOpenAIParseModels() throws {
        let json = """
        {"data":[{"id":"gpt-4o-mini","object":"model"},
                 {"id":"whisper-1","object":"model"},
                 {"id":"gpt-4o","object":"model"}]}
        """
        let models = try OpenAIAPI.parseModels(Data(json.utf8))
        XCTAssertEqual(models.map(\.id), ["gpt-4o", "gpt-4o-mini"])
    }

    func testOpenAIParseReword() throws {
        let json = """
        {"choices":[{"message":{"role":"assistant","content":"  Rewritten.  "}}]}
        """
        XCTAssertEqual(try OpenAIAPI.parseReword(Data(json.utf8)), "Rewritten.")
    }

    func testOpenAIEmptyContentThrows() {
        let json = """
        {"choices":[{"message":{"role":"assistant","content":""}}]}
        """
        XCTAssertThrowsError(try OpenAIAPI.parseReword(Data(json.utf8))) { error in
            XCTAssertEqual(error as? RewordError, .emptyResponse)
        }
    }

    // MARK: - Gemini

    func testGeminiParseModelsFiltersOnGenerateContentAndStripsPrefix() throws {
        let json = """
        {"models":[
          {"name":"models/gemini-2.5-flash-lite","displayName":"Gemini 2.5 Flash-Lite",
           "supportedGenerationMethods":["generateContent","countTokens"]},
          {"name":"models/text-embedding-004","displayName":"Embedding",
           "supportedGenerationMethods":["embedContent"]}
        ]}
        """
        let models = try GeminiAPI.parseModels(Data(json.utf8))
        XCTAssertEqual(models.map(\.id), ["gemini-2.5-flash-lite"])
        XCTAssertEqual(models[0].displayName, "Gemini 2.5 Flash-Lite")
    }

    func testGeminiKeyGoesInHeaderNotURL() throws {
        let modelsRequest = GeminiAPI.modelsRequest(apiKey: "secret")
        XCTAssertEqual(modelsRequest.value(forHTTPHeaderField: "x-goog-api-key"), "secret")
        XCTAssertFalse(modelsRequest.url!.absoluteString.contains("secret"))

        let rewordRequest = try GeminiAPI.rewordRequest(
            apiKey: "secret", model: "gemini-2.5-flash-lite", systemPrompt: "s", text: "t"
        )
        XCTAssertEqual(rewordRequest.value(forHTTPHeaderField: "x-goog-api-key"), "secret")
        XCTAssertFalse(rewordRequest.url!.absoluteString.contains("secret"))
    }

    func testGeminiParseReword() throws {
        let json = """
        {"candidates":[{"content":{"role":"model","parts":[{"text":"Re"},{"text":"written."}]}}]}
        """
        XCTAssertEqual(try GeminiAPI.parseReword(Data(json.utf8)), "Rewritten.")
    }

    // MARK: - Shared error envelope

    func testErrorMessageExtraction() {
        let anthropicStyle = #"{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"#
        XCTAssertEqual(RewordService.errorMessage(from: Data(anthropicStyle.utf8)), "Overloaded")

        let plain = #"{"message":"boom"}"#
        XCTAssertEqual(RewordService.errorMessage(from: Data(plain.utf8)), "boom")

        let garbage = "not json"
        XCTAssertEqual(RewordService.errorMessage(from: Data(garbage.utf8)), "not json")
    }
}

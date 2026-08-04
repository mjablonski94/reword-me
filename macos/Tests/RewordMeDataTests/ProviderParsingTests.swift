import XCTest
import RewordMeModels
@testable import RewordMeData

final class ProviderParsingTests: XCTestCase {
    // MARK: - Anthropic

    func testAnthropicParseModels() throws {
        let json = """
        {"data":[
          {"id":"claude-haiku-4-5","display_name":"Claude Haiku 4.5","created_at":"2025-10-01T00:00:00Z"},
          {"id":"claude-opus-5","display_name":"Claude Opus 5","created_at":"2026-05-01T00:00:00Z"}
        ],"has_more":false}
        """
        let models = try AnthropicClient().parseModels(Data(json.utf8))
        XCTAssertEqual(models.map(\.id), ["claude-haiku-4-5", "claude-opus-5"])
        XCTAssertEqual(models[0].displayName, "Claude Haiku 4.5")
    }

    func testAnthropicParseRewordJoinsTextBlocks() throws {
        let json = """
        {"content":[{"type":"text","text":"Hello "},{"type":"text","text":"world."}],
         "stop_reason":"end_turn"}
        """
        XCTAssertEqual(try AnthropicClient().parseReword(Data(json.utf8)), "Hello world.")
    }

    func testAnthropicRefusalThrows() {
        let json = """
        {"content":[],"stop_reason":"refusal",
         "stop_details":{"type":"refusal","category":"cyber","explanation":"declined"}}
        """
        XCTAssertThrowsError(try AnthropicClient().parseReword(Data(json.utf8))) { error in
            XCTAssertEqual(error as? RewordError, .refused("declined"))
        }
    }

    func testAnthropicRewordRequestShape() throws {
        let request = try AnthropicClient().rewordRequest(
            apiKey: "k", model: "claude-haiku-4-5", systemPrompt: "sys", text: "hi", endpoint: nil
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
        XCTAssertEqual(AnthropicClient.outputTokenBudget(for: "short"), 1024)
        XCTAssertEqual(
            AnthropicClient.outputTokenBudget(for: String(repeating: "a", count: 2000)),
            2000
        )
        XCTAssertEqual(
            AnthropicClient.outputTokenBudget(for: String(repeating: "a", count: 100_000)),
            8192
        )
    }

    // MARK: - OpenAI-compatible family

    func testOpenAIModelFilterKeepsChatModelsOnly() {
        let openai = ProviderKind.openai
        XCTAssertTrue(openai.includesModel("gpt-4o-mini"))
        XCTAssertTrue(openai.includesModel("gpt-5-nano"))
        XCTAssertTrue(openai.includesModel("o3-mini"))
        XCTAssertFalse(openai.includesModel("text-embedding-3-small"))
        XCTAssertFalse(openai.includesModel("whisper-1"))
        XCTAssertFalse(openai.includesModel("gpt-4o-realtime-preview"))
        XCTAssertFalse(openai.includesModel("dall-e-3"))
        XCTAssertFalse(openai.includesModel("gpt-4o-audio-preview"))
    }

    func testMistralModelFilter() {
        let mistral = ProviderKind.mistral
        XCTAssertTrue(mistral.includesModel("ministral-8b-latest"))
        XCTAssertTrue(mistral.includesModel("mistral-small-latest"))
        XCTAssertFalse(mistral.includesModel("mistral-embed"))
        XCTAssertFalse(mistral.includesModel("mistral-moderation-latest"))
        XCTAssertFalse(mistral.includesModel("mistral-ocr-latest"))
        XCTAssertFalse(mistral.includesModel("voxtral-mini-latest"))
    }

    func testXAIModelFilter() {
        let xai = ProviderKind.xai
        XCTAssertTrue(xai.includesModel("grok-3-mini"))
        XCTAssertFalse(xai.includesModel("grok-2-image-1212"))
    }

    func testOllamaModelFilterExcludesEmbeddings() {
        let ollama = ProviderKind.ollama
        XCTAssertTrue(ollama.includesModel("llama3.2:latest"))
        XCTAssertFalse(ollama.includesModel("nomic-embed-text:latest"))
    }

    func testOllamaEndpointNormalization() {
        XCTAssertEqual(
            OllamaEndpoint.baseURL(host: "http://localhost:11434")?.absoluteString,
            "http://localhost:11434/v1"
        )
        XCTAssertEqual(
            OllamaEndpoint.baseURL(host: "192.168.1.20:11434/")?.absoluteString,
            "http://192.168.1.20:11434/v1"
        )
        XCTAssertEqual(
            OllamaEndpoint.baseURL(host: "  ")?.absoluteString,
            "http://localhost:11434/v1"
        )
        XCTAssertEqual(
            OllamaEndpoint.baseURL(host: "http://my-server:8080/v1")?.absoluteString,
            "http://my-server:8080/v1"
        )
    }

    func testProviderAccessModesAndRequiredOrder() {
        XCTAssertFalse(ProviderKind.ollama.requiresAPIKey)
        let APIProviders: Set<ProviderKind> = [
            .gemini, .openai, .anthropic, .mistral, .xai, .deepseek
        ]
        for provider in ProviderKind.allCases {
            XCTAssertEqual(provider.requiresAPIKey, APIProviders.contains(provider))
        }
        XCTAssertEqual(
            ProviderKind.allCases,
            [.gemini, .local, .openai, .codex, .anthropic, .claudeAccount,
             .mistral, .xai, .deepseek, .ollama]
        )
        XCTAssertEqual(ProviderKind.allCases[0].displayName, "Gemini (Recommended)")
        XCTAssertEqual(ProviderKind.allCases[1].displayName, "Offline models (Local)")
    }

    func testOfflineCatalogIsPinnedAndHasInformationalLicenses() {
        let models = LocalModelCatalog.all
        XCTAssertEqual(models.count, 6)
        XCTAssertEqual(Set(models.map(\.id)).count, models.count)
        XCTAssertEqual(Set(models.map(\.fileName)).count, models.count)
        XCTAssertEqual(LocalModelCatalog.defaultModel, models.first)
        XCTAssertTrue(Set(models.map(\.maker)).isSuperset(of: ["Qwen", "Google", "Hugging Face", "Mistral AI"]))
        for model in models {
            XCTAssertGreaterThan(model.byteCount, 0)
            XCTAssertNotEqual(model.tier, "")
            XCTAssertEqual(model.sha256.count, 64)
            XCTAssertTrue(model.sha256.allSatisfy { $0.isHexDigit })
            XCTAssertTrue(model.downloadURL.absoluteString.contains("/resolve/\(model.revision)/"))
            XCTAssertEqual(model.informationURL.host, "huggingface.co")
            XCTAssertTrue(model.licenseURL.scheme == "https")
            XCTAssertFalse(model.licenseName.isEmpty)
        }
    }

    func testOpenAICompatibleParseModelsFiltersAndPreservesServerOrder() throws {
        let json = """
        {"data":[{"id":"gpt-4o-mini","object":"model"},
                 {"id":"whisper-1","object":"model"},
                 {"id":"gpt-4o","object":"model"}]}
        """
        let models = try OpenAICompatibleClient(kind: .openai).parseModels(Data(json.utf8))
        // Server order is preserved (Ollama's automatic pick depends on it).
        XCTAssertEqual(models.map(\.id), ["gpt-4o-mini", "gpt-4o"])
    }

    func testOpenAICompatibleParseReword() throws {
        let json = """
        {"choices":[{"message":{"role":"assistant","content":"  Rewritten.  "}}]}
        """
        XCTAssertEqual(try OpenAICompatibleClient(kind: .openai).parseReword(Data(json.utf8)), "Rewritten.")
    }

    func testOpenAICompatibleEmptyContentThrows() {
        let json = """
        {"choices":[{"message":{"role":"assistant","content":""}}]}
        """
        XCTAssertThrowsError(try OpenAICompatibleClient(kind: .openai).parseReword(Data(json.utf8))) { error in
            XCTAssertEqual(error as? RewordError, .emptyResponse)
        }
    }

    func testOpenAICompatibleRequestsTargetTheProviderBaseURL() throws {
        for provider in ProviderKind.allCases {
            guard let base = provider.openAICompatibleBaseURL else { continue }
            let request = try OpenAICompatibleClient(kind: provider).rewordRequest(
                apiKey: "k", model: "m", systemPrompt: "s", text: "t", endpoint: nil
            )
            XCTAssertEqual(request.url?.host, base.host)
            XCTAssertTrue(request.url!.path.hasSuffix("chat/completions"))
            XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer k")
        }
    }

    func testDefaultRegistryCoversEveryHTTPProviderKind() {
        let registry = ProviderClientRegistry()
        for kind in ProviderKind.allCases where !kind.isAccountProvider {
            XCTAssertEqual(registry.client(for: kind).kind, kind)
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
        let models = try GeminiClient().parseModels(Data(json.utf8))
        XCTAssertEqual(models.map(\.id), ["gemini-2.5-flash-lite"])
        XCTAssertEqual(models[0].displayName, "Gemini 2.5 Flash-Lite")
    }

    func testGeminiKeyGoesInHeaderNotURL() throws {
        let modelsRequest = GeminiClient().modelsRequest(apiKey: "secret", endpoint: nil)
        XCTAssertEqual(modelsRequest.value(forHTTPHeaderField: "x-goog-api-key"), "secret")
        XCTAssertFalse(modelsRequest.url!.absoluteString.contains("secret"))

        let rewordRequest = try GeminiClient().rewordRequest(
            apiKey: "secret", model: "gemini-2.5-flash-lite", systemPrompt: "s", text: "t", endpoint: nil
        )
        XCTAssertEqual(rewordRequest.value(forHTTPHeaderField: "x-goog-api-key"), "secret")
        XCTAssertFalse(rewordRequest.url!.absoluteString.contains("secret"))
    }

    func testGeminiParseReword() throws {
        let json = """
        {"candidates":[{"content":{"role":"model","parts":[{"text":"Re"},{"text":"written."}]}}]}
        """
        XCTAssertEqual(try GeminiClient().parseReword(Data(json.utf8)), "Rewritten.")
    }

    // MARK: - Errors

    func testEveryErrorHasAReadableDescription() {
        let errors: [RewordError] = [
            .missingAPIKey, .invalidAPIKey,
            .rateLimited(retryAfterSeconds: 30), .rateLimited(retryAfterSeconds: nil),
            .usageLimitReached,
            .refused("nope"), .refused(nil),
            .apiError(status: 500, message: "boom"),
            .emptyResponse, .invalidResponse, .noModelAvailable,
            .providerNotInstalled("Codex"), .accountNotSignedIn("Claude"),
            .accountUsesAPIKey("Claude"),
            .accountCommandFailed(message: "offline", retryable: true),
            .localModelNotDownloaded,
            .localRuntimeUnavailable, .localModelDownloadFailed("network")
        ]
        for error in errors {
            let description = error.errorDescription ?? ""
            XCTAssertFalse(description.isEmpty, "\(error) has no description")
        }
        XCTAssertTrue(
            RewordError.rateLimited(retryAfterSeconds: 30).errorDescription!.contains("30")
        )
        XCTAssertTrue(RewordError.rateLimited(retryAfterSeconds: nil).isRetryable)
        XCTAssertFalse(RewordError.usageLimitReached.isRetryable)
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

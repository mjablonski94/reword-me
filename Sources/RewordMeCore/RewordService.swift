import Foundation

/// Performs the actual HTTP calls. Request building and parsing are
/// delegated to the per-provider enums so they stay pure and testable.
public struct RewordService: Sendable {
    private let session: URLSession

    public init(session: URLSession = .shared) {
        self.session = session
    }

    public func listModels(provider: ProviderKind, apiKey: String) async throws -> [ModelInfo] {
        let key = try validated(apiKey)
        let request: URLRequest
        switch provider {
        case .anthropic: request = AnthropicAPI.modelsRequest(apiKey: key)
        case .openai: request = OpenAIAPI.modelsRequest(apiKey: key)
        case .gemini: request = GeminiAPI.modelsRequest(apiKey: key)
        }
        let data = try await perform(request)
        switch provider {
        case .anthropic: return try AnthropicAPI.parseModels(data)
        case .openai: return try OpenAIAPI.parseModels(data)
        case .gemini: return try GeminiAPI.parseModels(data)
        }
    }

    public func reword(
        provider: ProviderKind,
        apiKey: String,
        model: String,
        systemPrompt: String,
        text: String
    ) async throws -> String {
        let key = try validated(apiKey)
        let request: URLRequest
        switch provider {
        case .anthropic:
            request = try AnthropicAPI.rewordRequest(
                apiKey: key, model: model, systemPrompt: systemPrompt, text: text
            )
        case .openai:
            request = try OpenAIAPI.rewordRequest(
                apiKey: key, model: model, systemPrompt: systemPrompt, text: text
            )
        case .gemini:
            request = try GeminiAPI.rewordRequest(
                apiKey: key, model: model, systemPrompt: systemPrompt, text: text
            )
        }
        let data = try await perform(request)
        switch provider {
        case .anthropic: return try AnthropicAPI.parseReword(data)
        case .openai: return try OpenAIAPI.parseReword(data)
        case .gemini: return try GeminiAPI.parseReword(data)
        }
    }

    private func validated(_ apiKey: String) throws -> String {
        let trimmed = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw RewordError.missingAPIKey }
        return trimmed
    }

    private func perform(_ request: URLRequest) async throws -> Data {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw RewordError.invalidResponse
        }
        switch http.statusCode {
        case 200...299:
            return data
        case 401, 403:
            throw RewordError.invalidAPIKey
        case 429:
            let retryAfter = http.value(forHTTPHeaderField: "retry-after").flatMap { Int($0) }
            throw RewordError.rateLimited(retryAfterSeconds: retryAfter)
        default:
            throw RewordError.apiError(
                status: http.statusCode,
                message: Self.errorMessage(from: data)
            )
        }
    }

    /// Best-effort extraction of a human-readable message from any of the
    /// three providers' error envelopes.
    static func errorMessage(from data: Data) -> String {
        guard let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return String(data: data.prefix(200), encoding: .utf8) ?? "Unknown error"
        }
        if let error = object["error"] as? [String: Any] {
            if let message = error["message"] as? String { return message }
        }
        if let message = object["message"] as? String { return message }
        return String(data: data.prefix(200), encoding: .utf8) ?? "Unknown error"
    }
}

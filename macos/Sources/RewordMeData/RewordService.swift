import Foundation
import RewordMeDomain
import RewordMeModels

/// Performs the actual HTTP calls. The wire formats live in the injected
/// provider clients; this type only owns transport and error mapping.
public struct RewordService: Sendable {
    private let session: URLSession
    private let registry: ProviderClientRegistry

    public init(
        session: URLSession = .shared,
        registry: ProviderClientRegistry = ProviderClientRegistry()
    ) {
        self.session = session
        self.registry = registry
    }

    public func listModels(
        provider: ProviderKind,
        apiKey: String,
        endpoint: URL? = nil
    ) async throws -> [ModelInfo] {
        let key = try validated(apiKey, for: provider)
        let client = registry.client(for: provider)
        let data = try await perform(client.modelsRequest(apiKey: key, endpoint: endpoint))
        return try client.parseModels(data)
    }

    public func reword(
        provider: ProviderKind,
        apiKey: String,
        model: String,
        systemPrompt: String,
        text: String,
        endpoint: URL? = nil
    ) async throws -> String {
        let key = try validated(apiKey, for: provider)
        let client = registry.client(for: provider)
        let request = try client.rewordRequest(
            apiKey: key,
            model: model,
            systemPrompt: systemPrompt,
            text: text,
            endpoint: endpoint
        )
        let data = try await perform(request)
        return try client.parseReword(data)
    }

    private func validated(_ apiKey: String, for provider: ProviderKind) throws -> String {
        guard provider.requiresAPIKey else {
            // Ollama ignores auth; a placeholder keeps the header well-formed.
            return "ollama"
        }
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
    /// providers' error envelopes.
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

extension RewordService: ModelListing {}

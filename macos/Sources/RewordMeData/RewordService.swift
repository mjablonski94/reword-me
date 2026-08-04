import Foundation
import RewordMeDomain
import RewordMeModels

/// Performs the actual HTTP calls. The wire formats live in the injected
/// provider clients; this type only owns transport and error mapping.
public struct RewordService: Sendable {
    private let session: URLSession
    private let registry: ProviderClientRegistry
    private let accountProviders: AccountProviderService
    private let localModel: LocalModelManager

    public init(
        session: URLSession = .shared,
        registry: ProviderClientRegistry = ProviderClientRegistry(),
        accountProviders: AccountProviderService = AccountProviderService(),
        localModel: LocalModelManager = LocalModelManager()
    ) {
        self.session = session
        self.registry = registry
        self.accountProviders = accountProviders
        self.localModel = localModel
    }

    public func listModels(
        provider: ProviderKind,
        apiKey: String,
        endpoint: URL? = nil
    ) async throws -> [ModelInfo] {
        switch provider {
        case .local:
            return LocalModelCatalog.all.map { ModelInfo(id: $0.id, displayName: $0.displayName) }
        case .codex:
            return [ModelInfo(id: "automatic", displayName: "Automatic (Codex)")]
        case .claudeAccount:
            return [
                ModelInfo(id: "automatic", displayName: "Automatic (Claude)"),
                ModelInfo(id: "sonnet", displayName: "Sonnet"),
                ModelInfo(id: "opus", displayName: "Opus")
            ]
        default:
            break
        }
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
        if provider.isAccountProvider {
            return try await accountProviders.reword(
                provider: provider,
                model: model,
                systemPrompt: systemPrompt,
                text: text
            )
        }
        if provider == .local {
            let manifest = LocalModelCatalog.model(id: model)
            let connection = try await localModel.connection(modelID: manifest.id)
            let client = registry.client(for: .local)
            let request = try client.rewordRequest(
                apiKey: connection.apiKey,
                model: manifest.id,
                systemPrompt: systemPrompt,
                text: text,
                endpoint: connection.endpoint
            )
            return try client.parseReword(try await perform(request))
        }
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
            // Local services ignore this placeholder; managed/account paths
            // are intercepted before reaching validation.
            return provider == .ollama ? "ollama" : "local"
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
            if Self.isUsageLimitError(data) {
                throw RewordError.usageLimitReached
            }
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

    /// HTTP 429 also covers exhausted credits and enforced spend/usage limits.
    /// Those failures require user action and must not be presented as a
    /// temporary rate limit. OpenAI exposes the distinction in `error.code`
    /// while older responses may only carry `error.type` or a message.
    static func isUsageLimitError(_ data: Data) -> Bool {
        guard let object = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return false
        }
        let error = object["error"] as? [String: Any]
        let fields = [
            error?["code"] as? String,
            error?["type"] as? String,
            object["code"] as? String,
            object["type"] as? String
        ]
        let actionRequiredCodes: Set<String> = [
            "credit_balance_exhausted",
            "organization_spend_limit_exceeded",
            "project_spend_limit_exceeded",
            "organization_usage_limit_exceeded",
            "insufficient_quota",
            "billing_hard_limit_reached",
            "billing_not_active"
        ]
        if fields.compactMap({ $0?.lowercased() }).contains(where: actionRequiredCodes.contains) {
            return true
        }

        let message = errorMessage(from: data).lowercased()
        let transientMarkers = [
            "rate_limit_error", "rate_limit_exceeded", "resource_exhausted",
            "per minute", "per second", "requests per", "tokens per",
            "rpm", "tpm", "retry after", "retry shortly", "quota metric", "rate limit"
        ]
        if transientMarkers.contains(where: message.contains) { return false }

        let actionRequiredPhrases = [
            "credit balance", "spend limit", "insufficient quota",
            "billing not active", "billing hard limit", "payment required",
            "credits exhausted", "no credits"
        ]
        if actionRequiredPhrases.contains(where: message.contains) { return true }
        return message.contains("current quota") && (
            message.contains("billing") || message.contains("plan") || message.contains("credit")
        )
    }
}

extension RewordService: ModelListing {}

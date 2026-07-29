import Foundation

/// Anything that can list a provider's models - RewordService in the app,
/// a stub in tests.
public protocol ModelListing: Sendable {
    func listModels(provider: ProviderKind, apiKey: String, endpoint: URL?) async throws -> [ModelInfo]
}

extension RewordService: ModelListing {}

/// Resolves the model to use for a request. An explicit model in the
/// config wins; otherwise the provider's model list is fetched once and
/// the least costly model is cached for the rest of the session.
public actor ModelResolver {
    public static let shared = ModelResolver()

    private var cache: [ProviderKind: String] = [:]

    public init() {}

    public func model(
        for config: RewordConfig,
        apiKey: String,
        service: any ModelListing
    ) async throws -> String {
        if let explicit = config.model, !explicit.isEmpty {
            return explicit
        }
        if let cached = cache[config.provider] {
            return cached
        }
        let models = try await service.listModels(
            provider: config.provider,
            apiKey: apiKey,
            endpoint: config.endpointOverride
        )
        guard let pick = ModelSelection.defaultModel(for: config.provider, from: models) else {
            throw RewordError.noModelAvailable
        }
        cache[config.provider] = pick.id
        return pick.id
    }

    public func invalidate() {
        cache.removeAll()
    }
}

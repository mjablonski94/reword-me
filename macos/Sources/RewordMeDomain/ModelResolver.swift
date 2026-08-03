import Foundation
import RewordMeModels

/// Resolves the model to use for a request. An explicit model in the
/// config wins; otherwise the provider's model list is fetched once and
/// the least costly model is cached for the rest of the session.
public actor ModelResolver {
    private struct CacheKey: Hashable {
        let provider: ProviderKind
        let apiKey: String
        let endpoint: URL?
    }

    private var cache: [CacheKey: String] = [:]

    public init() {}

    public func model(
        for config: RewordConfig,
        apiKey: String,
        service: any ModelListing
    ) async throws -> String {
        if let explicit = config.model, !explicit.isEmpty {
            return explicit
        }
        let key = CacheKey(
            provider: config.provider,
            apiKey: apiKey,
            endpoint: config.endpointOverride
        )
        if let cached = cache[key] {
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
        cache[key] = pick.id
        return pick.id
    }

    public func invalidate() {
        cache.removeAll()
    }
}

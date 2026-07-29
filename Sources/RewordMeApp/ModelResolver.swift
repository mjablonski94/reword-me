import Foundation
import RewordMeCore

/// Resolves the model to use for a request. An explicit model in the
/// config wins; otherwise the provider's model list is fetched once and
/// the least costly model is cached for the rest of the session.
actor ModelResolver {
    static let shared = ModelResolver()

    private var cache: [ProviderKind: String] = [:]

    func model(
        for config: RewordConfig,
        apiKey: String,
        service: RewordService
    ) async throws -> String {
        if let explicit = config.model, !explicit.isEmpty {
            return explicit
        }
        if let cached = cache[config.provider] {
            return cached
        }
        let models = try await service.listModels(provider: config.provider, apiKey: apiKey)
        guard let pick = ModelSelection.defaultModel(for: config.provider, from: models) else {
            throw RewordError.noModelAvailable
        }
        cache[config.provider] = pick.id
        return pick.id
    }

    func invalidate() {
        cache.removeAll()
    }
}

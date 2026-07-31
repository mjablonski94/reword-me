import Foundation
import RewordMeModels

/// Ports the outer layers implement (RewordMeData provides the real ones,
/// tests provide stubs). The domain only ever sees these protocols.

/// Anything that can list a provider's models.
public protocol ModelListing: Sendable {
    func listModels(provider: ProviderKind, apiKey: String, endpoint: URL?) async throws -> [ModelInfo]
}

/// Where API keys live.
public protocol APIKeyStore: Sendable {
    func apiKey(for provider: ProviderKind) -> String?
    func setAPIKey(_ key: String?, for provider: ProviderKind)
}

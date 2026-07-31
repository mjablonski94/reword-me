import Foundation

/// One provider's wire format: how to build requests and read responses.
/// Instances are stateless values; RewordService picks the right one
/// through the injected registry.
public protocol ProviderClient: Sendable {
    var kind: ProviderKind { get }

    func modelsRequest(apiKey: String, endpoint: URL?) -> URLRequest
    func parseModels(_ data: Data) throws -> [ModelInfo]
    func rewordRequest(
        apiKey: String,
        model: String,
        systemPrompt: String,
        text: String,
        endpoint: URL?
    ) throws -> URLRequest
    func parseReword(_ data: Data) throws -> String
}

/// Maps a provider kind to its client. The default set covers every
/// ProviderKind; tests can inject stubs.
public struct ProviderClientRegistry: Sendable {
    private let clients: [ProviderKind: any ProviderClient]

    public init(clients: [any ProviderClient] = ProviderClientRegistry.defaultClients) {
        self.clients = Dictionary(uniqueKeysWithValues: clients.map { ($0.kind, $0) })
    }

    public static var defaultClients: [any ProviderClient] {
        [AnthropicClient(), GeminiClient()]
            + [ProviderKind.openai, .mistral, .xai, .deepseek, .ollama]
                .map { OpenAICompatibleClient(kind: $0) }
    }

    public func client(for kind: ProviderKind) -> any ProviderClient {
        guard let client = clients[kind] else {
            preconditionFailure("No client registered for \(kind.rawValue)")
        }
        return client
    }
}

import Foundation
import RewordMeModels

/// The OpenAI chat-completions dialect, shared by OpenAI, Mistral, xAI,
/// DeepSeek and Ollama - only the base URL and the model filter differ.
public struct OpenAICompatibleClient: ProviderClient {
    public let kind: ProviderKind

    public init(kind: ProviderKind) {
        precondition(
            kind.openAICompatibleBaseURL != nil,
            "\(kind.rawValue) does not speak the OpenAI dialect"
        )
        self.kind = kind
    }

    private func baseURL(_ endpoint: URL?) -> URL {
        endpoint ?? kind.openAICompatibleBaseURL!
    }

    public func modelsRequest(apiKey: String, endpoint: URL?) -> URLRequest {
        var request = URLRequest(url: baseURL(endpoint).appendingPathComponent("models"))
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        return request
    }

    public func parseModels(_ data: Data) throws -> [ModelInfo] {
        struct Response: Decodable {
            struct Model: Decodable {
                let id: String
            }

            let data: [Model]
        }
        guard let response = try? JSONDecoder().decode(Response.self, from: data) else {
            throw RewordError.invalidResponse
        }
        // Server order is preserved - Ollama lists most recently used
        // models first and the automatic pick relies on that; UIs sort
        // for display themselves.
        return response.data
            .map(\.id)
            .filter(kind.includesModel)
            .map { ModelInfo(id: $0) }
    }

    public func rewordRequest(
        apiKey: String,
        model: String,
        systemPrompt: String,
        text: String,
        endpoint: URL?
    ) throws -> URLRequest {
        struct Body: Encodable {
            struct Message: Encodable {
                let role: String
                let content: String
            }

            let model: String
            let messages: [Message]
        }
        let body = Body(
            model: model,
            messages: [
                Body.Message(role: "system", content: systemPrompt),
                Body.Message(role: "user", content: text)
            ]
        )
        var request = URLRequest(
            url: baseURL(endpoint).appendingPathComponent("chat/completions")
        )
        request.httpMethod = "POST"
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(body)
        return request
    }

    public func parseReword(_ data: Data) throws -> String {
        struct Response: Decodable {
            struct Choice: Decodable {
                struct Message: Decodable {
                    let content: String?
                }

                let message: Message
            }

            let choices: [Choice]
        }
        guard let response = try? JSONDecoder().decode(Response.self, from: data) else {
            throw RewordError.invalidResponse
        }
        let text = (response.choices.first?.message.content ?? "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { throw RewordError.emptyResponse }
        return text
    }
}

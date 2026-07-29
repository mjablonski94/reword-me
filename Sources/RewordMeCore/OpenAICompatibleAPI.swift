import Foundation

/// Raw HTTP bindings for the OpenAI chat-completions dialect, shared by
/// OpenAI, Mistral, xAI and DeepSeek - only the base URL and the model
/// filter differ per provider.
enum OpenAICompatibleAPI {
    static func modelsRequest(baseURL: URL, apiKey: String) -> URLRequest {
        var request = URLRequest(url: baseURL.appendingPathComponent("models"))
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        return request
    }

    static func parseModels(
        _ data: Data,
        includeModel: (String) -> Bool
    ) throws -> [ModelInfo] {
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
            .filter(includeModel)
            .map { ModelInfo(id: $0) }
    }

    static func rewordRequest(
        baseURL: URL,
        apiKey: String,
        model: String,
        systemPrompt: String,
        text: String
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
        var request = URLRequest(url: baseURL.appendingPathComponent("chat/completions"))
        request.httpMethod = "POST"
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(body)
        return request
    }

    static func parseReword(_ data: Data) throws -> String {
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

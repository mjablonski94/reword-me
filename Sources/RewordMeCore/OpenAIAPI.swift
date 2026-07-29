import Foundation

/// Raw HTTP bindings for the OpenAI Chat Completions API.
enum OpenAIAPI {
    static let baseURL = URL(string: "https://api.openai.com/v1")!

    static func modelsRequest(apiKey: String) -> URLRequest {
        var request = URLRequest(url: baseURL.appendingPathComponent("models"))
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        return request
    }

    static func parseModels(_ data: Data) throws -> [ModelInfo] {
        struct Response: Decodable {
            struct Model: Decodable {
                let id: String
            }

            let data: [Model]
        }
        guard let response = try? JSONDecoder().decode(Response.self, from: data) else {
            throw RewordError.invalidResponse
        }
        return response.data
            .map(\.id)
            .filter(isChatModel)
            .sorted()
            .map { ModelInfo(id: $0) }
    }

    /// The models endpoint lists everything (embeddings, audio, images).
    /// Keep only chat-capable text models.
    static func isChatModel(_ id: String) -> Bool {
        let lower = id.lowercased()
        let excluded = [
            "embedding", "whisper", "tts", "audio", "realtime", "image",
            "dall-e", "moderation", "transcribe", "computer-use", "search", "instruct"
        ]
        if excluded.contains(where: lower.contains) { return false }
        if lower.hasPrefix("gpt-") { return true }
        return lower.range(of: "^o[0-9]", options: .regularExpression) != nil
    }

    static func rewordRequest(
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

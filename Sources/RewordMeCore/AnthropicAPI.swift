import Foundation

/// Raw HTTP bindings for the Anthropic Messages API.
/// Docs: POST /v1/messages, GET /v1/models (anthropic-version: 2023-06-01).
enum AnthropicAPI {
    static let baseURL = URL(string: "https://api.anthropic.com/v1")!
    static let apiVersion = "2023-06-01"

    static func modelsRequest(apiKey: String) -> URLRequest {
        var components = URLComponents(
            url: baseURL.appendingPathComponent("models"),
            resolvingAgainstBaseURL: false
        )!
        components.queryItems = [URLQueryItem(name: "limit", value: "100")]
        var request = URLRequest(url: components.url!)
        request.setValue(apiKey, forHTTPHeaderField: "x-api-key")
        request.setValue(apiVersion, forHTTPHeaderField: "anthropic-version")
        return request
    }

    static func parseModels(_ data: Data) throws -> [ModelInfo] {
        struct Response: Decodable {
            struct Model: Decodable {
                let id: String
                let displayName: String?

                enum CodingKeys: String, CodingKey {
                    case id
                    case displayName = "display_name"
                }
            }

            let data: [Model]
        }
        guard let response = try? JSONDecoder().decode(Response.self, from: data) else {
            throw RewordError.invalidResponse
        }
        return response.data.map { ModelInfo(id: $0.id, displayName: $0.displayName) }
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
            let maxTokens: Int
            let system: String
            let messages: [Message]

            enum CodingKeys: String, CodingKey {
                case model
                case maxTokens = "max_tokens"
                case system
                case messages
            }
        }
        let body = Body(
            model: model,
            maxTokens: outputTokenBudget(for: text),
            system: systemPrompt,
            messages: [Body.Message(role: "user", content: text)]
        )
        var request = URLRequest(url: baseURL.appendingPathComponent("messages"))
        request.httpMethod = "POST"
        request.setValue(apiKey, forHTTPHeaderField: "x-api-key")
        request.setValue(apiVersion, forHTTPHeaderField: "anthropic-version")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(body)
        return request
    }

    static func parseReword(_ data: Data) throws -> String {
        struct Response: Decodable {
            struct Block: Decodable {
                let type: String
                let text: String?
            }

            struct StopDetails: Decodable {
                let explanation: String?
            }

            let content: [Block]
            let stopReason: String?
            let stopDetails: StopDetails?

            enum CodingKeys: String, CodingKey {
                case content
                case stopReason = "stop_reason"
                case stopDetails = "stop_details"
            }
        }
        guard let response = try? JSONDecoder().decode(Response.self, from: data) else {
            throw RewordError.invalidResponse
        }
        if response.stopReason == "refusal" {
            throw RewordError.refused(response.stopDetails?.explanation)
        }
        let text = response.content
            .filter { $0.type == "text" }
            .compactMap(\.text)
            .joined()
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { throw RewordError.emptyResponse }
        return text
    }

    /// Generous cap for a rewrite: roughly the input length again, floored and capped.
    static func outputTokenBudget(for text: String) -> Int {
        min(8192, max(1024, text.count))
    }
}

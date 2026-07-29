import Foundation

/// Raw HTTP bindings for the Google Gemini API (generativelanguage.googleapis.com).
/// The key goes in the x-goog-api-key header, never in the URL.
enum GeminiAPI {
    static let baseURL = URL(string: "https://generativelanguage.googleapis.com/v1beta")!

    static func modelsRequest(apiKey: String) -> URLRequest {
        var components = URLComponents(
            url: baseURL.appendingPathComponent("models"),
            resolvingAgainstBaseURL: false
        )!
        components.queryItems = [URLQueryItem(name: "pageSize", value: "200")]
        var request = URLRequest(url: components.url!)
        request.setValue(apiKey, forHTTPHeaderField: "x-goog-api-key")
        return request
    }

    static func parseModels(_ data: Data) throws -> [ModelInfo] {
        struct Response: Decodable {
            struct Model: Decodable {
                let name: String
                let displayName: String?
                let supportedGenerationMethods: [String]?
            }

            let models: [Model]?
        }
        guard let response = try? JSONDecoder().decode(Response.self, from: data) else {
            throw RewordError.invalidResponse
        }
        return (response.models ?? [])
            .filter { $0.supportedGenerationMethods?.contains("generateContent") == true }
            .map { model in
                let id = model.name.hasPrefix("models/")
                    ? String(model.name.dropFirst("models/".count))
                    : model.name
                return ModelInfo(id: id, displayName: model.displayName)
            }
    }

    static func rewordRequest(
        apiKey: String,
        model: String,
        systemPrompt: String,
        text: String
    ) throws -> URLRequest {
        struct Body: Encodable {
            struct Part: Encodable {
                let text: String
            }

            struct Content: Encodable {
                let role: String?
                let parts: [Part]
            }

            let systemInstruction: Content
            let contents: [Content]

            enum CodingKeys: String, CodingKey {
                case systemInstruction = "system_instruction"
                case contents
            }
        }
        let body = Body(
            systemInstruction: Body.Content(role: nil, parts: [Body.Part(text: systemPrompt)]),
            contents: [Body.Content(role: "user", parts: [Body.Part(text: text)])]
        )
        let url = baseURL.appendingPathComponent("models/\(model):generateContent")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(apiKey, forHTTPHeaderField: "x-goog-api-key")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(body)
        return request
    }

    static func parseReword(_ data: Data) throws -> String {
        struct Response: Decodable {
            struct Candidate: Decodable {
                struct Content: Decodable {
                    struct Part: Decodable {
                        let text: String?
                    }

                    let parts: [Part]?
                }

                let content: Content?
            }

            let candidates: [Candidate]?
        }
        guard let response = try? JSONDecoder().decode(Response.self, from: data) else {
            throw RewordError.invalidResponse
        }
        let text = (response.candidates?.first?.content?.parts ?? [])
            .compactMap(\.text)
            .joined()
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { throw RewordError.emptyResponse }
        return text
    }
}

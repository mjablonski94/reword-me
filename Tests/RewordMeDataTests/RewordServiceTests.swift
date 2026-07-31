import XCTest
import RewordMeModels
@testable import RewordMeData

/// Intercepts every request the service makes, so HTTP behavior is tested
/// without the network.
final class MockURLProtocol: URLProtocol {
    nonisolated(unsafe) static var handler: ((URLRequest) -> (Int, [String: String], Data))?
    nonisolated(unsafe) static var requests: [URLRequest] = []

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.requests.append(request)
        guard let handler = Self.handler else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }
        let (status, headers, data) = handler(request)
        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: status,
            httpVersion: "HTTP/1.1",
            headerFields: headers
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

final class RewordServiceTests: XCTestCase {
    private var service: RewordService!

    override func setUp() {
        super.setUp()
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        service = RewordService(session: URLSession(configuration: configuration))
        MockURLProtocol.requests = []
        MockURLProtocol.handler = nil
    }

    private func respond(status: Int, headers: [String: String] = [:], body: String = "{}") {
        MockURLProtocol.handler = { _ in (status, headers, Data(body.utf8)) }
    }

    func testUnauthorizedMapsToInvalidAPIKey() async {
        respond(status: 401)
        await assertThrows(.invalidAPIKey) {
            _ = try await self.service.listModels(provider: .openai, apiKey: "bad")
        }
    }

    func testRateLimitCarriesRetryAfterHeader() async {
        respond(status: 429, headers: ["retry-after": "30"])
        await assertThrows(.rateLimited(retryAfterSeconds: 30)) {
            _ = try await self.service.listModels(provider: .anthropic, apiKey: "k")
        }
    }

    func testServerErrorSurfacesProviderMessage() async {
        respond(
            status: 529,
            body: #"{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"#
        )
        await assertThrows(.apiError(status: 529, message: "Overloaded")) {
            _ = try await self.service.listModels(provider: .anthropic, apiKey: "k")
        }
    }

    func testMissingKeyThrowsBeforeAnyRequestIsMade() async {
        respond(status: 200)
        await assertThrows(.missingAPIKey) {
            _ = try await self.service.reword(
                provider: .openai, apiKey: "  ", model: "m", systemPrompt: "s", text: "t"
            )
        }
        XCTAssertTrue(MockURLProtocol.requests.isEmpty)
    }

    func testSuccessfulAnthropicModelListRoundTrip() async throws {
        respond(
            status: 200,
            body: #"{"data":[{"id":"claude-haiku-4-5","display_name":"Claude Haiku 4.5"}]}"#
        )
        let models = try await service.listModels(provider: .anthropic, apiKey: "k")
        XCTAssertEqual(models.map(\.id), ["claude-haiku-4-5"])
        let request = MockURLProtocol.requests.first
        XCTAssertEqual(request?.url?.host, "api.anthropic.com")
        XCTAssertEqual(request?.value(forHTTPHeaderField: "x-api-key"), "k")
    }

    func testOllamaNeedsNoKeyAndHonorsEndpointOverride() async throws {
        respond(status: 200, body: #"{"data":[{"id":"llama3.2:latest"}]}"#)
        let endpoint = OllamaEndpoint.baseURL(host: "192.168.1.20:11434")
        let models = try await service.listModels(
            provider: .ollama, apiKey: "", endpoint: endpoint
        )
        XCTAssertEqual(models.map(\.id), ["llama3.2:latest"])
        let request = MockURLProtocol.requests.first
        XCTAssertEqual(request?.url?.host, "192.168.1.20")
        XCTAssertEqual(request?.value(forHTTPHeaderField: "Authorization"), "Bearer ollama")
    }

    private func assertThrows(
        _ expected: RewordError,
        file: StaticString = #filePath,
        line: UInt = #line,
        _ body: @escaping () async throws -> Void
    ) async {
        do {
            try await body()
            XCTFail("Expected \(expected) to be thrown", file: file, line: line)
        } catch {
            XCTAssertEqual(error as? RewordError, expected, file: file, line: line)
        }
    }
}

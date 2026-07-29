import XCTest
@testable import RewordMeCore

/// Counts calls and returns a scripted model list.
private actor StubModelListing: ModelListing {
    private(set) var calls = 0
    private let models: [ModelInfo]

    init(models: [ModelInfo]) {
        self.models = models
    }

    func listModels(provider: ProviderKind, apiKey: String, endpoint: URL?) async throws -> [ModelInfo] {
        calls += 1
        return models
    }
}

final class ModelResolverTests: XCTestCase {
    func testExplicitModelWinsWithoutTouchingTheService() async throws {
        let stub = StubModelListing(models: [])
        let resolver = ModelResolver()
        var config = RewordConfig.default
        config.model = "claude-opus-5"

        let model = try await resolver.model(for: config, apiKey: "k", service: stub)

        XCTAssertEqual(model, "claude-opus-5")
        let calls = await stub.calls
        XCTAssertEqual(calls, 0)
    }

    func testAutomaticPickIsCheapestAndCached() async throws {
        let stub = StubModelListing(models: [
            ModelInfo(id: "claude-opus-5"),
            ModelInfo(id: "claude-haiku-4-5")
        ])
        let resolver = ModelResolver()
        let config = RewordConfig.default

        let first = try await resolver.model(for: config, apiKey: "k", service: stub)
        let second = try await resolver.model(for: config, apiKey: "k", service: stub)

        XCTAssertEqual(first, "claude-haiku-4-5")
        XCTAssertEqual(second, "claude-haiku-4-5")
        let calls = await stub.calls
        XCTAssertEqual(calls, 1, "second resolution must come from the cache")
    }

    func testInvalidateForcesARefetch() async throws {
        let stub = StubModelListing(models: [ModelInfo(id: "claude-haiku-4-5")])
        let resolver = ModelResolver()
        let config = RewordConfig.default

        _ = try await resolver.model(for: config, apiKey: "k", service: stub)
        await resolver.invalidate()
        _ = try await resolver.model(for: config, apiKey: "k", service: stub)

        let calls = await stub.calls
        XCTAssertEqual(calls, 2)
    }

    func testEmptyModelListThrows() async {
        let stub = StubModelListing(models: [])
        let resolver = ModelResolver()

        do {
            _ = try await resolver.model(for: .default, apiKey: "k", service: stub)
            XCTFail("Expected noModelAvailable")
        } catch {
            XCTAssertEqual(error as? RewordError, .noModelAvailable)
        }
    }
}

import XCTest
import RewordMeModels
@testable import RewordMeData

final class KeychainStoreTests: XCTestCase {
    /// A dedicated service name keeps test entries away from real keys.
    private let store = KeychainAPIKeyStore(service: "com.mjablonski.rewordme.tests")

    override func tearDown() {
        for provider in ProviderKind.allCases {
            store.setAPIKey(nil, for: provider)
        }
        super.tearDown()
    }

    func testMissingKeyReadsAsNil() {
        XCTAssertNil(store.apiKey(for: .anthropic))
    }

    func testRoundTripAndOverwrite() {
        XCTAssertTrue(store.setAPIKey("sk-first", for: .anthropic))
        XCTAssertEqual(store.apiKey(for: .anthropic), "sk-first")

        XCTAssertTrue(store.setAPIKey("sk-second", for: .anthropic))
        XCTAssertEqual(store.apiKey(for: .anthropic), "sk-second")
    }

    func testKeysAreIsolatedPerProvider() {
        store.setAPIKey("sk-claude", for: .anthropic)
        store.setAPIKey("sk-openai", for: .openai)

        XCTAssertEqual(store.apiKey(for: .anthropic), "sk-claude")
        XCTAssertEqual(store.apiKey(for: .openai), "sk-openai")
        XCTAssertNil(store.apiKey(for: .gemini))
    }

    func testNilOrBlankDeletesTheEntry() {
        store.setAPIKey("sk-key", for: .mistral)
        XCTAssertTrue(store.setAPIKey(nil, for: .mistral))
        XCTAssertNil(store.apiKey(for: .mistral))

        store.setAPIKey("sk-key", for: .mistral)
        XCTAssertTrue(store.setAPIKey("   ", for: .mistral))
        XCTAssertNil(store.apiKey(for: .mistral))
    }

    func testStoresAreIsolatedPerService() {
        let other = KeychainAPIKeyStore(service: "com.mjablonski.rewordme.tests.other")
        defer { other.setAPIKey(nil, for: .anthropic) }

        store.setAPIKey("sk-main", for: .anthropic)
        XCTAssertNil(other.apiKey(for: .anthropic))
    }
}

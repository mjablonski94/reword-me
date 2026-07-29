import XCTest
@testable import RewordMeCore

final class KeychainStoreTests: XCTestCase {
    /// A dedicated service name keeps test entries away from real keys.
    private let service = "com.mjablonski.rewordme.tests"

    override func tearDown() {
        for provider in ProviderKind.allCases {
            KeychainStore.setAPIKey(nil, for: provider, service: service)
        }
        super.tearDown()
    }

    func testMissingKeyReadsAsNil() {
        XCTAssertNil(KeychainStore.apiKey(for: .anthropic, service: service))
    }

    func testRoundTripAndOverwrite() {
        KeychainStore.setAPIKey("sk-first", for: .anthropic, service: service)
        XCTAssertEqual(KeychainStore.apiKey(for: .anthropic, service: service), "sk-first")

        KeychainStore.setAPIKey("sk-second", for: .anthropic, service: service)
        XCTAssertEqual(KeychainStore.apiKey(for: .anthropic, service: service), "sk-second")
    }

    func testKeysAreIsolatedPerProvider() {
        KeychainStore.setAPIKey("sk-claude", for: .anthropic, service: service)
        KeychainStore.setAPIKey("sk-openai", for: .openai, service: service)

        XCTAssertEqual(KeychainStore.apiKey(for: .anthropic, service: service), "sk-claude")
        XCTAssertEqual(KeychainStore.apiKey(for: .openai, service: service), "sk-openai")
        XCTAssertNil(KeychainStore.apiKey(for: .gemini, service: service))
    }

    func testNilOrBlankDeletesTheEntry() {
        KeychainStore.setAPIKey("sk-key", for: .mistral, service: service)
        KeychainStore.setAPIKey(nil, for: .mistral, service: service)
        XCTAssertNil(KeychainStore.apiKey(for: .mistral, service: service))

        KeychainStore.setAPIKey("sk-key", for: .mistral, service: service)
        KeychainStore.setAPIKey("   ", for: .mistral, service: service)
        XCTAssertNil(KeychainStore.apiKey(for: .mistral, service: service))
    }
}

import XCTest
import RewordMeModels
@testable import RewordMeData

final class ConfigTests: XCTestCase {
    func testRoundTrip() throws {
        let config = RewordConfig(
            provider: .gemini,
            model: "gemini-2.5-flash-lite",
            rules: [
                RewriteRule(kind: .dontRule, text: "No emoji", isEnabled: false),
                RewriteRule(kind: .doRule, text: "Be concise")
            ],
            basePrompt: "Keep my voice.",
            ollamaHost: "http://192.168.1.20:11434",
            hotkey: HotkeyConfig(
                keyCode: 17,
                carbonModifiers: HotkeyConfig.carbonCommand | HotkeyConfig.carbonShift,
                character: "t",
                display: "⇧⌘T"
            )
        )
        let data = try JSONEncoder().encode(config)
        let decoded = try JSONDecoder().decode(RewordConfig.self, from: data)
        XCTAssertEqual(decoded, config)
    }

    func testDecodingEmptyObjectFallsBackToDefaults() throws {
        let decoded = try JSONDecoder().decode(RewordConfig.self, from: Data("{}".utf8))
        XCTAssertEqual(decoded, .default)
        XCTAssertEqual(decoded.provider, .anthropic)
        XCTAssertNil(decoded.model)
        XCTAssertEqual(decoded.hotkey, .default)
        XCTAssertEqual(decoded.hotkey.display, "⌥⌘R")
        XCTAssertEqual(decoded.hotkey.keyCode, 15, "R key")
        XCTAssertEqual(
            decoded.hotkey.carbonModifiers,
            HotkeyConfig.carbonCommand | HotkeyConfig.carbonOption
        )
    }

    func testEveryProviderHasAnAPIKeyConsoleLink() {
        for provider in ProviderKind.allCases {
            XCTAssertEqual(provider.apiKeyConsoleURL.scheme, "https")
            XCTAssertFalse(provider.apiKeyConsoleName.isEmpty)
        }
    }

    func testStoreSaveAndLoad() throws {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("rewordme-tests-\(UUID().uuidString)")
        let store = ConfigStore(url: dir.appendingPathComponent("config.json"))
        defer { try? FileManager.default.removeItem(at: dir) }

        XCTAssertEqual(store.load(), .default, "missing file loads defaults")

        var config = RewordConfig.default
        config.provider = .openai
        config.basePrompt = "base"
        try store.save(config)
        XCTAssertEqual(store.load(), config)
    }
}

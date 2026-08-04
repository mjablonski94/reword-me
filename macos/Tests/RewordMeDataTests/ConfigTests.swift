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
        XCTAssertEqual(decoded.provider, .gemini)
        XCTAssertNil(decoded.model)
        XCTAssertEqual(decoded.hotkey, .default)
        XCTAssertEqual(decoded.hotkey.display, "⌥⌘R")
        XCTAssertEqual(decoded.hotkey.keyCode, 15, "R key")
        XCTAssertEqual(
            decoded.hotkey.carbonModifiers,
            HotkeyConfig.carbonCommand | HotkeyConfig.carbonOption
        )
    }

    func testLegacyGlobalModelMigratesToItsSavedProvider() throws {
        let legacy = Data(#"{"provider":"gemini","model":"gemini-2.5-flash"}"#.utf8)
        var decoded = try JSONDecoder().decode(RewordConfig.self, from: legacy)

        XCTAssertEqual(decoded.model, "gemini-2.5-flash")
        decoded.provider = .openai
        XCTAssertNil(decoded.model)
        decoded.model = "gpt-5-mini"
        decoded.provider = .gemini
        XCTAssertEqual(decoded.model, "gemini-2.5-flash")
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

    func testInvalidConfigSurvivesTheFirstRunDefaultSave() throws {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("rewordme-invalid-config-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }
        let invalid = Data("{ recoverable but invalid JSON".utf8)
        let store = ConfigStore(url: dir.appendingPathComponent("config.json"))
        try invalid.write(to: store.url)

        var defaults = store.load()
        defaults.launchAtLogin = false
        try store.save(defaults)

        XCTAssertEqual(try Data(contentsOf: store.invalidBackupURL), invalid)
        XCTAssertEqual(store.load().launchAtLogin, false)
    }
}

import XCTest

/// Guards translation completeness: every language must carry exactly the
/// same key set as English, with the same format specifiers.
final class LocalizationTests: XCTestCase {
    private static let languages = ["en", "de", "es", "fr", "it", "ja", "ko", "pl", "pt", "uk", "zh-Hans"]

    private var localizationsURL: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // RewordMeCoreTests
            .deletingLastPathComponent() // Tests
            .deletingLastPathComponent() // repo root
            .appendingPathComponent("Localizations")
    }

    private func keysAndValues(for language: String) throws -> [String: String] {
        let url = localizationsURL
            .appendingPathComponent("\(language).lproj/Localizable.strings")
        let content = try String(contentsOf: url, encoding: .utf8)
        var entries: [String: String] = [:]
        let pattern = #/"(?<key>[^"]+)"\s*=\s*"(?<value>(?:[^"\\]|\\.)*)";/#
        for match in content.matches(of: pattern) {
            entries[String(match.key)] = String(match.value)
        }
        return entries
    }

    func testEveryLanguageHasExactlyTheEnglishKeySet() throws {
        let english = try keysAndValues(for: "en")
        XCTAssertFalse(english.isEmpty)

        for language in Self.languages.dropFirst() {
            let localized = try keysAndValues(for: language)
            let missing = Set(english.keys).subtracting(localized.keys).sorted()
            let extra = Set(localized.keys).subtracting(english.keys).sorted()
            XCTAssertEqual(missing, [], "\(language) is missing keys")
            XCTAssertEqual(extra, [], "\(language) has unknown keys")
        }
    }

    func testFormatSpecifiersMatchEnglish() throws {
        let english = try keysAndValues(for: "en")

        func specifiers(_ value: String) -> [String] {
            value.matches(of: #/%[@d]/#).map { String($0.output) }.sorted()
        }

        for language in Self.languages.dropFirst() {
            let localized = try keysAndValues(for: language)
            for (key, englishValue) in english {
                guard let value = localized[key] else { continue }
                XCTAssertEqual(
                    specifiers(value),
                    specifiers(englishValue),
                    "\(language)/\(key) has mismatched format specifiers"
                )
            }
        }
    }

    func testNoLanguageHasEmptyValues() throws {
        for language in Self.languages {
            let localized = try keysAndValues(for: language)
            for (key, value) in localized {
                XCTAssertFalse(value.isEmpty, "\(language)/\(key) is empty")
            }
        }
    }
}

import Foundation
import RewordMeModels

/// Loads and saves the config as JSON in Application Support.
public struct ConfigStore: Sendable {
    public let url: URL

    public init(url: URL = ConfigStore.defaultURL) {
        self.url = url
    }

    public static let defaultURL: URL = {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent("RewordMe/config.json")
    }()

    public var invalidBackupURL: URL {
        url.deletingLastPathComponent().appendingPathComponent("config.invalid.json")
    }

    public func load() -> RewordConfig {
        let fileManager = FileManager.default
        guard fileManager.fileExists(atPath: url.path) else {
            return .default
        }
        do {
            let data = try Data(contentsOf: url)
            return try JSONDecoder().decode(RewordConfig.self, from: data)
        } catch {
            // AppDependencies may immediately persist first-run defaults. Keep
            // the undecodable bytes recoverable before that atomic save can
            // replace config.json.
            try? fileManager.removeItem(at: invalidBackupURL)
            try? fileManager.copyItem(at: url, to: invalidBackupURL)
            return .default
        }
    }

    public func save(_ config: RewordConfig) throws {
        try FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        try encoder.encode(config).write(to: url, options: .atomic)
    }
}

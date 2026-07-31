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

    public func load() -> RewordConfig {
        guard let data = try? Data(contentsOf: url),
              let config = try? JSONDecoder().decode(RewordConfig.self, from: data) else {
            return .default
        }
        return config
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

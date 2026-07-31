import Foundation
import RewordMeDomain
import RewordMeModels
import Security

/// The real store: one login-Keychain entry per provider. Nothing
/// sensitive ever touches the JSON config or UserDefaults.
public struct KeychainAPIKeyStore: APIKeyStore {
    public let service: String

    public init(service: String = "com.mjablonski.rewordme") {
        self.service = service
    }

    public func apiKey(for provider: ProviderKind) -> String? {
        var query = baseQuery(for: provider)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else {
            return nil
        }
        return String(data: data, encoding: .utf8)
    }

    public func setAPIKey(_ key: String?, for provider: ProviderKind) {
        SecItemDelete(baseQuery(for: provider) as CFDictionary)
        guard let key = key?.trimmingCharacters(in: .whitespacesAndNewlines), !key.isEmpty else {
            return
        }
        var attributes = baseQuery(for: provider)
        attributes[kSecValueData as String] = Data(key.utf8)
        SecItemAdd(attributes as CFDictionary, nil)
    }

    private func baseQuery(for provider: ProviderKind) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: provider.rawValue
        ]
    }
}

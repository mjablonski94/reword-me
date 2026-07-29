import Foundation
import Security

/// API keys live in the login Keychain, one entry per provider.
/// Nothing sensitive ever touches the JSON config or UserDefaults.
public enum KeychainStore {
    public static let defaultService = "com.mjablonski.rewordme"

    public static func apiKey(
        for provider: ProviderKind,
        service: String = defaultService
    ) -> String? {
        var query = baseQuery(for: provider, service: service)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else {
            return nil
        }
        return String(data: data, encoding: .utf8)
    }

    public static func setAPIKey(
        _ key: String?,
        for provider: ProviderKind,
        service: String = defaultService
    ) {
        SecItemDelete(baseQuery(for: provider, service: service) as CFDictionary)
        guard let key = key?.trimmingCharacters(in: .whitespacesAndNewlines), !key.isEmpty else {
            return
        }
        var attributes = baseQuery(for: provider, service: service)
        attributes[kSecValueData as String] = Data(key.utf8)
        SecItemAdd(attributes as CFDictionary, nil)
    }

    private static func baseQuery(for provider: ProviderKind, service: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: provider.rawValue
        ]
    }
}

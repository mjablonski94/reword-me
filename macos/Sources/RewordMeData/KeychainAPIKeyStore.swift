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

    @discardableResult
    public func setAPIKey(_ key: String?, for provider: ProviderKind) -> Bool {
        let query = baseQuery(for: provider)
        guard let key = key?.trimmingCharacters(in: .whitespacesAndNewlines), !key.isEmpty else {
            let status = SecItemDelete(query as CFDictionary)
            return status == errSecSuccess || status == errSecItemNotFound
        }

        let value = Data(key.utf8)
        let updateStatus = SecItemUpdate(
            query as CFDictionary,
            [kSecValueData as String: value] as CFDictionary
        )
        if updateStatus == errSecSuccess {
            return true
        }
        guard updateStatus == errSecItemNotFound else {
            // Crucially, the existing item is still intact when an update is
            // denied or fails. The previous delete-then-add flow lost it.
            return false
        }

        var attributes = query
        attributes[kSecValueData as String] = value
        return SecItemAdd(attributes as CFDictionary, nil) == errSecSuccess
    }

    private func baseQuery(for provider: ProviderKind) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: provider.rawValue
        ]
    }
}

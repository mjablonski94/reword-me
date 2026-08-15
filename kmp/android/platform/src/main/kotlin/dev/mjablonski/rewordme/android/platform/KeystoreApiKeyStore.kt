package dev.mjablonski.rewordme.android.platform

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.mjablonski.rewordme.domain.ApiKeyStore
import dev.mjablonski.rewordme.models.ProviderKind
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Android analogue of the desktop's Credential Manager store: API keys
 * are encrypted with an AES-256-GCM key that lives in the hardware-backed
 * Android Keystore and never leaves it. Only the ciphertext (IV prepended,
 * base64) touches disk, in app-private preferences excluded from backups
 * by the app's backup rules - a copied preferences file is useless without
 * this device's Keystore entry.
 */
class KeystoreApiKeyStore(context: Context) : ApiKeyStore {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun apiKey(provider: ProviderKind): String? = runCatching {
        val stored = preferences.getString(provider.id, null) ?: return null
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        if (bytes.size <= IV_LENGTH) return null
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(TAG_BITS, bytes, 0, IV_LENGTH)
            )
        }
        String(cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH), Charsets.UTF_8)
    }.getOrNull()

    override fun setApiKey(provider: ProviderKind, key: String?): Boolean = runCatching {
        val trimmed = key?.trim()
        if (trimmed.isNullOrEmpty()) {
            preferences.edit().remove(provider.id).commit()
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, secretKey())
            }
            val sealed = cipher.iv + cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))
            preferences.edit()
                .putString(provider.id, Base64.encodeToString(sealed, Base64.NO_WRAP))
                .commit() // Synchronous on purpose: the result reports persistence.
        }
    }.getOrDefault(false)

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "rewordme.apiKeys"
        const val PREFERENCES_NAME = "rewordme.keys"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        /** GCM's default nonce length; Keystore always generates this one. */
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}

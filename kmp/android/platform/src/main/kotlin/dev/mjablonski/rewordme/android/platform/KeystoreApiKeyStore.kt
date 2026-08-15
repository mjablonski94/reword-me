package dev.mjablonski.rewordme.android.platform

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.mjablonski.rewordme.domain.ApiKeyStore
import dev.mjablonski.rewordme.models.ProviderKind
import java.security.KeyStore
import javax.crypto.AEADBadTagException
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
 *
 * Failure policy: keys are re-enterable secrets, so availability wins over
 * clinging to dead state. A blob the current Keystore entry cannot open is
 * removed (re-saving works again); an unusable Keystore entry is deleted and
 * regenerated. Transient errors change nothing and read as "no key" for the
 * one call.
 */
class KeystoreApiKeyStore(context: Context) : ApiKeyStore {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun apiKey(provider: ProviderKind): String? = synchronized(lock) {
        val stored = preferences.getString(provider.id, null) ?: return null
        val bytes = runCatching { Base64.decode(stored, Base64.NO_WRAP) }.getOrNull()
        if (bytes == null || bytes.size <= IV_LENGTH) {
            discardDeadBlob(provider)
            return null
        }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    secretKey(),
                    GCMParameterSpec(TAG_BITS, bytes, 0, IV_LENGTH)
                )
            }
            String(cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH), Charsets.UTF_8)
        } catch (_: AEADBadTagException) {
            // The blob no longer matches the Keystore entry (key was rotated
            // or restored from another device). It can never decrypt again;
            // dropping it lets the user simply re-enter the key.
            discardDeadBlob(provider)
            null
        } catch (_: Exception) {
            // Possibly transient (keystore daemon hiccup): keep the blob.
            null
        }
    }

    override fun setApiKey(provider: ProviderKind, key: String?): Boolean = synchronized(lock) {
        runCatching {
            val trimmed = key?.trim()
            if (trimmed.isNullOrEmpty()) {
                preferences.edit().remove(provider.id).commit()
            } else {
                val sealed = try {
                    seal(trimmed)
                } catch (_: Exception) {
                    // The entry exists but cannot encrypt: it is dead weight.
                    // Regenerate once and retry; old blobs are unreadable
                    // either way, and this restores a working vault.
                    regenerateKey()
                    seal(trimmed)
                }
                preferences.edit()
                    .putString(provider.id, Base64.encodeToString(sealed, Base64.NO_WRAP))
                    .commit() // Synchronous on purpose: the result reports persistence.
            }
        }.getOrDefault(false)
    }

    private fun seal(value: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, secretKey())
        }
        return cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    }

    private fun discardDeadBlob(provider: ProviderKind) {
        runCatching { preferences.edit().remove(provider.id).commit() }
    }

    /**
     * Get-or-create under [lock]: generateKey on an existing alias silently
     * replaces it, so an unsynchronized double-create would orphan every
     * blob written between the two generations.
     */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = runCatching { keyStore.getKey(KEY_ALIAS, null) as? SecretKey }
            .getOrElse {
                // UnrecoverableKeyException and friends: the entry is
                // permanently unusable, so replace it.
                runCatching { keyStore.deleteEntry(KEY_ALIAS) }
                null
            }
        return existing ?: regenerateKey()
    }

    private fun regenerateKey(): SecretKey {
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
        /** One lock per process: all instances share one alias and one file. */
        val lock = Any()
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "rewordme.apiKeys"
        const val PREFERENCES_NAME = "rewordme.keys"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        /** GCM's default nonce length; Keystore always generates this one. */
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}

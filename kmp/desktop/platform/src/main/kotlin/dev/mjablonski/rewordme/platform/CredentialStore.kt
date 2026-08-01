package dev.mjablonski.rewordme.platform

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import dev.mjablonski.rewordme.domain.ApiKeyStore
import dev.mjablonski.rewordme.models.ProviderKind

/**
 * API keys in the Windows Credential Manager, the counterpart to the macOS
 * app's Keychain storage: encrypted by the OS against the user's account and
 * visible under Control Panel > Credential Manager > Windows Credentials.
 */
class CredentialApiKeyStore : ApiKeyStore {
    override fun apiKey(provider: ProviderKind): String? =
        if (isWindows) WindowsCredentials.read(target(provider)) else null

    override fun setApiKey(provider: ProviderKind, key: String?) {
        if (!isWindows) return
        val trimmed = key?.trim()
        if (trimmed.isNullOrEmpty()) WindowsCredentials.delete(target(provider))
        else WindowsCredentials.write(target(provider), trimmed)
    }

    private fun target(provider: ProviderKind) = "RewordMe/${provider.id}"
}

/**
 * advapi32.dll credential calls, which jna-platform does not wrap. Failures are
 * swallowed: a vault the app cannot reach must not stop it from starting, and
 * the caller treats a missing key the same as a missing vault.
 */
private object WindowsCredentials {
    fun read(target: String): String? = runCatching {
        val holder = PointerByReference()
        if (!Advapi32Cred.INSTANCE.CredReadW(WString(target), CRED_TYPE_GENERIC, 0, holder)) {
            return null
        }
        val pointer = holder.value ?: return null
        try {
            val credential = Credential(pointer)
            credential.read()
            val blob = credential.CredentialBlob
            val size = credential.CredentialBlobSize
            if (blob == null || size <= 0) null
            else String(blob.getByteArray(0, size), Charsets.UTF_8)
        } finally {
            Advapi32Cred.INSTANCE.CredFree(pointer)
        }
    }.getOrNull()

    fun write(target: String, secret: String) {
        runCatching {
            val bytes = secret.toByteArray(Charsets.UTF_8)
            // A local, so the GC cannot free the blob before the call returns.
            val blob = Memory(bytes.size.toLong())
            blob.write(0, bytes, 0, bytes.size)
            val credential = Credential().apply {
                Type = CRED_TYPE_GENERIC
                TargetName = WString(target)
                CredentialBlobSize = bytes.size
                CredentialBlob = blob
                Persist = CRED_PERSIST_LOCAL_MACHINE
            }
            credential.write()
            Advapi32Cred.INSTANCE.CredWriteW(credential, 0)
        }
    }

    fun delete(target: String) {
        runCatching { Advapi32Cred.INSTANCE.CredDeleteW(WString(target), CRED_TYPE_GENERIC, 0) }
    }

    private const val CRED_TYPE_GENERIC = 1
    private const val CRED_PERSIST_LOCAL_MACHINE = 2
}

/** Public to the JVM because JNA reads the fields reflectively. */
@Suppress("PropertyName")
@Structure.FieldOrder(
    "Flags", "Type", "TargetName", "Comment", "LastWritten", "CredentialBlobSize",
    "CredentialBlob", "Persist", "AttributeCount", "Attributes", "TargetAlias", "UserName"
)
internal class Credential(pointer: Pointer? = null) : Structure(pointer) {
    @JvmField var Flags: Int = 0
    @JvmField var Type: Int = 0
    @JvmField var TargetName: WString? = null
    @JvmField var Comment: WString? = null
    @JvmField var LastWritten: WinBase.FILETIME = WinBase.FILETIME()
    @JvmField var CredentialBlobSize: Int = 0
    @JvmField var CredentialBlob: Pointer? = null
    @JvmField var Persist: Int = 0
    @JvmField var AttributeCount: Int = 0
    @JvmField var Attributes: Pointer? = null
    @JvmField var TargetAlias: WString? = null
    @JvmField var UserName: WString? = null
}

@Suppress("FunctionName")
internal interface Advapi32Cred : StdCallLibrary {
    fun CredReadW(target: WString, type: Int, flags: Int, credential: PointerByReference): Boolean
    fun CredWriteW(credential: Credential, flags: Int): Boolean
    fun CredDeleteW(target: WString, type: Int, flags: Int): Boolean
    fun CredFree(buffer: Pointer)

    companion object {
        val INSTANCE: Advapi32Cred by lazy {
            Native.load("Advapi32", Advapi32Cred::class.java)
        }
    }
}

package dev.mjablonski.rewordme.data

import dev.mjablonski.rewordme.domain.ApiKeyStore
import dev.mjablonski.rewordme.domain.ConfigStore
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordConfig
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** %APPDATA%\RewordMe on Windows, ~/.config/rewordme elsewhere. */
fun defaultConfigDirectory(): Path {
    val appData = System.getenv("APPDATA")
    return if (appData != null && System.getProperty("os.name").startsWith("Windows")) {
        Path.of(appData, "RewordMe")
    } else {
        Path.of(System.getProperty("user.home"), ".config", "rewordme")
    }
}

private val prettyJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/** Non-secret settings as JSON on disk. */
class JsonConfigStore(
    private val directory: Path = defaultConfigDirectory()
) : ConfigStore {
    private val file: Path get() = directory.resolve("config.json")
    private val invalidBackup: Path get() = directory.resolve("config.invalid.json")

    override fun load(): RewordConfig {
        if (!Files.isRegularFile(file)) return RewordConfig()
        return try {
            prettyJson.decodeFromString<RewordConfig>(Files.readString(file))
        } catch (_: Throwable) {
            // Preserve the bytes that failed to parse before a later save can
            // replace config.json. This makes recovery and bug reports possible.
            runCatching {
                Files.copy(file, invalidBackup, StandardCopyOption.REPLACE_EXISTING)
            }
            RewordConfig()
        }
    }

    override fun save(config: RewordConfig) {
        writeTextAtomically(file, prettyJson.encodeToString(RewordConfig.serializer(), config))
    }
}

/**
 * Fallback key store for platforms without a system vault: a JSON file next to
 * the config, readable only by the owner where the filesystem supports it. On
 * Windows the Credential Manager is used instead and this file is migrated away.
 */
class FileApiKeyStore(
    private val directory: Path = defaultConfigDirectory()
) : ApiKeyStore {
    private val file: Path get() = directory.resolve("keys.json")

    override fun apiKey(provider: ProviderKind): String? = readAll()[provider.id]

    override fun setApiKey(provider: ProviderKind, key: String?): Boolean = runCatching {
        val keys = readAll().toMutableMap()
        val trimmed = key?.trim()
        if (trimmed.isNullOrEmpty()) keys.remove(provider.id) else keys[provider.id] = trimmed
        writeTextAtomically(file, prettyJson.encodeToString(keysSerializer, keys))
        restrictToOwner()
        true
    }.getOrDefault(false)

    /** Removes the file once its contents live somewhere safer. */
    fun discard() {
        runCatching { Files.deleteIfExists(file) }
    }

    private fun readAll(): Map<String, String> = runCatching {
        prettyJson.decodeFromString(keysSerializer, Files.readString(file))
    }.getOrElse { emptyMap() }

    private companion object {
        val keysSerializer = kotlinx.serialization.builtins.MapSerializer(
            String.serializer(), String.serializer()
        )
    }

    private fun restrictToOwner() {
        runCatching {
            val f = file.toFile()
            f.setReadable(false, false)
            f.setWritable(false, false)
            f.setReadable(true, true)
            f.setWritable(true, true)
        }
    }
}

/** Same-directory temp + fsync + replace keeps crashes from truncating JSON. */
internal fun writeTextAtomically(target: Path, text: String) {
    Files.createDirectories(target.parent)
    val temporary = target.resolveSibling(".${target.fileName}.${UUID.randomUUID()}.tmp")
    try {
        FileChannel.open(
            temporary,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
        ).use { channel ->
            val bytes = ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8))
            while (bytes.hasRemaining()) channel.write(bytes)
            channel.force(true)
        }
        try {
            Files.move(
                temporary,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(temporary)
    }
}

package dev.mjablonski.rewordme.data

import dev.mjablonski.rewordme.domain.ApiKeyStore
import dev.mjablonski.rewordme.domain.ConfigStore
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordConfig
import java.nio.file.Files
import java.nio.file.Path
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

    override fun load(): RewordConfig = runCatching {
        prettyJson.decodeFromString<RewordConfig>(Files.readString(file))
    }.getOrElse { RewordConfig() }

    override fun save(config: RewordConfig) {
        Files.createDirectories(directory)
        Files.writeString(file, prettyJson.encodeToString(RewordConfig.serializer(), config))
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

    override fun setApiKey(provider: ProviderKind, key: String?) {
        val keys = readAll().toMutableMap()
        val trimmed = key?.trim()
        if (trimmed.isNullOrEmpty()) keys.remove(provider.id) else keys[provider.id] = trimmed
        Files.createDirectories(directory)
        Files.writeString(file, prettyJson.encodeToString(keysSerializer, keys))
        restrictToOwner()
    }

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

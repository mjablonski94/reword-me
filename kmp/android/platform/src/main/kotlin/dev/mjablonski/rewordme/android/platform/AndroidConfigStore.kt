package dev.mjablonski.rewordme.android.platform

import android.content.Context
import dev.mjablonski.rewordme.domain.ConfigStore
import dev.mjablonski.rewordme.models.RewordConfig
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.serialization.json.Json

private val prettyJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/**
 * The desktop keeps config.json under %APPDATA%; here it lives in the
 * app-private filesDir. Same forgiving contract: an unreadable file is
 * backed up for diagnosis and replaced by defaults instead of crashing.
 *
 * Writes carry the same guarantees as the desktop's writeTextAtomically:
 * a unique temp name (concurrent savers can't truncate each other's temp),
 * fsync before rename (a crash can't install a half-written file), and a
 * process-wide lock so overlapping saves serialize instead of interleaving.
 */
class AndroidConfigStore(private val directory: File) : ConfigStore {

    constructor(context: Context) : this(context.applicationContext.filesDir)

    private val file = File(directory, "config.json")
    private val invalidBackup = File(directory, "config.invalid.json")

    override fun load(): RewordConfig = synchronized(lock) {
        if (!file.isFile) return RewordConfig()
        try {
            prettyJson.decodeFromString<RewordConfig>(file.readText())
        } catch (_: Throwable) {
            runCatching { file.copyTo(invalidBackup, overwrite = true) }
            RewordConfig()
        }
    }

    override fun save(config: RewordConfig) = synchronized(lock) {
        val text = prettyJson.encodeToString(RewordConfig.serializer(), config)
        val temporary = File(directory, ".config.json.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            // rename(2) replaces the target atomically on Android's filesystems.
            if (!temporary.renameTo(file)) {
                error("Could not move ${temporary.name} over ${file.name}")
            }
        } finally {
            temporary.delete()
        }
    }

    private companion object {
        /** One lock per process: every instance points at the same file. */
        val lock = Any()
    }
}

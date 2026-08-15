package dev.mjablonski.rewordme.android.platform

import android.content.Context
import dev.mjablonski.rewordme.domain.ConfigStore
import dev.mjablonski.rewordme.models.RewordConfig
import java.io.File
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
 */
class AndroidConfigStore(context: Context) : ConfigStore {
    private val directory = context.applicationContext.filesDir
    private val file = File(directory, "config.json")
    private val invalidBackup = File(directory, "config.invalid.json")

    override fun load(): RewordConfig {
        if (!file.isFile) return RewordConfig()
        return try {
            prettyJson.decodeFromString<RewordConfig>(file.readText())
        } catch (_: Throwable) {
            runCatching { file.copyTo(invalidBackup, overwrite = true) }
            RewordConfig()
        }
    }

    override fun save(config: RewordConfig) {
        // Same-directory temp + rename, so a crash mid-write can't truncate
        // the only copy of the user's settings.
        val temporary = File(directory, ".config.json.tmp")
        temporary.writeText(prettyJson.encodeToString(RewordConfig.serializer(), config))
        if (!temporary.renameTo(file)) {
            file.writeText(prettyJson.encodeToString(RewordConfig.serializer(), config))
            temporary.delete()
        }
    }
}

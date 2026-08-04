package dev.mjablonski.rewordme.app

import java.util.Properties

/** Release metadata shared by the packaged installer and the Settings UI. */
internal object AppInfo {
    val version: String by lazy {
        val properties = Properties()
        val stream = requireNotNull(
            AppInfo::class.java.classLoader.getResourceAsStream("app.properties")
        ) { "app.properties is missing from the application resources" }
        stream.use(properties::load)
        properties.getProperty("version")?.takeIf(String::isNotBlank) ?: "—"
    }
}

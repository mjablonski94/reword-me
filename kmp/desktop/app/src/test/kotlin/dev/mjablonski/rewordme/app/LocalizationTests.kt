package dev.mjablonski.rewordme.app

import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LocalizationTests {
    @Test
    fun packagedVersionIsAvailableToSettings() {
        assertEquals("1.0.1", AppInfo.version)
    }

    @Test
    fun everyLanguageHasTheEnglishKeySet() {
        val base = properties("Strings.properties")
        val languages = listOf("de", "es", "fr", "it", "ja", "ko", "pl", "pt", "uk", "zh")

        languages.forEach { language ->
            assertEquals(
                base.stringPropertyNames(),
                properties("Strings_${language}.properties").stringPropertyNames(),
                "localization key mismatch for $language"
            )
        }
    }

    private fun properties(name: String): Properties = Properties().apply {
        val stream = assertNotNull(
            LocalizationTests::class.java.classLoader.getResourceAsStream(name),
            name
        )
        stream.use(::load)
    }
}

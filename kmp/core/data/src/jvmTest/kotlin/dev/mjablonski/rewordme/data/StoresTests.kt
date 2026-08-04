package dev.mjablonski.rewordme.data

import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordConfig
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoresTests {
    private val directory = Files.createTempDirectory("rewordme-stores-test-")

    @AfterTest
    fun cleanUp() {
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    @Test
    fun invalidConfigIsQuarantinedBeforeReturningDefaults() {
        val invalid = "{ this is not valid JSON"
        Files.writeString(directory.resolve("config.json"), invalid)

        val loaded = JsonConfigStore(directory).load()

        assertEquals(RewordConfig(), loaded)
        assertEquals(invalid, Files.readString(directory.resolve("config.invalid.json")))
    }

    @Test
    fun configSaveAtomicallyProducesOneValidFileAndNoTemporaryRemainder() {
        val expected = RewordConfig(
            provider = ProviderKind.MISTRAL,
            basePrompt = "Keep the author's voice"
        )
        val store = JsonConfigStore(directory)

        store.save(expected)

        assertEquals(expected, store.load())
        Files.list(directory).use { paths ->
            assertFalse(paths.anyMatch { it.fileName.toString().endsWith(".tmp") })
        }
        assertTrue(Files.size(directory.resolve("config.json")) > 0)
    }

    @Test
    fun blankFallbackKeyDeletesThePersistedCredential() {
        val store = FileApiKeyStore(directory)
        assertTrue(store.setApiKey(ProviderKind.MISTRAL, "secret"))
        assertEquals("secret", store.apiKey(ProviderKind.MISTRAL))

        assertTrue(store.setApiKey(ProviderKind.MISTRAL, "   "))

        assertNull(store.apiKey(ProviderKind.MISTRAL))
    }
}

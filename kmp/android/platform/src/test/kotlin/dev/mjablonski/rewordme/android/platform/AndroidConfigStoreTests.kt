package dev.mjablonski.rewordme.android.platform

import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordConfig
import dev.mjablonski.rewordme.models.RewriteRule
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidConfigStoreTests {

    private fun temporaryDirectory(): File =
        File.createTempFile("config-store", "").let { marker ->
            marker.delete()
            File(marker.parentFile, "config-store-${marker.name}").apply { mkdirs() }
        }

    @Test
    fun roundTripsTheConfig() {
        val store = AndroidConfigStore(temporaryDirectory())
        val config = RewordConfig(
            provider = ProviderKind.MISTRAL,
            rules = listOf(RewriteRule(text = "Keep it short")),
            basePrompt = "Base."
        )
        store.save(config)
        assertEquals(config, store.load())
    }

    @Test
    fun hammeringSaveFromManyThreadsNeverCorruptsTheFile() {
        val directory = temporaryDirectory()
        val store = AndroidConfigStore(directory)
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val writers = 64
        val done = CountDownLatch(writers)
        repeat(writers) { index ->
            pool.execute {
                start.await()
                store.save(RewordConfig(basePrompt = "prompt-$index"))
                done.countDown()
            }
        }
        start.countDown()
        done.await()
        pool.shutdown()

        // Whatever write won, the file parses and is one of the writers'
        // values - never defaults (the corrupted-file fallback), never a
        // half-written body, and no temp files left behind.
        val loaded = store.load()
        assertTrue(loaded.basePrompt.startsWith("prompt-"))
        assertTrue(
            directory.listFiles().orEmpty().none { it.name.endsWith(".tmp") },
            "temp files should be cleaned up"
        )
        assertTrue(File(directory, "config.invalid.json").exists().not())
    }

    @Test
    fun unreadableFileIsQuarantinedAndReplacedByDefaults() {
        val directory = temporaryDirectory()
        File(directory, "config.json").writeText("{ not json")
        val store = AndroidConfigStore(directory)

        assertEquals(RewordConfig(), store.load())
        assertTrue(File(directory, "config.invalid.json").isFile)
    }
}

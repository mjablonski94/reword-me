package dev.mjablonski.rewordme.domain

import dev.mjablonski.rewordme.models.ModelInfo
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordConfig
import dev.mjablonski.rewordme.models.RewriteRule
import dev.mjablonski.rewordme.models.RuleKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PromptBuilderTest {
    @Test
    fun coreOnlyWhenNothingElseConfigured() {
        assertEquals(
            PromptBuilder.CORE_INSTRUCTION,
            PromptBuilder.systemPrompt(emptyList(), "", null)
        )
    }

    @Test
    fun rulesAreGroupedAndDisabledOnesSkipped() {
        val prompt = PromptBuilder.systemPrompt(
            listOf(
                RewriteRule(kind = RuleKind.DO, text = "Keep it short"),
                RewriteRule(kind = RuleKind.DONT, text = "No emoji"),
                RewriteRule(kind = RuleKind.DO, text = "Off", isEnabled = false)
            ),
            basePrompt = "Base.",
            steering = "more formal"
        )
        assertTrue("Do:\n- Keep it short" in prompt)
        assertTrue("Don't:\n- No emoji" in prompt)
        assertFalse("Off" in prompt)
        assertTrue(prompt.endsWith("Additional instruction for this rewrite only: more formal"))
    }
}

class ModelSelectionTest {
    @Test
    fun anthropicPicksNewestHaiku() {
        val pick = ModelSelection.defaultModel(
            ProviderKind.ANTHROPIC,
            listOf(
                ModelInfo("claude-opus-5"),
                ModelInfo("claude-3-5-haiku-20241022"),
                ModelInfo("claude-haiku-4-5")
            )
        )
        assertEquals("claude-haiku-4-5", pick?.id)
    }

    @Test
    fun geminiPrefersStableFlashLite() {
        val pick = ModelSelection.defaultModel(
            ProviderKind.GEMINI,
            listOf(
                ModelInfo("gemini-2.5-pro"),
                ModelInfo("gemini-3.0-flash-lite-preview"),
                ModelInfo("gemini-2.5-flash-lite")
            )
        )
        assertEquals("gemini-2.5-flash-lite", pick?.id)
    }

    @Test
    fun ollamaKeepsServerOrder() {
        val pick = ModelSelection.defaultModel(
            ProviderKind.OLLAMA,
            listOf(ModelInfo("qwen2.5:7b"), ModelInfo("llama3.2:latest"))
        )
        assertEquals("qwen2.5:7b", pick?.id)
    }

    @Test
    fun emptyListGivesNull() {
        assertNull(ModelSelection.defaultModel(ProviderKind.OPENAI, emptyList()))
    }
}

class ModelResolverTest {
    private class Listing : ModelListing {
        var calls = 0

        override suspend fun listModels(
            provider: ProviderKind,
            apiKey: String,
            endpoint: String?
        ): List<ModelInfo> {
            calls++
            return listOf(ModelInfo(if (provider == ProviderKind.OLLAMA) "local" else "gpt-5-nano"))
        }
    }

    @Test
    fun automaticCacheIncludesApiKey() = runBlocking {
        val listing = Listing()
        val resolver = ModelResolver()
        val config = RewordConfig(provider = ProviderKind.OPENAI)

        resolver.model(config, "first-key", listing)
        resolver.model(config, "second-key", listing)

        assertEquals(2, listing.calls, "a changed credential can expose a different catalog")
    }

    @Test
    fun automaticCacheIncludesEndpoint() = runBlocking {
        val listing = Listing()
        val resolver = ModelResolver()

        resolver.model(
            RewordConfig(provider = ProviderKind.OLLAMA, ollamaHost = "http://first:11434"),
            "",
            listing
        )
        resolver.model(
            RewordConfig(provider = ProviderKind.OLLAMA, ollamaHost = "http://second:11434"),
            "",
            listing
        )

        assertEquals(2, listing.calls, "a different Ollama server needs its own resolution")
    }
}

class SelectionFilterTest {
    @Test
    fun meaningfulTextPasses() {
        assertTrue(SelectionFilter.isMeaningful("Hello world"))
        assertTrue(SelectionFilter.isMeaningful("  fix  "))
    }

    @Test
    fun noiseIsRejected() {
        assertFalse(SelectionFilter.isMeaningful(""))
        assertFalse(SelectionFilter.isMeaningful("  \n "))
        assertFalse(SelectionFilter.isMeaningful("ab"))
        assertFalse(SelectionFilter.isMeaningful("12345"))
        assertFalse(SelectionFilter.isMeaningful("-> -> ->"))
    }
}

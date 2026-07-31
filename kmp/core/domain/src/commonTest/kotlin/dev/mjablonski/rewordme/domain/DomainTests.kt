package dev.mjablonski.rewordme.domain

import dev.mjablonski.rewordme.models.ModelInfo
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewriteRule
import dev.mjablonski.rewordme.models.RuleKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

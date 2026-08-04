package dev.mjablonski.rewordme.models

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun roundTrip() {
        val config = RewordConfig(
            provider = ProviderKind.OLLAMA,
            model = "llama3.2",
            rules = listOf(RewriteRule(kind = RuleKind.DONT, text = "No emoji", isEnabled = false)),
            basePrompt = "Keep my voice.",
            ollamaHost = "http://192.168.1.20:11434"
        )
        val decoded = json.decodeFromString<RewordConfig>(json.encodeToString(RewordConfig.serializer(), config))
        assertEquals(config, decoded)
    }

    @Test
    fun emptyJsonFallsBackToDefaults() {
        val decoded = json.decodeFromString<RewordConfig>("{}")
        assertEquals(ProviderKind.GEMINI, decoded.provider)
        assertEquals("Ctrl+Alt+R", decoded.hotkey.display)
        assertEquals(null, decoded.model)
    }

    @Test
    fun modelSelectionsBelongToProvidersAndLegacyValueMigrates() {
        val legacy = json.decodeFromString<RewordConfig>(
            """{"provider":"GEMINI","model":"gemini-2.5-flash"}"""
        )
        assertEquals("gemini-2.5-flash", legacy.selectedModel)

        val openAi = legacy.selectingProvider(ProviderKind.OPENAI).selectingModel("gpt-5-mini")
        assertEquals("gpt-5-mini", openAi.selectedModel)
        assertEquals(
            "gemini-2.5-flash",
            openAi.selectingProvider(ProviderKind.GEMINI).selectedModel
        )
    }

    @Test
    fun ollamaEndpointNormalization() {
        assertEquals("http://localhost:11434/v1", OllamaEndpoint.baseUrl("http://localhost:11434"))
        assertEquals("http://192.168.1.20:11434/v1", OllamaEndpoint.baseUrl("192.168.1.20:11434/"))
        assertEquals("http://localhost:11434/v1", OllamaEndpoint.baseUrl("  "))
        assertEquals("http://my-server:8080/v1", OllamaEndpoint.baseUrl("http://my-server:8080/v1"))
    }

    @Test
    fun endpointOverrideOnlyForOllama() {
        assertEquals(null, RewordConfig(provider = ProviderKind.OPENAI).endpointOverride)
        assertEquals(
            "http://localhost:11434/v1",
            RewordConfig(provider = ProviderKind.OLLAMA).endpointOverride
        )
    }
}

class ProviderKindTest {
    @Test
    fun accessModesAreExplicitAndProviderOrderIsStable() {
        val apiProviders = setOf(
            ProviderKind.GEMINI, ProviderKind.OPENAI, ProviderKind.ANTHROPIC,
            ProviderKind.MISTRAL, ProviderKind.XAI, ProviderKind.DEEPSEEK
        )
        ProviderKind.entries.forEach { provider ->
            assertEquals(provider in apiProviders, provider.requiresApiKey)
        }
        assertEquals(
            listOf(
                ProviderKind.GEMINI, ProviderKind.LOCAL, ProviderKind.OPENAI,
                ProviderKind.CODEX, ProviderKind.ANTHROPIC, ProviderKind.CLAUDE_ACCOUNT,
                ProviderKind.MISTRAL, ProviderKind.XAI, ProviderKind.DEEPSEEK, ProviderKind.OLLAMA
            ),
            ProviderKind.entries
        )
        assertEquals("Gemini (Recommended)", ProviderKind.entries[0].displayName)
        assertEquals("Offline models (Local)", ProviderKind.entries[1].displayName)
        assertFalse(ProviderKind.OLLAMA.requiresApiKey)
    }

    @Test
    fun offlineCatalogIsPinnedAndHasInformationalLicenses() {
        val models = LocalModelCatalog.ALL
        assertEquals(6, models.size)
        assertEquals(models.size, models.map { it.id }.toSet().size)
        assertEquals(models.size, models.map { it.fileName }.toSet().size)
        assertEquals(LocalModelCatalog.DEFAULT, models.first())
        assertTrue(models.map { it.maker }.toSet().containsAll(
            setOf("Qwen", "Google", "Hugging Face", "Mistral AI")
        ))
        models.forEach { model ->
            assertTrue(model.byteCount > 0)
            assertTrue(model.tier.isNotBlank())
            assertTrue(model.sha256.matches(Regex("[0-9a-f]{64}")))
            assertTrue(model.downloadUrl.contains("/resolve/${model.revision}/"))
            assertTrue(model.informationUrl.startsWith("https://huggingface.co/"))
            assertTrue(model.licenseUrl.startsWith("https://"))
            assertTrue(model.licenseName.isNotBlank())
        }
    }

    @Test
    fun modelFilters() {
        assertTrue(ProviderKind.OPENAI.includesModel("gpt-4o-mini"))
        assertFalse(ProviderKind.OPENAI.includesModel("text-embedding-3-small"))
        assertFalse(ProviderKind.MISTRAL.includesModel("mistral-embed"))
        assertFalse(ProviderKind.XAI.includesModel("grok-2-image"))
        assertFalse(ProviderKind.OLLAMA.includesModel("nomic-embed-text"))
    }

    @Test
    fun onlyTransientErrorsAreRetryable() {
        assertTrue(RewordError.RateLimited(5).isRetryable)
        assertTrue(RewordError.Api(503, "busy").isRetryable)
        assertTrue(RewordError.AccountCommandFailed("offline").isRetryable)
        assertFalse(RewordError.AccountCommandFailed("plan", retryable = false).isRetryable)
        assertFalse(RewordError.UsageLimitReached.isRetryable)
        assertFalse(RewordError.InvalidApiKey.isRetryable)
    }
}

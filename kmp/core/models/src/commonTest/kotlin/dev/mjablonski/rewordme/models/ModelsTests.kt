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
        assertEquals(ProviderKind.ANTHROPIC, decoded.provider)
        assertEquals("Ctrl+Alt+R", decoded.hotkey.display)
        assertEquals(null, decoded.model)
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
    fun onlyOllamaIsKeyless() {
        assertFalse(ProviderKind.OLLAMA.requiresApiKey)
        ProviderKind.entries.filter { it != ProviderKind.OLLAMA }
            .forEach { assertTrue(it.requiresApiKey) }
    }

    @Test
    fun modelFilters() {
        assertTrue(ProviderKind.OPENAI.includesModel("gpt-4o-mini"))
        assertFalse(ProviderKind.OPENAI.includesModel("text-embedding-3-small"))
        assertFalse(ProviderKind.MISTRAL.includesModel("mistral-embed"))
        assertFalse(ProviderKind.XAI.includesModel("grok-2-image"))
        assertFalse(ProviderKind.OLLAMA.includesModel("nomic-embed-text"))
    }
}

package dev.mjablonski.rewordme.data

import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientParsingTests {
    @Test
    fun anthropicParsesModelsAndReword() {
        val client = AnthropicClient()
        val models = client.parseModels(
            """{"data":[{"id":"claude-haiku-4-5","display_name":"Claude Haiku 4.5"}]}"""
        )
        assertEquals(listOf("claude-haiku-4-5"), models.map { it.id })

        val text = client.parseReword(
            """{"content":[{"type":"text","text":"Hello "},{"type":"text","text":"world."}],"stop_reason":"end_turn"}"""
        )
        assertEquals("Hello world.", text)
    }

    @Test
    fun anthropicRefusalThrows() {
        val error = assertFailsWith<RewordError.Refused> {
            AnthropicClient().parseReword(
                """{"content":[],"stop_reason":"refusal","stop_details":{"explanation":"declined"}}"""
            )
        }
        assertEquals("declined", error.explanation)
    }

    @Test
    fun anthropicRequestShape() {
        val request = AnthropicClient().rewordRequest("k", "m", "sys", "hi", null)
        assertEquals("POST", request.method)
        assertEquals("k", request.headers["x-api-key"])
        assertTrue(request.url.endsWith("/messages"))
        assertTrue(""""max_tokens":1024""" in request.jsonBody!!)
    }

    @Test
    fun openAiCompatibleFiltersAndPreservesOrder() {
        val models = OpenAiCompatibleClient(ProviderKind.OPENAI).parseModels(
            """{"data":[{"id":"gpt-4o-mini"},{"id":"whisper-1"},{"id":"gpt-4o"}]}"""
        )
        assertEquals(listOf("gpt-4o-mini", "gpt-4o"), models.map { it.id })
    }

    @Test
    fun openAiCompatibleTargetsProviderBaseUrl() {
        ProviderKind.entries.filter { it.openAiCompatibleBaseUrl != null }.forEach { kind ->
            val request = OpenAiCompatibleClient(kind).rewordRequest("k", "m", "s", "t", null)
            assertTrue(request.url.startsWith(kind.openAiCompatibleBaseUrl!!))
            assertEquals("Bearer k", request.headers["Authorization"])
        }
    }

    @Test
    fun geminiKeyGoesInHeaderNotUrl() {
        val request = GeminiClient().rewordRequest("secret", "gemini-2.5-flash-lite", "s", "t", null)
        assertEquals("secret", request.headers["x-goog-api-key"])
        assertFalse("secret" in request.url)
    }

    @Test
    fun geminiParsesModelsFilteringGenerateContent() {
        val models = GeminiClient().parseModels(
            """{"models":[
                {"name":"models/gemini-2.5-flash-lite","supportedGenerationMethods":["generateContent"]},
                {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]}
            ]}"""
        )
        assertEquals(listOf("gemini-2.5-flash-lite"), models.map { it.id })
    }

    @Test
    fun registryCoversEveryProvider() {
        val registry = ProviderClientRegistry()
        ProviderKind.entries.forEach { kind ->
            assertEquals(kind, registry.client(kind).kind)
        }
    }

    @Test
    fun errorDetailExtraction() {
        assertEquals(
            "Overloaded",
            RewordService.errorDetail("""{"error":{"message":"Overloaded"}}""")
        )
        assertEquals("boom", RewordService.errorDetail("""{"message":"boom"}"""))
        assertEquals("not json", RewordService.errorDetail("not json"))
    }
}

package dev.mjablonski.rewordme.data

import dev.mjablonski.rewordme.models.LocalModelCatalog
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

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
    fun registryCoversEveryHttpProvider() {
        val registry = ProviderClientRegistry()
        ProviderKind.entries.filterNot { it.isAccountProvider }.forEach { kind ->
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

    @Test
    fun usageAndBillingLimitErrorsAreDetectedSeparatelyFromTemporaryRateLimits() {
        val actionRequired = listOf(
            """{"error":{"message":"No credits","type":"insufficient_quota","code":"credit_balance_exhausted"}}""",
            """{"error":{"message":"Usage limit reached","code":"organization_usage_limit_exceeded"}}""",
            """{"error":{"message":"You exceeded your current quota. Check billing.","type":"insufficient_quota","code":null}}"""
        )
        actionRequired.forEach { body ->
            assertTrue(RewordService.isUsageLimitError(body), body)
        }
        assertFalse(
            RewordService.isUsageLimitError(
                """{"error":{"message":"Too many requests","type":"rate_limit_error","code":"rate_limit_exceeded"}}"""
            )
        )
        listOf(
            """{"error":{"message":"Quota exceeded for quota metric 'Generate Content API requests per minute'","status":"RESOURCE_EXHAUSTED"}}""",
            """{"error":{"message":"RPM limit reached; retry shortly","type":"resource_exhausted"}}""",
            """{"error":{"message":"TPM quota metric exceeded","type":"rate_limit_error"}}"""
        ).forEach { body ->
            assertFalse(RewordService.isUsageLimitError(body), body)
        }
    }

    @Test
    fun managedAndAccountCatalogsAreLocalAndKeyless() = runBlocking {
        val service = RewordService()
        assertEquals(
            LocalModelCatalog.ALL.map { it.id },
            service.listModels(ProviderKind.LOCAL, "", null).map { it.id }
        )
        assertEquals(
            listOf("automatic"),
            service.listModels(ProviderKind.CODEX, "", null).map { it.id }
        )
        assertEquals(
            listOf("automatic", "sonnet", "opus"),
            service.listModels(ProviderKind.CLAUDE_ACCOUNT, "", null).map { it.id }
        )
    }
}

package dev.mjablonski.rewordme.data

import dev.mjablonski.rewordme.models.ModelInfo
import dev.mjablonski.rewordme.models.ProviderKind
import kotlinx.serialization.json.Json

/**
 * One provider's wire format: how to build requests and read responses.
 * Request descriptions are plain values so they stay pure and testable;
 * RewordService performs the actual HTTP call.
 */
data class ProviderRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val jsonBody: String? = null
)

interface ProviderClient {
    val kind: ProviderKind

    fun modelsRequest(apiKey: String, endpoint: String?): ProviderRequest
    fun parseModels(json: String): List<ModelInfo>
    fun rewordRequest(
        apiKey: String,
        model: String,
        systemPrompt: String,
        text: String,
        endpoint: String?
    ): ProviderRequest

    fun parseReword(json: String): String
}

internal val lenientJson = Json { ignoreUnknownKeys = true }

/** Maps HTTP wire-format providers to clients; account providers bypass it. */
class ProviderClientRegistry(
    clients: List<ProviderClient> = defaultClients()
) {
    private val byKind = clients.associateBy(ProviderClient::kind)

    fun client(kind: ProviderKind): ProviderClient =
        checkNotNull(byKind[kind]) { "No client registered for ${kind.id}" }

    companion object {
        fun defaultClients(): List<ProviderClient> =
            listOf(AnthropicClient(), GeminiClient()) +
                listOf(
                    ProviderKind.LOCAL, ProviderKind.OPENAI, ProviderKind.MISTRAL, ProviderKind.XAI,
                    ProviderKind.DEEPSEEK, ProviderKind.OLLAMA
                ).map(::OpenAiCompatibleClient)
    }
}

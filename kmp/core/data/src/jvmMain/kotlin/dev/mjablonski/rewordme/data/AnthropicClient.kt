package dev.mjablonski.rewordme.data

import dev.mjablonski.rewordme.models.ModelInfo
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordError
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The Anthropic Messages API (anthropic-version: 2023-06-01). */
class AnthropicClient : ProviderClient {
    override val kind = ProviderKind.ANTHROPIC

    override fun modelsRequest(apiKey: String, endpoint: String?): ProviderRequest =
        ProviderRequest(
            url = "${endpoint ?: BASE_URL}/models?limit=100",
            headers = mapOf("x-api-key" to apiKey, "anthropic-version" to API_VERSION)
        )

    @Serializable
    private data class ModelsResponse(val data: List<Model>) {
        @Serializable
        data class Model(val id: String, @SerialName("display_name") val displayName: String? = null)
    }

    override fun parseModels(json: String): List<ModelInfo> {
        val response = runCatching { lenientJson.decodeFromString<ModelsResponse>(json) }
            .getOrElse { throw RewordError.InvalidResponse }
        return response.data.map { ModelInfo(it.id, it.displayName ?: it.id) }
    }

    override fun rewordRequest(
        apiKey: String,
        model: String,
        systemPrompt: String,
        text: String,
        endpoint: String?
    ): ProviderRequest {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", outputTokenBudget(text))
            put("system", systemPrompt)
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", text)
                })
            })
        }
        return ProviderRequest(
            url = "${endpoint ?: BASE_URL}/messages",
            method = "POST",
            headers = mapOf("x-api-key" to apiKey, "anthropic-version" to API_VERSION),
            jsonBody = body.toString()
        )
    }

    @Serializable
    private data class MessageResponse(
        val content: List<Block> = emptyList(),
        @SerialName("stop_reason") val stopReason: String? = null,
        @SerialName("stop_details") val stopDetails: StopDetails? = null
    ) {
        @Serializable
        data class Block(val type: String, val text: String? = null)

        @Serializable
        data class StopDetails(val explanation: String? = null)
    }

    override fun parseReword(json: String): String {
        val response = runCatching { lenientJson.decodeFromString<MessageResponse>(json) }
            .getOrElse { throw RewordError.InvalidResponse }
        if (response.stopReason == "refusal") {
            throw RewordError.Refused(response.stopDetails?.explanation)
        }
        val text = response.content
            .filter { it.type == "text" }
            .mapNotNull { it.text }
            .joinToString("")
            .trim()
        if (text.isEmpty()) throw RewordError.EmptyResponse
        return text
    }

    companion object {
        private const val BASE_URL = "https://api.anthropic.com/v1"
        private const val API_VERSION = "2023-06-01"

        /** Generous cap for a rewrite: roughly the input length again. */
        internal fun outputTokenBudget(text: String): Int =
            text.length.coerceIn(1024, 8192)
    }
}

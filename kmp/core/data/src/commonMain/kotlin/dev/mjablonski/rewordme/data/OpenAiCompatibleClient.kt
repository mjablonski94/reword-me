package dev.mjablonski.rewordme.data

import dev.mjablonski.rewordme.models.ModelInfo
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordError
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The OpenAI chat-completions dialect, shared by OpenAI, Mistral, xAI,
 * DeepSeek and Ollama - only the base URL and the model filter differ.
 */
class OpenAiCompatibleClient(override val kind: ProviderKind) : ProviderClient {
    init {
        requireNotNull(kind.openAiCompatibleBaseUrl) {
            "${kind.id} does not speak the OpenAI dialect"
        }
    }

    private fun baseUrl(endpoint: String?): String =
        endpoint ?: kind.openAiCompatibleBaseUrl!!

    override fun modelsRequest(apiKey: String, endpoint: String?): ProviderRequest =
        ProviderRequest(
            url = "${baseUrl(endpoint)}/models",
            headers = mapOf("Authorization" to "Bearer $apiKey")
        )

    @Serializable
    private data class ModelsResponse(val data: List<Model>) {
        @Serializable
        data class Model(val id: String)
    }

    override fun parseModels(json: String): List<ModelInfo> {
        val response = runCatching { lenientJson.decodeFromString<ModelsResponse>(json) }
            .getOrElse { throw RewordError.InvalidResponse }
        // Server order is preserved - Ollama lists most recently used
        // models first and the automatic pick relies on that.
        return response.data
            .map { it.id }
            .filter(kind::includesModel)
            .map { ModelInfo(it) }
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
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", systemPrompt) })
                add(buildJsonObject { put("role", "user"); put("content", text) })
            })
        }
        return ProviderRequest(
            url = "${baseUrl(endpoint)}/chat/completions",
            method = "POST",
            headers = mapOf("Authorization" to "Bearer $apiKey"),
            jsonBody = body.toString()
        )
    }

    @Serializable
    private data class ChatResponse(val choices: List<Choice> = emptyList()) {
        @Serializable
        data class Choice(val message: Message) {
            @Serializable
            data class Message(val content: String? = null)
        }
    }

    override fun parseReword(json: String): String {
        val response = runCatching { lenientJson.decodeFromString<ChatResponse>(json) }
            .getOrElse { throw RewordError.InvalidResponse }
        val text = response.choices.firstOrNull()?.message?.content.orEmpty().trim()
        if (text.isEmpty()) throw RewordError.EmptyResponse
        return text
    }
}

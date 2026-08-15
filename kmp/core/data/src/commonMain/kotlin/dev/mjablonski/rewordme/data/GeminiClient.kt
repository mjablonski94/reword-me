package dev.mjablonski.rewordme.data

import dev.mjablonski.rewordme.models.ModelInfo
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordError
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The Google Gemini API (generativelanguage.googleapis.com).
 * The key goes in the x-goog-api-key header, never in the URL.
 */
class GeminiClient : ProviderClient {
    override val kind = ProviderKind.GEMINI

    override fun modelsRequest(apiKey: String, endpoint: String?): ProviderRequest =
        ProviderRequest(
            url = "${endpoint ?: BASE_URL}/models?pageSize=200",
            headers = mapOf("x-goog-api-key" to apiKey)
        )

    @Serializable
    private data class ModelsResponse(val models: List<Model> = emptyList()) {
        @Serializable
        data class Model(
            val name: String,
            val displayName: String? = null,
            val supportedGenerationMethods: List<String> = emptyList()
        )
    }

    override fun parseModels(json: String): List<ModelInfo> {
        val response = runCatching { lenientJson.decodeFromString<ModelsResponse>(json) }
            .getOrElse { throw RewordError.InvalidResponse }
        return response.models
            .filter { "generateContent" in it.supportedGenerationMethods }
            .map { ModelInfo(it.name.removePrefix("models/"), it.displayName ?: it.name) }
    }

    override fun rewordRequest(
        apiKey: String,
        model: String,
        systemPrompt: String,
        text: String,
        endpoint: String?
    ): ProviderRequest {
        val body = buildJsonObject {
            put("system_instruction", buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", systemPrompt) }) })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", text) }) })
                })
            })
        }
        return ProviderRequest(
            url = "${endpoint ?: BASE_URL}/models/$model:generateContent",
            method = "POST",
            headers = mapOf("x-goog-api-key" to apiKey),
            jsonBody = body.toString()
        )
    }

    @Serializable
    private data class GenerateResponse(val candidates: List<Candidate> = emptyList()) {
        @Serializable
        data class Candidate(val content: Content? = null) {
            @Serializable
            data class Content(val parts: List<Part> = emptyList()) {
                @Serializable
                data class Part(val text: String? = null)
            }
        }
    }

    override fun parseReword(json: String): String {
        val response = runCatching { lenientJson.decodeFromString<GenerateResponse>(json) }
            .getOrElse { throw RewordError.InvalidResponse }
        val text = response.candidates.firstOrNull()?.content?.parts
            .orEmpty()
            .mapNotNull { it.text }
            .joinToString("")
            .trim()
        if (text.isEmpty()) throw RewordError.EmptyResponse
        return text
    }

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    }
}

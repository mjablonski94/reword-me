package dev.mjablonski.rewordme.models

import kotlinx.serialization.Serializable

/** The LLM providers RewordMe can talk to. One API key per provider. */
@Serializable
enum class ProviderKind(val id: String) {
    ANTHROPIC("anthropic"),
    OPENAI("openai"),
    GEMINI("gemini"),
    MISTRAL("mistral"),
    XAI("xai"),
    DEEPSEEK("deepseek"),
    OLLAMA("ollama");

    val displayName: String
        get() = when (this) {
            ANTHROPIC -> "Claude (Anthropic)"
            OPENAI -> "ChatGPT (OpenAI)"
            GEMINI -> "Gemini (Google)"
            MISTRAL -> "Mistral"
            XAI -> "Grok (xAI)"
            DEEPSEEK -> "DeepSeek"
            OLLAMA -> "Ollama (local)"
        }

    /** Ollama runs on the user's machine and needs no key at all. */
    val requiresApiKey: Boolean get() = this != OLLAMA

    val apiKeyConsoleUrl: String
        get() = when (this) {
            ANTHROPIC -> "https://platform.claude.com/settings/keys"
            OPENAI -> "https://platform.openai.com/api-keys"
            GEMINI -> "https://aistudio.google.com/apikey"
            MISTRAL -> "https://console.mistral.ai/api-keys"
            XAI -> "https://console.x.ai"
            DEEPSEEK -> "https://platform.deepseek.com/api_keys"
            OLLAMA -> "https://ollama.com/download"
        }

    /** Base URL for providers speaking the OpenAI chat-completions dialect. */
    val openAiCompatibleBaseUrl: String?
        get() = when (this) {
            ANTHROPIC, GEMINI -> null
            OPENAI -> "https://api.openai.com/v1"
            MISTRAL -> "https://api.mistral.ai/v1"
            XAI -> "https://api.x.ai/v1"
            DEEPSEEK -> "https://api.deepseek.com/v1"
            OLLAMA -> OllamaEndpoint.baseUrl(OllamaEndpoint.DEFAULT_HOST)
        }

    /**
     * Model-list filter: OpenAI-style listings mix chat models with
     * embeddings, audio and image models; keep chat-capable text models.
     */
    fun includesModel(modelId: String): Boolean {
        val lower = modelId.lowercase()
        return when (this) {
            ANTHROPIC, GEMINI -> true // their APIs are filtered during parsing
            OPENAI -> {
                val excluded = listOf(
                    "embedding", "whisper", "tts", "audio", "realtime", "image",
                    "dall-e", "moderation", "transcribe", "computer-use", "search", "instruct"
                )
                if (excluded.any(lower::contains)) false
                else lower.startsWith("gpt-") || Regex("^o[0-9]").containsMatchIn(lower)
            }
            MISTRAL -> listOf("embed", "moderation", "ocr", "transcribe", "voxtral")
                .none(lower::contains)
            XAI -> !lower.contains("image")
            DEEPSEEK -> true
            OLLAMA -> !lower.contains("embed")
        }
    }
}

/** A model as reported by the provider's model-listing endpoint. */
data class ModelInfo(val id: String, val displayName: String = id)

sealed class RewordError(message: String) : Exception(message) {
    data object MissingApiKey : RewordError("No API key configured. Add one in Settings.") {
        private fun readResolve(): Any = MissingApiKey
    }

    data object InvalidApiKey :
        RewordError("The API key was rejected by the provider. Check it in Settings.") {
        private fun readResolve(): Any = InvalidApiKey
    }

    data class RateLimited(val retryAfterSeconds: Int?) :
        RewordError("Rate limit reached. Try again in ${retryAfterSeconds ?: "a few"}s.")

    data class Refused(val explanation: String?) :
        RewordError(explanation ?: "The provider declined to rewrite this text.")

    data class Api(val status: Int, val detail: String) :
        RewordError("Provider error ($status): $detail")

    data object EmptyResponse : RewordError("The provider returned an empty response.") {
        private fun readResolve(): Any = EmptyResponse
    }

    data object InvalidResponse : RewordError("Could not read the provider's response.") {
        private fun readResolve(): Any = InvalidResponse
    }

    data object NoModelAvailable : RewordError("No usable model found for this provider.") {
        private fun readResolve(): Any = NoModelAvailable
    }
}

/**
 * Ollama's server address is user-configurable (OLLAMA_HOST, Docker port
 * mappings, another machine on the LAN). Normalizes whatever the user
 * typed into a usable OpenAI-compatible base URL.
 */
object OllamaEndpoint {
    const val DEFAULT_HOST = "http://localhost:11434"

    fun baseUrl(host: String): String {
        var trimmed = host.trim().ifEmpty { DEFAULT_HOST }
        if ("://" !in trimmed) trimmed = "http://$trimmed"
        trimmed = trimmed.trimEnd('/')
        if (!trimmed.endsWith("/v1")) trimmed += "/v1"
        return trimmed
    }
}

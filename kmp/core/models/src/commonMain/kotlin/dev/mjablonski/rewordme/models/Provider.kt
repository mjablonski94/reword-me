package dev.mjablonski.rewordme.models

import kotlinx.serialization.Serializable

/** How RewordMe authenticates and executes one provider. */
enum class ProviderAccess { API_KEY, ACCOUNT, MANAGED_LOCAL, EXTERNAL_LOCAL }

/**
 * The LLM providers RewordMe can talk to. Existing API enum names deliberately
 * stay unchanged so a saved key/config is never reinterpreted as account
 * access. Declaration order is the order shown in Settings.
 */
@Serializable
enum class ProviderKind(val id: String) {
    GEMINI("gemini"),
    LOCAL("local"),
    OPENAI("openai"),
    CODEX("codex"),
    ANTHROPIC("anthropic"),
    CLAUDE_ACCOUNT("claudeAccount"),
    MISTRAL("mistral"),
    XAI("xai"),
    DEEPSEEK("deepseek"),
    OLLAMA("ollama");

    val displayName: String
        get() = when (this) {
            GEMINI -> "Gemini (Recommended)"
            LOCAL -> "Offline models (Local)"
            OPENAI -> "OpenAI API"
            CODEX -> "Codex via ChatGPT"
            ANTHROPIC -> "Claude API"
            CLAUDE_ACCOUNT -> "Claude via Claude account"
            MISTRAL -> "Mistral"
            XAI -> "Grok (xAI)"
            DEEPSEEK -> "DeepSeek"
            OLLAMA -> "Ollama (External local)"
        }

    val access: ProviderAccess
        get() = when (this) {
            GEMINI, OPENAI, ANTHROPIC, MISTRAL, XAI, DEEPSEEK -> ProviderAccess.API_KEY
            CODEX, CLAUDE_ACCOUNT -> ProviderAccess.ACCOUNT
            LOCAL -> ProviderAccess.MANAGED_LOCAL
            OLLAMA -> ProviderAccess.EXTERNAL_LOCAL
        }

    val requiresApiKey: Boolean get() = access == ProviderAccess.API_KEY
    val isAccountProvider: Boolean get() = access == ProviderAccess.ACCOUNT

    val apiKeyConsoleUrl: String
        get() = when (this) {
            GEMINI -> "https://aistudio.google.com/apikey"
            LOCAL -> LocalModelCatalog.DEFAULT.informationUrl
            OPENAI -> "https://platform.openai.com/api-keys"
            CODEX -> "https://developers.openai.com/codex/cli"
            ANTHROPIC -> "https://platform.claude.com/settings/keys"
            CLAUDE_ACCOUNT -> "https://docs.anthropic.com/en/docs/claude-code/getting-started"
            MISTRAL -> "https://console.mistral.ai/api-keys"
            XAI -> "https://console.x.ai"
            DEEPSEEK -> "https://platform.deepseek.com/api_keys"
            OLLAMA -> "https://ollama.com/download"
        }

    /** Shown in the "get a key at ..." link instead of the full URL. */
    val apiKeyConsoleName: String
        get() = when (this) {
            GEMINI -> "aistudio.google.com"
            LOCAL -> "huggingface.co"
            OPENAI -> "platform.openai.com"
            CODEX -> "developers.openai.com"
            ANTHROPIC -> "platform.claude.com"
            CLAUDE_ACCOUNT -> "docs.anthropic.com"
            MISTRAL -> "console.mistral.ai"
            XAI -> "console.x.ai"
            DEEPSEEK -> "platform.deepseek.com"
            OLLAMA -> "ollama.com"
        }

    val keyPlaceholder: String
        get() = when (this) {
            GEMINI -> "AIza..."
            LOCAL, CODEX, CLAUDE_ACCOUNT, OLLAMA -> ""
            OPENAI, DEEPSEEK -> "sk-..."
            ANTHROPIC -> "sk-ant-..."
            MISTRAL -> "API key from console.mistral.ai"
            XAI -> "xai-..."
        }

    /** Base URL for providers speaking the OpenAI chat-completions dialect. */
    val openAiCompatibleBaseUrl: String?
        get() = when (this) {
            ANTHROPIC, GEMINI, CODEX, CLAUDE_ACCOUNT -> null
            // Managed local requests always provide a live endpoint override.
            LOCAL -> "http://127.0.0.1:1/v1"
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
            CODEX, CLAUDE_ACCOUNT, LOCAL -> true // locally supplied catalogs
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

data class OfflineModelManifest(
    val id: String,
    val displayName: String,
    val maker: String,
    val tier: String,
    val fileName: String,
    val byteCount: Long,
    val sha256: String,
    val revision: String,
    val repository: String,
    val licenseName: String,
    val licenseUrl: String
) {
    val downloadUrl: String get() = "https://huggingface.co/$repository/resolve/$revision/$fileName"
    val informationUrl: String get() = "https://huggingface.co/$repository"
}

object LocalModelCatalog {
    private const val APACHE = "https://www.apache.org/licenses/LICENSE-2.0"
    val QWEN35_SMALL = OfflineModelManifest(
        "qwen3.5-0.8b-q4_0", "Qwen 3.5 0.8B Q4", "Qwen", "Fastest",
        "Qwen3.5-0.8B-Q4_0.gguf", 563_036_064L,
        "57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf",
        "8fea620810c4afa23dd6443f999a48574c1611a3", "ggml-org/Qwen3.5-0.8B-GGUF",
        "Apache 2.0", APACHE
    )
    val GEMMA3 = OfflineModelManifest(
        "gemma-3-1b-it-q4_k_m", "Gemma 3 1B IT Q4", "Google", "Compact",
        "gemma-3-1b-it-Q4_K_M.gguf", 806_058_240L,
        "8ccc5cd1f1b3602548715ae25a66ed73fd5dc68a210412eea643eb20eb75a135",
        "f9c28bcd85737ffc5aef028638d3341d49869c27", "ggml-org/gemma-3-1b-it-GGUF",
        "Gemma Terms", "https://ai.google.dev/gemma/terms"
    )
    val QWEN3_BALANCED = OfflineModelManifest(
        "qwen3-1.7b-q4_k_m", "Qwen 3 1.7B Q4", "Qwen", "Balanced",
        "Qwen3-1.7B-Q4_K_M.gguf", 1_282_439_264L,
        "d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5",
        "daeb8e2d528a760970442092f6bf1e55c3b659eb", "ggml-org/Qwen3-1.7B-GGUF",
        "Apache 2.0", APACHE
    )
    val SMOLLM3 = OfflineModelManifest(
        "smollm3-3b-q4_k_m", "SmolLM3 3B Q4", "Hugging Face", "English-focused",
        "SmolLM3-Q4_K_M.gguf", 1_915_305_312L,
        "8334b850b7bd46238c16b0c550df2138f0889bf433809008cc17a8b05761863e",
        "4965cb60b150737b68a0408c36aeefb65078f894", "ggml-org/SmolLM3-3B-GGUF",
        "Apache 2.0", APACHE
    )
    val MINISTRAL3 = OfflineModelManifest(
        "ministral-3-3b-instruct-q4_k_m", "Ministral 3 3B Instruct Q4", "Mistral AI",
        "Quality alternative", "Ministral-3-3B-Instruct-2512-Q4_K_M.gguf", 2_147_023_008L,
        "9ed150d4367e68df0ac8e1540f6ddc65b42d0ee26378329d1ecbca60f93fc5f8",
        "eb599d408350ea2bb60452cb86be7c7b2fc28227", "mistralai/Ministral-3-3B-Instruct-2512-GGUF",
        "Apache 2.0", APACHE
    )
    val QWEN3_QUALITY = OfflineModelManifest(
        "qwen3-4b-q4_k_m", "Qwen 3 4B Q4", "Qwen", "Best multilingual quality",
        "Qwen3-4B-Q4_K_M.gguf", 2_497_280_640L,
        "ab27b9bfa375a178d6cba48f3ad892b94b7739659dcc7aae8058ce0ffed6b328",
        "2f3b082b1356a6123f7ed71e65aea340da25d53c", "ggml-org/Qwen3-4B-GGUF",
        "Apache 2.0", APACHE
    )
    val ALL = listOf(QWEN35_SMALL, GEMMA3, QWEN3_BALANCED, SMOLLM3, MINISTRAL3, QWEN3_QUALITY)
    val DEFAULT = QWEN35_SMALL
    fun model(id: String?): OfflineModelManifest = ALL.firstOrNull { it.id == id } ?: DEFAULT
}

/** Compatibility constants for the original single-model implementation. */
object LocalModelManifest {
    val ID get() = LocalModelCatalog.DEFAULT.id
    val DISPLAY_NAME get() = LocalModelCatalog.DEFAULT.displayName
    val FILE_NAME get() = LocalModelCatalog.DEFAULT.fileName
    val BYTE_COUNT get() = LocalModelCatalog.DEFAULT.byteCount
    val SHA256 get() = LocalModelCatalog.DEFAULT.sha256
    val REVISION get() = LocalModelCatalog.DEFAULT.revision
    val DOWNLOAD_URL get() = LocalModelCatalog.DEFAULT.downloadUrl
    val INFORMATION_URL get() = LocalModelCatalog.DEFAULT.informationUrl
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

    data object UsageLimitReached :
        RewordError("API credits or usage limit reached. Check the provider's billing and limits.") {
        private fun readResolve(): Any = UsageLimitReached
    }

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

    data class ProviderNotInstalled(val providerName: String) :
        RewordError("$providerName is not installed. Open Settings to install it.")

    data class AccountNotSignedIn(val providerName: String) :
        RewordError("$providerName is not signed in. Open Settings to connect your account.")

    data class AccountUsesApiKey(val providerName: String) :
        RewordError("$providerName is using API-key billing. Sign in with your subscription account instead.")

    data class AccountCommandFailed(val detail: String, val retryable: Boolean = true) :
        RewordError(detail)

    data object LocalModelNotDownloaded :
        RewordError("The local model is not downloaded. Download it in Settings.") {
        private fun readResolve(): Any = LocalModelNotDownloaded
    }

    data object LocalRuntimeUnavailable :
        RewordError("The bundled local AI runtime is unavailable. Reinstall RewordMe.") {
        private fun readResolve(): Any = LocalRuntimeUnavailable
    }

    data class LocalModelDownloadFailed(val detail: String) :
        RewordError("Local model download failed: $detail")

    val isRetryable: Boolean
        get() = when (this) {
            is RateLimited, EmptyResponse, InvalidResponse -> true
            is Api -> status == 408 || status == 409 || status == 425 || status in 500..599
            is AccountCommandFailed -> retryable
            MissingApiKey, InvalidApiKey, UsageLimitReached, is Refused, NoModelAvailable,
            is ProviderNotInstalled, is AccountNotSignedIn, is AccountUsesApiKey,
            LocalModelNotDownloaded, LocalRuntimeUnavailable -> false
            is LocalModelDownloadFailed -> true
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

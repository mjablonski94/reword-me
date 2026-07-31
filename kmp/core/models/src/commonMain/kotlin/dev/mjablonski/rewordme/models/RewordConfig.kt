package dev.mjablonski.rewordme.models

import kotlin.random.Random
import kotlinx.serialization.Serializable

@Serializable
enum class RuleKind { DO, DONT }

/** A user-defined do/don't rule, toggleable per rule. */
@Serializable
data class RewriteRule(
    val id: String = randomId(),
    val kind: RuleKind,
    val text: String,
    val isEnabled: Boolean = true
) {
    companion object {
        private fun randomId(): String =
            Random.nextBytes(8).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}

/**
 * The global shortcut that summons the popup, in Win32 terms
 * (RegisterHotKey modifier mask + virtual key code).
 */
@Serializable
data class HotkeyConfig(
    /** MOD_ALT = 0x1, MOD_CONTROL = 0x2, MOD_SHIFT = 0x4, MOD_WIN = 0x8. */
    val modifiers: Int = MOD_CONTROL or MOD_ALT,
    /** Virtual key code; 0x52 is R. */
    val virtualKey: Int = 0x52,
    val display: String = "Ctrl+Alt+R"
) {
    companion object {
        const val MOD_ALT = 0x1
        const val MOD_CONTROL = 0x2
        const val MOD_SHIFT = 0x4
        const val MOD_WIN = 0x8
    }
}

/** Everything except API keys (those live in the platform's secret store). */
@Serializable
data class RewordConfig(
    val provider: ProviderKind = ProviderKind.ANTHROPIC,
    /** null means automatic: the least costly model the provider lists. */
    val model: String? = null,
    val rules: List<RewriteRule> = emptyList(),
    val basePrompt: String = "",
    /** Where the local Ollama server listens; only used by the ollama provider. */
    val ollamaHost: String = OllamaEndpoint.DEFAULT_HOST,
    val hotkey: HotkeyConfig = HotkeyConfig()
) {
    /** Endpoint override for providers with a configurable server address. */
    val endpointOverride: String?
        get() = if (provider == ProviderKind.OLLAMA) OllamaEndpoint.baseUrl(ollamaHost) else null
}

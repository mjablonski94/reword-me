package dev.mjablonski.rewordme.models

import kotlin.random.Random
import kotlinx.serialization.Serializable

/** Legacy persisted classification retained for 1.0.1 config compatibility. */
@Serializable
enum class RuleKind { DO, DONT }

/** A literal user-written rule. [kind] is retained only for config migration. */
@Serializable
data class RewriteRule(
    val id: String = randomId(),
    val kind: RuleKind = RuleKind.DO,
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
    val provider: ProviderKind = ProviderKind.GEMINI,
    /**
     * Legacy 1.0.1 value. New writes move it into [modelsByProvider]; keeping
     * the field lets existing config files migrate without losing a choice.
     */
    val model: String? = null,
    /** Each provider owns its model selection; a missing entry means automatic. */
    val modelsByProvider: Map<String, String> = emptyMap(),
    val rules: List<RewriteRule> = emptyList(),
    val basePrompt: String = "",
    /** Where the local Ollama server listens; only used by the ollama provider. */
    val ollamaHost: String = OllamaEndpoint.DEFAULT_HOST,
    val hotkey: HotkeyConfig = HotkeyConfig(),
    /**
     * Null until the user has had a say. The app registers itself for startup
     * on first run - a tray app that is not running cannot answer its shortcut
     * - and records the answer here, so switching it off sticks.
     */
    val launchAtLogin: Boolean? = null
) {
    /** The selection belonging to [provider], including a legacy-file fallback. */
    val selectedModel: String?
        get() = modelsByProvider[provider.id]
            ?: model?.takeIf { modelsByProvider.isEmpty() && it.isNotBlank() }

    /** Binds the legacy model to its saved provider before changing provider. */
    fun selectingProvider(selected: ProviderKind): RewordConfig {
        val migrated = migratedModels()
        return copy(provider = selected, model = null, modelsByProvider = migrated)
    }

    fun selectingModel(selected: String?): RewordConfig {
        val updated = migratedModels().toMutableMap()
        val normalized = selected?.trim().orEmpty()
        if (normalized.isEmpty()) updated.remove(provider.id) else updated[provider.id] = normalized
        return copy(model = null, modelsByProvider = updated)
    }

    private fun migratedModels(): Map<String, String> {
        val legacy = model?.trim().orEmpty()
        if (legacy.isEmpty() || provider.id in modelsByProvider) return modelsByProvider
        return modelsByProvider + (provider.id to legacy)
    }

    /** Endpoint override for providers with a configurable server address. */
    val endpointOverride: String?
        get() = if (provider == ProviderKind.OLLAMA) OllamaEndpoint.baseUrl(ollamaHost) else null
}

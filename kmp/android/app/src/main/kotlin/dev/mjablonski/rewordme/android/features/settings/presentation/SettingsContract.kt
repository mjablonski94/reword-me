package dev.mjablonski.rewordme.android.features.settings.presentation

import dev.mjablonski.rewordme.android.arch.MviEffect
import dev.mjablonski.rewordme.android.arch.MviIntent
import dev.mjablonski.rewordme.android.arch.MviState
import dev.mjablonski.rewordme.android.common.UiError
import dev.mjablonski.rewordme.models.ModelInfo
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordConfig

/** Same three tabs as the desktop settings window. */
enum class SettingsTab { PROVIDER, REWRITING, GENERAL }

data class SettingsState(
    val tab: SettingsTab = SettingsTab.PROVIDER,
    val config: RewordConfig = RewordConfig(),
    /** Providers this platform can serve, in declaration order. */
    val providers: List<ProviderKind> = emptyList(),
    /** The vault never surfaces the key itself, only whether one exists. */
    val hasStoredKey: Boolean = false,
    val keyDraft: String = "",
    val keySaved: Boolean = false,
    val keySaveFailed: Boolean = false,
    val models: List<ModelInfo> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelsError: UiError? = null,
    val newRuleDraft: String = ""
) : MviState {
    /** What "Automatic" currently resolves to, once models are known. */
    val automaticModelHint: String?
        get() = models.firstOrNull()?.takeIf { config.selectedModel == null }?.id
}

sealed interface SettingsIntent : MviIntent {
    data class SelectTab(val tab: SettingsTab) : SettingsIntent
    data class SelectProvider(val provider: ProviderKind) : SettingsIntent
    data class KeyChanged(val value: String) : SettingsIntent
    data object SaveKey : SettingsIntent
    data object LoadModels : SettingsIntent
    data class SelectModel(val model: String?) : SettingsIntent
    data class OllamaHostChanged(val host: String) : SettingsIntent
    data class BasePromptChanged(val prompt: String) : SettingsIntent
    data class NewRuleChanged(val value: String) : SettingsIntent
    data object AddRule : SettingsIntent
    data class ToggleRule(val id: String, val enabled: Boolean) : SettingsIntent
    data class RuleTextChanged(val id: String, val text: String) : SettingsIntent
    data class RemoveRule(val id: String) : SettingsIntent
    data class OpenUrl(val url: String) : SettingsIntent
}

sealed interface SettingsEffect : MviEffect {
    data class OpenUrl(val url: String) : SettingsEffect
}

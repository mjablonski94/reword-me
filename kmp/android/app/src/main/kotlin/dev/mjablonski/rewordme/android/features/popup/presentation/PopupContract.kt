package dev.mjablonski.rewordme.android.features.popup.presentation

import dev.mjablonski.rewordme.android.arch.MviEffect
import dev.mjablonski.rewordme.android.arch.MviIntent
import dev.mjablonski.rewordme.android.arch.MviState
import dev.mjablonski.rewordme.android.common.UiError

/**
 * Same stages as the desktop popup, plus SETUP: on a phone the popup is the
 * first thing people reach, so an unconfigured provider is caught when it
 * opens instead of after they pick an action. EMPTY covers a host handing
 * over blank or too-short text; MENU is the entry stage otherwise.
 */
enum class PopupStage { EMPTY, SETUP, MENU, LOADING, RESULT, FAILED }

/**
 * The rewrite actions, one-to-one with the desktop presets. Instructions are
 * deliberately not localized - they are prompt text sent to the model, which
 * reasons about English instructions best. Titles and icons live in the UI.
 */
enum class PopupAction(val instruction: String?) {
    PROOFREAD(
        "Only fix grammar, spelling and punctuation. " +
            "Keep the wording and tone unchanged otherwise."
    ),
    REWRITE(null),
    FRIENDLY("Make it warmer and more friendly."),
    PROFESSIONAL("Make it more professional and polished."),
    CONCISE("Make it more concise without losing meaning.")
}

data class PopupState(
    val stage: PopupStage = PopupStage.MENU,
    val original: String = "",
    /** False when the host field is read-only: offer Copy, hide Replace. */
    val canReplace: Boolean = true,
    /** EMPTY because the selection was too short, not because it was missing. */
    val tooShort: Boolean = false,
    /**
     * The host is a browser: its fields may silently drop the returned text,
     * so Replace also puts the rewrite on the clipboard as a fallback.
     */
    val replaceAlsoCopies: Boolean = false,
    val steering: String = "",
    val result: String = "",
    val error: UiError? = null,
    /** Post-result action failure (clipboard), shown under the result. */
    val actionFailedCopy: Boolean = false,
    /** The model that served (or will serve) the request; null means automatic. */
    val modelName: String? = null,
    /** Short provider name for the header, e.g. "Gemini" or "Ollama". */
    val providerName: String = ""
) : MviState

sealed interface PopupIntent : MviIntent {
    data class Begin(
        val text: String,
        val canReplace: Boolean,
        val tooShort: Boolean = false,
        val fromBrowser: Boolean = false
    ) : PopupIntent
    data class SteeringChanged(val value: String) : PopupIntent
    data class Run(val action: PopupAction) : PopupIntent
    data object SubmitSteering : PopupIntent
    data object Again : PopupIntent
    data object Retry : PopupIntent
    data object BackToMenu : PopupIntent
    data object Replace : PopupIntent
    data object Copy : PopupIntent
    data object OpenSettings : PopupIntent
    /** The system back gesture: steps back a stage before it closes anything. */
    data object BackPressed : PopupIntent
    /** A tap on the dimmed backdrop; ignored while a result is pending or shown. */
    data object ScrimTapped : PopupIntent
    data object Dismiss : PopupIntent
}

sealed interface PopupEffect : MviEffect {
    /** Finish the activity handing the rewrite back to the host field. */
    data class FinishWithReplacement(val text: String, val alsoCopied: Boolean) : PopupEffect
    /** The rewrite is on the clipboard; older Androids show no confirmation of their own. */
    data object Copied : PopupEffect
    data object OpenSettings : PopupEffect
    data object Finish : PopupEffect
}

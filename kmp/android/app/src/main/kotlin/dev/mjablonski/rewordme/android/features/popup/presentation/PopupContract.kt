package dev.mjablonski.rewordme.android.features.popup.presentation

import dev.mjablonski.rewordme.android.arch.MviEffect
import dev.mjablonski.rewordme.android.arch.MviIntent
import dev.mjablonski.rewordme.android.arch.MviState
import dev.mjablonski.rewordme.android.common.UiError

/**
 * Same stages as the desktop popup. EMPTY exists for the defensive case of a
 * host app handing over blank text; MENU is the entry stage otherwise.
 */
enum class PopupStage { EMPTY, MENU, LOADING, RESULT, FAILED }

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
    val steering: String = "",
    val result: String = "",
    val error: UiError? = null,
    /** Post-result action failure (clipboard), shown under the result. */
    val actionFailedCopy: Boolean = false,
    val modelLabel: String = ""
) : MviState

sealed interface PopupIntent : MviIntent {
    data class Begin(val text: String, val canReplace: Boolean) : PopupIntent
    data class SteeringChanged(val value: String) : PopupIntent
    data class Run(val action: PopupAction) : PopupIntent
    data object SubmitSteering : PopupIntent
    data object Again : PopupIntent
    data object Retry : PopupIntent
    data object BackToMenu : PopupIntent
    data object Replace : PopupIntent
    data object Copy : PopupIntent
    data object Dismiss : PopupIntent
}

sealed interface PopupEffect : MviEffect {
    /** Finish the activity handing the rewrite back to the host field. */
    data class FinishWithReplacement(val text: String) : PopupEffect
    data object Finish : PopupEffect
}

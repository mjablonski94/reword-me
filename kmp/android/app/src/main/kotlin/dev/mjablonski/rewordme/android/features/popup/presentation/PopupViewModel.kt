package dev.mjablonski.rewordme.android.features.popup.presentation

import androidx.lifecycle.viewModelScope
import dev.mjablonski.rewordme.android.arch.MviViewModel
import dev.mjablonski.rewordme.android.common.UiError
import dev.mjablonski.rewordme.android.platform.Clipboard
import dev.mjablonski.rewordme.domain.usecase.LoadConfigUseCase
import dev.mjablonski.rewordme.domain.usecase.RewordParams
import dev.mjablonski.rewordme.domain.usecase.RewordTextUseCase
import dev.mjablonski.rewordme.models.LocalModelCatalog
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The desktop PopupViewModel's behaviour behind the MVI contract:
 * menu-first, nothing is generated until the user picks an action, and a
 * newer request always invalidates a slower older one.
 */
class PopupViewModel(
    private val rewordText: RewordTextUseCase,
    private val loadConfig: LoadConfigUseCase,
    private val clipboard: Clipboard
) : MviViewModel<PopupState, PopupIntent, PopupEffect>(PopupState()) {

    private var generation: Job? = null
    private var generationId = 0L
    private var lastInstruction: String? = null
    private var started = false

    override fun onIntent(intent: PopupIntent) {
        when (intent) {
            is PopupIntent.Begin -> begin(intent.text, intent.canReplace)
            is PopupIntent.SteeringChanged -> setState { copy(steering = intent.value) }
            is PopupIntent.Run -> run(intent.action.instruction ?: steeringOrNull())
            PopupIntent.SubmitSteering ->
                if (currentState.steering.isNotBlank()) run(steeringOrNull())
            PopupIntent.Again -> again()
            PopupIntent.Retry -> run(lastInstruction)
            PopupIntent.BackToMenu -> backToMenu()
            PopupIntent.Replace -> replace()
            PopupIntent.Copy -> copy()
            PopupIntent.Dismiss -> dismiss()
        }
    }

    /** Re-delivered on every activity recreation; only the first one counts. */
    private fun begin(text: String, canReplace: Boolean) {
        if (started) return
        started = true
        lastInstruction = null
        setState {
            copy(
                stage = if (text.isEmpty()) PopupStage.EMPTY else PopupStage.MENU,
                original = text,
                canReplace = canReplace
            )
        }
        viewModelScope.launch {
            val config = loadConfig.safeAction(Unit).getOrNull() ?: return@launch
            setState { copy(modelLabel = modelLabel(config, null)) }
        }
    }

    private fun steeringOrNull(): String? =
        currentState.steering.trim().ifEmpty { null }

    /** Re-run with newly typed steering, or repeat the prior instruction. */
    private fun again() {
        val instruction = instructionForAgain(currentState.steering, lastInstruction)
        run(instruction)
    }

    private fun run(instruction: String?) {
        cancelGeneration()
        lastInstruction = instruction
        val requestId = generationId
        setState {
            copy(stage = PopupStage.LOADING, error = null, actionFailedCopy = false)
        }
        generation = viewModelScope.launch {
            val outcome = rewordText.safeAction(
                RewordParams(currentState.original, instruction)
            )
            if (requestId != generationId) return@launch
            outcome.fold(
                onSuccess = { generated ->
                    setState {
                        copy(
                            stage = PopupStage.RESULT,
                            result = generated.text,
                            modelLabel = modelLabel(generated.config, generated.model)
                        )
                    }
                },
                onFailure = { failure ->
                    setState { copy(stage = PopupStage.FAILED, error = UiError.of(failure)) }
                }
            )
        }
    }

    private fun backToMenu() {
        cancelGeneration()
        setState {
            copy(
                stage = if (original.isEmpty()) PopupStage.EMPTY else PopupStage.MENU,
                error = null,
                actionFailedCopy = false
            )
        }
    }

    private fun replace() {
        val result = currentState.result
        if (result.isNotEmpty()) sendEffect(PopupEffect.FinishWithReplacement(result))
    }

    private fun copy() {
        val result = currentState.result
        if (result.isEmpty()) return
        if (clipboard.copy(result)) {
            dismiss()
        } else {
            setState { copy(actionFailedCopy = true) }
        }
    }

    private fun dismiss() {
        cancelGeneration()
        sendEffect(PopupEffect.Finish)
    }

    private fun cancelGeneration() {
        generation?.cancel()
        generation = null
        generationId++
    }

    private fun modelLabel(config: RewordConfig, resolvedModel: String?): String {
        if (config.provider == ProviderKind.LOCAL) {
            return LocalModelCatalog.model(resolvedModel ?: config.selectedModel).displayName
        }
        val model = resolvedModel ?: config.selectedModel ?: "Automatic"
        return "$model · ${config.provider.displayName}"
    }
}

internal fun instructionForAgain(steering: String, previous: String?): String? {
    val edited = steering.trim()
    return if (edited.isNotEmpty() && edited != previous) edited else previous
}

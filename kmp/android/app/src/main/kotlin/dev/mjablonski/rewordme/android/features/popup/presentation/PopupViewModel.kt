package dev.mjablonski.rewordme.android.features.popup.presentation

import androidx.lifecycle.viewModelScope
import dev.mjablonski.rewordme.android.arch.MviViewModel
import dev.mjablonski.rewordme.android.common.UiError
import dev.mjablonski.rewordme.android.platform.Clipboard
import dev.mjablonski.rewordme.domain.usecase.HasApiKeyUseCase
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
    private val hasApiKey: HasApiKeyUseCase,
    private val clipboard: Clipboard
) : MviViewModel<PopupState, PopupIntent, PopupEffect>(PopupState()) {

    private var generation: Job? = null
    private var generationId = 0L
    private var lastInstruction: String? = null
    private var started = false

    override fun onIntent(intent: PopupIntent) {
        when (intent) {
            is PopupIntent.Begin -> begin(intent)
            is PopupIntent.SteeringChanged -> setState { copy(steering = intent.value) }
            is PopupIntent.Run -> run(intent.action.instruction ?: steeringOrNull())
            PopupIntent.SubmitSteering ->
                if (currentState.steering.isNotBlank()) run(steeringOrNull())
            PopupIntent.Again -> again()
            PopupIntent.Retry -> run(lastInstruction)
            PopupIntent.BackToMenu -> backToMenu()
            PopupIntent.Replace -> replace()
            PopupIntent.Copy -> copy()
            PopupIntent.OpenSettings -> {
                cancelGeneration()
                sendEffect(PopupEffect.OpenSettings)
            }
            PopupIntent.BackPressed -> when (currentState.stage) {
                PopupStage.LOADING, PopupStage.RESULT, PopupStage.FAILED -> backToMenu()
                else -> dismiss()
            }
            // A stray tap next to the card must not throw away a request the
            // user is waiting for, or a result that may have cost money.
            PopupIntent.ScrimTapped -> when (currentState.stage) {
                PopupStage.LOADING, PopupStage.RESULT -> Unit
                else -> dismiss()
            }
            PopupIntent.Dismiss -> dismiss()
        }
    }

    /** Re-delivered on every activity recreation; only the first one counts. */
    private fun begin(intent: PopupIntent.Begin) {
        if (started) return
        started = true
        lastInstruction = null
        setState {
            copy(
                stage = if (intent.text.isEmpty()) PopupStage.EMPTY else PopupStage.MENU,
                original = intent.text,
                canReplace = intent.canReplace,
                tooShort = intent.tooShort,
                replaceAlsoCopies = intent.fromBrowser && intent.canReplace
            )
        }
        viewModelScope.launch {
            val config = loadConfig.safeAction(Unit).getOrNull() ?: return@launch
            val ready = !config.provider.requiresApiKey ||
                hasApiKey.safeAction(config.provider).getOrDefault(false)
            setState {
                copy(
                    modelName = modelName(config, null),
                    providerName = providerName(config.provider),
                    // Only from the untouched menu: an action already running
                    // reports the missing key through its own error.
                    stage = if (!ready && stage == PopupStage.MENU) PopupStage.SETUP else stage
                )
            }
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
                            result = withoutReasoning(generated.text),
                            modelName = modelName(generated.config, generated.model),
                            providerName = providerName(generated.config.provider)
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
        if (result.isEmpty()) return
        // Browsers can accept the result and still leave the field as it was;
        // with the rewrite on the clipboard the user can always paste it.
        val copied = currentState.replaceAlsoCopies && clipboard.copy(result)
        sendEffect(PopupEffect.FinishWithReplacement(result, alsoCopied = copied))
    }

    private fun copy() {
        val result = currentState.result
        if (result.isEmpty()) return
        if (clipboard.copy(result)) {
            sendEffect(PopupEffect.Copied)
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

    private fun modelName(config: RewordConfig, resolvedModel: String?): String? {
        if (config.provider == ProviderKind.LOCAL) {
            return LocalModelCatalog.model(resolvedModel ?: config.selectedModel).displayName
        }
        return resolvedModel ?: config.selectedModel
    }
}

/** "Gemini (Recommended)" is a settings label; the cramped header needs "Gemini". */
internal fun providerName(provider: ProviderKind): String =
    provider.displayName.substringBefore(" (")

/**
 * Reasoning models served through OpenAI-style endpoints can prefix the answer
 * with a <think> block; Replace must never paste that into someone's message.
 */
internal fun withoutReasoning(text: String): String {
    val stripped = text.replace(Regex("(?s)<think>.*?</think>"), "").trim()
    return stripped.ifEmpty { text }
}

internal fun instructionForAgain(steering: String, previous: String?): String? {
    val edited = steering.trim()
    return if (edited.isNotEmpty() && edited != previous) edited else previous
}

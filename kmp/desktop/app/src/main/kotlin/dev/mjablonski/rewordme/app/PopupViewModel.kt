package dev.mjablonski.rewordme.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BusinessCenter
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import dev.mjablonski.rewordme.domain.PromptBuilder
import dev.mjablonski.rewordme.models.RewordError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * View model behind the popup. Menu-first: nothing is generated until
 * the user picks an action.
 */
class PopupViewModel(
    private val dependencies: AppDependencies,
    private val scope: CoroutineScope
) {
    enum class Stage { EMPTY, MENU, LOADING, RESULT, FAILED }

    data class Preset(val title: String, val icon: ImageVector, val instruction: String)

    var original by mutableStateOf("")
        private set

    // Internal rather than private so the offscreen snapshot renderer can pose
    // every stage without a live provider behind it.
    var stage by mutableStateOf(Stage.MENU)
        internal set
    var result by mutableStateOf("")
        internal set
    var errorMessage by mutableStateOf("")
        internal set
    var actionErrorMessage by mutableStateOf("")
        private set
    var modelLabel by mutableStateOf("")
        internal set
    var steering by mutableStateOf("")

    var onReplace: ((String) -> Unit)? = null
    var onClose: (() -> Unit)? = null

    private var generation: Job? = null
    private var generationId = 0L

    fun begin(text: String) {
        cancelGeneration()
        original = text
        steering = ""
        result = ""
        errorMessage = ""
        actionErrorMessage = ""
        lastInstruction = null
        stage = if (text.isEmpty()) Stage.EMPTY else Stage.MENU
    }

    fun reword(instruction: String? = null) {
        val effective = instruction ?: steering.trim().ifEmpty { null }
        lastInstruction = effective
        run(effective)
    }

    fun submitSteering() {
        if (steering.isNotBlank()) reword()
    }

    fun regenerate() = run(lastInstruction)

    /** Re-run with newly typed steering, or repeat the prior instruction. */
    fun again() {
        val instruction = instructionForAgain(steering, lastInstruction)
        lastInstruction = instruction
        run(instruction)
    }

    fun backToMenu() {
        cancelGeneration()
        actionErrorMessage = ""
        stage = if (original.isEmpty()) Stage.EMPTY else Stage.MENU
    }

    fun dismiss() {
        cancelGeneration()
        onClose?.invoke()
    }

    /** Shown in the empty state so the user knows which keys to press. */
    val shortcutDisplay: String get() = dependencies.configStore.load().hotkey.display

    private var lastInstruction: String? = null

    private fun run(instruction: String?) {
        cancelGeneration()
        val requestId = generationId
        actionErrorMessage = ""
        errorMessage = ""
        stage = Stage.LOADING
        val config = dependencies.configStore.load()

        generation = scope.launch {
            try {
                val generated = withContext(Dispatchers.IO) {
                    val apiKey = if (config.provider.requiresApiKey) {
                        dependencies.keyStore.apiKey(config.provider)
                            ?: throw RewordError.MissingApiKey
                    } else {
                        ""
                    }
                    val model = dependencies.modelResolver.model(
                        config, apiKey, dependencies.rewordService
                    )
                    val systemPrompt = PromptBuilder.systemPrompt(
                        config.rules, config.basePrompt, instruction
                    )
                    val reworded = dependencies.rewordService.reword(
                        config.provider, apiKey, model, systemPrompt, original,
                        config.endpointOverride
                    )
                    reworded to model
                }
                if (requestId != generationId) return@launch
                result = generated.first
                modelLabel = "${config.provider.displayName} - ${generated.second}"
                stage = Stage.RESULT
            } catch (error: RewordError) {
                if (requestId != generationId) return@launch
                errorMessage = error.localized()
                stage = Stage.FAILED
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                if (requestId != generationId) return@launch
                errorMessage = error.message ?: Strings["popup.errorTitle"]
                stage = Stage.FAILED
            }
        }
    }

    private fun cancelGeneration() {
        generation?.cancel()
        generation = null
        generationId++
    }

    fun replace() {
        if (result.isNotEmpty()) onReplace?.invoke(result)
    }

    /** Ignore a late failure if a newer hotkey invocation replaced this result. */
    fun replacementFailed(replacement: String): Boolean {
        if (stage != Stage.RESULT || result != replacement) return false
        actionErrorMessage = Strings["error.replaceFailed"]
        return true
    }

    fun copy() {
        if (result.isEmpty()) return
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        var copied = false
        for (attempt in 0 until 10) {
            try {
                clipboard.setContents(java.awt.datatransfer.StringSelection(result), null)
                copied = true
                break
            } catch (_: IllegalStateException) {
                if (attempt < 9) Thread.sleep(25)
            } catch (_: Exception) {
                break
            }
        }
        if (copied) dismiss() else actionErrorMessage = Strings["error.copyFailed"]
    }

    companion object {
        // Titles are localized; instructions are not - they are prompt text
        // sent to the model, which reasons about English instructions best.
        val proofread = Preset(
            Strings["popup.proofread"], Icons.AutoMirrored.Rounded.ManageSearch,
            "Only fix grammar, spelling and punctuation. Keep the wording and tone unchanged otherwise."
        )
        val rewrite = Preset(Strings["popup.rewrite"], Icons.Rounded.Autorenew, "")
        val tonePresets = listOf(
            Preset(
                Strings["popup.friendly"], Icons.Rounded.Mood,
                "Make it warmer and more friendly."
            ),
            Preset(
                Strings["popup.professional"], Icons.Rounded.BusinessCenter,
                "Make it more professional and polished."
            ),
            Preset(
                Strings["popup.concise"], Icons.AutoMirrored.Rounded.ShortText,
                "Make it more concise without losing meaning."
            )
        )
    }
}

internal fun instructionForAgain(steering: String, previous: String?): String? {
    val edited = steering.trim()
    return if (edited.isNotEmpty() && edited != previous) edited else previous
}

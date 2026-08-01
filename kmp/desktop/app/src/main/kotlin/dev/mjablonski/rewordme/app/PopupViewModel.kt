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
    var modelLabel by mutableStateOf("")
        internal set
    var steering by mutableStateOf("")

    var onReplace: ((String) -> Unit)? = null
    var onClose: (() -> Unit)? = null

    private var generation: Job? = null

    fun begin(text: String) {
        generation?.cancel()
        original = text
        steering = ""
        result = ""
        stage = if (text.isEmpty()) Stage.EMPTY else Stage.MENU
    }

    fun reword(instruction: String? = null) {
        val effective = instruction ?: steering.trim().ifEmpty { null }
        lastInstruction = effective
        run(effective)
    }

    fun regenerate() = run(lastInstruction)

    fun backToMenu() {
        generation?.cancel()
        stage = if (original.isEmpty()) Stage.EMPTY else Stage.MENU
    }

    /** Shown in the empty state so the user knows which keys to press. */
    val shortcutDisplay: String get() = dependencies.configStore.load().hotkey.display

    private var lastInstruction: String? = null

    private fun run(instruction: String?) {
        generation?.cancel()
        stage = Stage.LOADING
        val config = dependencies.configStore.load()

        generation = scope.launch(Dispatchers.IO) {
            try {
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
                result = reworded
                modelLabel = "${config.provider.displayName} - $model"
                stage = Stage.RESULT
            } catch (error: RewordError) {
                errorMessage = error.localized()
                stage = Stage.FAILED
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                errorMessage = error.message ?: Strings["popup.errorTitle"]
                stage = Stage.FAILED
            }
        }
    }

    fun replace() {
        if (result.isNotEmpty()) onReplace?.invoke(result)
    }

    fun copy() {
        if (result.isEmpty()) return
        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
            java.awt.datatransfer.StringSelection(result), null
        )
        onClose?.invoke()
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

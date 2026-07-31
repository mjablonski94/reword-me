package dev.mjablonski.rewordme.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    enum class Stage { MENU, LOADING, RESULT, FAILED }

    data class Preset(val title: String, val icon: String, val instruction: String)

    var original by mutableStateOf("")
        private set
    var stage by mutableStateOf(Stage.MENU)
        private set
    var result by mutableStateOf("")
        private set
    var errorMessage by mutableStateOf("")
        private set
    var modelLabel by mutableStateOf("")
        private set
    var steering by mutableStateOf("")

    var onReplace: ((String) -> Unit)? = null
    var onClose: (() -> Unit)? = null

    private var generation: Job? = null

    fun begin(text: String) {
        generation?.cancel()
        original = text
        steering = ""
        result = ""
        stage = Stage.MENU
    }

    fun reword(instruction: String? = null) {
        val effective = instruction ?: steering.trim().ifEmpty { null }
        lastInstruction = effective
        run(effective)
    }

    fun regenerate() = run(lastInstruction)

    fun backToMenu() {
        generation?.cancel()
        stage = Stage.MENU
    }

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
                errorMessage = error.message ?: "Something went wrong"
                stage = Stage.FAILED
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                errorMessage = error.message ?: "Something went wrong"
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
        val proofread = Preset(
            "Proofread", "search",
            "Only fix grammar, spelling and punctuation. Keep the wording and tone unchanged otherwise."
        )
        val rewrite = Preset("Rewrite", "refresh", "")
        val tonePresets = listOf(
            Preset("Friendly", "smile", "Make it warmer and more friendly."),
            Preset("Professional", "case", "Make it more professional and polished."),
            Preset("Concise", "minus", "Make it more concise without losing meaning.")
        )
    }
}

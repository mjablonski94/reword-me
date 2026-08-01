package dev.mjablonski.rewordme.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import dev.mjablonski.rewordme.models.RewordConfig
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Renders every screen offscreen to a PNG so the layout can be reviewed
 * without a running app, a provider key or a live selection.
 *
 * `./gradlew :desktop:app:renderUi`
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "ui-snapshots").apply { mkdirs() }
    val dependencies = AppDependencies()
    val scope = CoroutineScope(Dispatchers.Default)

    fun popup(name: String, pose: PopupViewModel.() -> Unit) {
        val viewModel = PopupViewModel(dependencies, scope).apply(pose)
        snapshot(out, "popup-$name", 420, 520) {
            Box(Modifier.background(Color(0xB3121218), RoundedCornerShape(12.dp))) {
                PopupContent(viewModel)
            }
        }
    }

    popup("empty") { begin("") }
    popup("menu") { begin(SAMPLE) }
    popup("loading") {
        begin(SAMPLE)
        stage = PopupViewModel.Stage.LOADING
    }
    popup("result") {
        begin(SAMPLE)
        result = REWORDED
        modelLabel = "Gemini - gemini-3.5-flash-lite"
        stage = PopupViewModel.Stage.RESULT
    }
    popup("failed") {
        begin(SAMPLE)
        errorMessage = "No API key saved for Gemini."
        stage = PopupViewModel.Stage.FAILED
    }

    val settings = SettingsViewModel(dependencies, scope, NoHotkeys)
    SettingsTab.entries.forEach { tab ->
        snapshot(out, "settings-${tab.name.lowercase()}", 576, 536) {
            // Pinned to the real window size: the screen fills its window, so
            // an unconstrained render says nothing about the actual layout.
            Box(Modifier.size(560.dp, 520.dp)) { SettingsContent(settings, tab) }
        }
    }

    println("wrote ${out.absolutePath}")
}

@OptIn(ExperimentalComposeUiApi::class)
private fun snapshot(out: File, name: String, width: Int, height: Int, content: @Composable () -> Unit) {
    ImageComposeScene(width, height, Density(1f)) {
        // A busy backdrop, because the popup is translucent and a flat fill
        // would hide contrast problems the acrylic version really has.
        Box(
            Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF7A8CA8), Color(0xFF2A2530))))
                .padding(8.dp)
        ) {
            content()
        }
    }.use { scene ->
        val size = scene.calculateContentSize()
        println("$name content=${size.width}x${size.height} frame=${width}x$height")
        File(out, "$name.png").writeBytes(scene.render().encodeToData()!!.bytes)
    }
}

private object NoHotkeys : HotkeyBinder {
    override fun bind(config: RewordConfig) = HotkeyStatus.Active
    override fun release() = Unit
}

internal const val SAMPLE =
    "hey so i was thinkin maybe we could push the deadline back a bit becuase " +
        "the api stuff isnt done yet and i dont wanna ship somethin broken"

internal const val REWORDED =
    "Hi, I was thinking we might push the deadline back slightly. The API work " +
        "isn't finished yet, and I'd rather not ship something broken."

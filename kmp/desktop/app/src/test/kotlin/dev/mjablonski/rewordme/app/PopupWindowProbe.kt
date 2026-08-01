package dev.mjablonski.rewordme.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.delay

/**
 * Drives the real popup window through every stage and prints the frame the
 * window actually settled on. The offscreen renderer measures content only, so
 * this is the one thing that can tell whether the frame follows it.
 *
 * `./gradlew :desktop:app:probePopupWindow`
 */
fun main(args: Array<String>) = application {
    val out = File(args.firstOrNull() ?: "popup-frames").apply { mkdirs() }
    val dependencies = remember { AppDependencies() }
    val scope = rememberCoroutineScope()
    val viewModel = remember { PopupViewModel(dependencies, scope) }
    val state = rememberWindowState(
        position = WindowPosition(200.dp, 200.dp),
        size = DpSize.Unspecified
    )

    PopupWindow(viewModel, state, visible = true) {}

    LaunchedEffect(Unit) {
        // Largest stage first: shrinking is the direction that used to leave
        // an empty pane hanging off the panel.
        val stages = listOf(
            PopupViewModel.Stage.MENU,
            PopupViewModel.Stage.EMPTY,
            PopupViewModel.Stage.RESULT,
            PopupViewModel.Stage.FAILED,
            PopupViewModel.Stage.LOADING,
            PopupViewModel.Stage.MENU
        )
        viewModel.begin(SAMPLE)
        viewModel.result = REWORDED
        viewModel.modelLabel = "Gemini - gemini-3.5-flash-lite"
        viewModel.errorMessage = "No API key saved for Gemini."
        for (stage in stages) {
            viewModel.stage = stage
            delay(900)
            println("PROBE stage=$stage frame=${state.size}")
            capture(out, "frame-${stage.name.lowercase()}")
        }
        checkMovable(out, viewModel, state)
        exitApplication()
    }
}

/**
 * Relocates the window the way WindowDraggableArea does, then changes stage, to
 * prove the frame-follows-content binding does not pull the window back.
 */
private suspend fun checkMovable(out: File, viewModel: PopupViewModel, state: WindowState) {
    val window = java.awt.Window.getWindows().firstOrNull { it.isShowing } ?: return
    val start = window.location
    println("PROBE move from=(${start.x},${start.y}) state=${state.position}")

    window.setLocation(start.x + 160, start.y + 110)
    delay(700)
    println("PROBE moved to=(${window.location.x},${window.location.y}) state=${state.position}")
    capture(out, "frame-moved")

    for (stage in listOf(
        PopupViewModel.Stage.RESULT,
        PopupViewModel.Stage.FAILED,
        PopupViewModel.Stage.MENU
    )) {
        viewModel.stage = stage
        delay(700)
        println(
            "PROBE after $stage at=(${window.location.x},${window.location.y}) " +
                "size=${window.width}x${window.height} state=${state.position}"
        )
    }
    capture(out, "frame-moved-then-resized")
}

/**
 * Grabs the popup off the screen with a margin, so the acrylic backdrop and
 * any slack between the panel and its frame are both visible.
 */
private fun capture(out: File, name: String) {
    val window = java.awt.Window.getWindows().firstOrNull { it.isShowing } ?: return
    val bounds = window.bounds
    val margin = 24
    val region = Rectangle(
        bounds.x - margin, bounds.y - margin,
        bounds.width + margin * 2, bounds.height + margin * 2
    )
    val shot = Robot().createScreenCapture(region)
    ImageIO.write(shot, "png", File(out, "$name.png"))
    println("  captured $name window=${bounds.width}x${bounds.height}")
}

package dev.mjablonski.rewordme.app

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.mjablonski.rewordme.domain.SelectionFilter
import dev.mjablonski.rewordme.platform.GlobalHotkey
import dev.mjablonski.rewordme.platform.WindowEffects
import dev.mjablonski.rewordme.platform.isWindows
import java.awt.Desktop
import java.awt.MouseInfo
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

fun main() = application {
    val dependencies = remember { AppDependencies() }
    val scope = rememberCoroutineScope()
    val viewModel = remember { PopupViewModel(dependencies, scope) }

    var popupVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    val popupState = rememberWindowState(
        position = WindowPosition(100.dp, 100.dp),
        size = DpSize.Unspecified
    )

    fun showPopupAtCursor() {
        val mouse = MouseInfo.getPointerInfo()?.location
        if (mouse != null) {
            popupState.position = WindowPosition((mouse.x + 12).dp, (mouse.y + 12).dp)
        }
        popupVisible = true
    }

    fun triggerReword() {
        scope.launch {
            dependencies.foreground.remember()
            val text = withContext(Dispatchers.IO) {
                dependencies.selectionReader.readSelection()
            }
            withContext(Dispatchers.Swing) {
                viewModel.begin(if (text != null && SelectionFilter.isMeaningful(text)) text else "")
                showPopupAtCursor()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.onClose = { popupVisible = false }
        viewModel.onReplace = { replacement ->
            popupVisible = false
            scope.launch(Dispatchers.IO) {
                dependencies.textReplacer.replaceSelection(replacement)
            }
        }
        val hotkey = GlobalHotkey()
        hotkey.register(dependencies.configStore.load().hotkey) {
            scope.launch(Dispatchers.Swing) { triggerReword() }
        }
    }

    Tray(
        icon = remember { trayIcon() },
        tooltip = "RewordMe",
        menu = {
            Item("Reword Selection") { triggerReword() }
            Item("Settings...") { settingsVisible = true }
            Separator()
            Item("Buy Me a Coffee") {
                runCatching { Desktop.getDesktop().browse(URI("https://buymeacoffee.com/kofcio94f")) }
            }
            Item("Quit RewordMe") { exitApplication() }
        }
    )

    // Pre-warmed popup: the window exists from launch and is only
    // shown/hidden, so the first hotkey press pays no composition cost.
    Window(
        onCloseRequest = { popupVisible = false },
        visible = popupVisible,
        state = popupState,
        title = "RewordMe",
        undecorated = true,
        transparent = false,
        resizable = false,
        alwaysOnTop = true,
        focusable = true
    ) {
        val acrylic = remember {
            WindowEffects.applyPopupChrome(window, dark = true)
        }
        val fallback = if (acrylic) Color(0x66101018) else Color(0xF2181820)
        androidx.compose.foundation.layout.Box(
            Modifier.background(fallback, RoundedCornerShape(if (isWindows) 0.dp else 12.dp))
        ) {
            PopupContent(viewModel)
        }
    }

    if (settingsVisible) {
        Window(
            onCloseRequest = { settingsVisible = false },
            title = "RewordMe Settings",
            state = rememberWindowState(size = DpSize(520.dp, 420.dp))
        ) {
            SettingsContent(dependencies)
        }
    }
}

/** Simple purple round tray glyph drawn in code (no asset pipeline yet). */
private fun trayIcon(): BitmapPainter {
    val size = 32
    val bitmap = ImageBitmap(size, size)
    val canvas = ComposeCanvas(bitmap)
    val paint = Paint().apply { color = Color(0xFF8B7CF6) }
    canvas.drawCircle(
        androidx.compose.ui.geometry.Offset(size / 2f, size / 2f),
        size / 2.4f,
        paint
    )
    return BitmapPainter(bitmap)
}

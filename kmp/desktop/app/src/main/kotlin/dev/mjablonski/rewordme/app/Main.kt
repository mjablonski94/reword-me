package dev.mjablonski.rewordme.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.mjablonski.rewordme.domain.SelectionFilter
import dev.mjablonski.rewordme.models.RewordConfig
import dev.mjablonski.rewordme.platform.GlobalHotkey
import dev.mjablonski.rewordme.platform.HotkeyResult
import dev.mjablonski.rewordme.platform.WindowEffects
import java.awt.Desktop
import java.awt.MouseInfo
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

fun main() = application {
    val dependencies = remember { AppDependencies() }
    DisposableEffect(dependencies) {
        onDispose { dependencies.localModelManager.shutdown() }
    }
    val scope = rememberCoroutineScope()
    val viewModel = remember { PopupViewModel(dependencies, scope) }

    var popupVisible by remember { mutableStateOf(false) }
    var settingsVisible by remember { mutableStateOf(false) }
    val popupState = rememberWindowState(
        position = WindowPosition(100.dp, 100.dp),
        size = DpSize.Unspecified
    )
    val captureInProgress = remember { AtomicBoolean(false) }

    fun showPopupAtCursor() {
        val mouse = MouseInfo.getPointerInfo()?.location
        if (mouse != null) {
            popupState.position = WindowPosition((mouse.x + 12).dp, (mouse.y + 12).dp)
        }
        popupVisible = true
    }

    fun triggerReword() {
        if (!captureInProgress.compareAndSet(false, true)) return
        scope.launch {
            try {
                dependencies.foreground.remember()
                val text = withContext(Dispatchers.IO) {
                    dependencies.selectionReader.readSelection()
                }
                withContext(Dispatchers.Swing) {
                    viewModel.begin(if (text != null && SelectionFilter.isMeaningful(text)) text else "")
                    showPopupAtCursor()
                }
            } finally {
                captureInProgress.set(false)
            }
        }
    }

    val hotkey = remember { GlobalHotkey() }
    val binder = remember {
        object : HotkeyBinder {
            override fun bind(config: RewordConfig): HotkeyStatus =
                when (hotkey.register(config.hotkey) {
                    scope.launch(Dispatchers.Swing) { triggerReword() }
                }) {
                    HotkeyResult.Registered -> HotkeyStatus.Active
                    HotkeyResult.Conflict -> HotkeyStatus.Conflict
                    HotkeyResult.Unsupported -> HotkeyStatus.Unsupported
                    is HotkeyResult.Failed -> HotkeyStatus.Failed
                }

            override fun release() = hotkey.stop()
        }
    }
    val settingsViewModel = remember { SettingsViewModel(dependencies, scope, binder) }

    LaunchedEffect(Unit) {
        viewModel.onClose = { popupVisible = false }
        viewModel.onReplace = { replacement ->
            popupVisible = false
            scope.launch(Dispatchers.IO) {
                val replaced = dependencies.textReplacer.replaceSelection(replacement)
                if (!replaced) {
                    withContext(Dispatchers.Swing) {
                        if (viewModel.replacementFailed(replacement)) popupVisible = true
                    }
                }
            }
        }
        val status = binder.bind(dependencies.configStore.load())
        settingsViewModel.reportHotkeyStatus(status)
        // Without a working shortcut there is no way into the app, so open
        // settings on the conflict rather than failing silently.
        if (status == HotkeyStatus.Conflict || status == HotkeyStatus.Failed) {
            settingsVisible = true
        }
    }

    // 32px: the size Windows asks the tray for at 100% scaling.
    val trayIcon = remember { AppIcon.render(32).toPainter() }
    val windowIcon = remember { AppIcon.render(256).toPainter() }

    Tray(
        icon = trayIcon,
        tooltip = "RewordMe",
        menu = {
            Item(Strings["menu.reword"]) { triggerReword() }
            Item(Strings["menu.settings"]) { settingsVisible = true }
            Separator()
            Item(Strings["menu.buyCoffee"]) {
                runCatching { Desktop.getDesktop().browse(URI("https://buymeacoffee.com/kofcio94f")) }
            }
            Item(Strings["menu.quit"]) { exitApplication() }
        }
    )

    // Pre-warmed popup: the window exists from launch and is only
    // shown/hidden, so the first hotkey press pays no composition cost.
    PopupWindow(
        viewModel = viewModel,
        state = popupState,
        visible = popupVisible,
        onCloseRequest = viewModel::dismiss
    )

    if (settingsVisible) {
        Window(
            onCloseRequest = {
                settingsViewModel.cancelRecording()
                settingsVisible = false
            },
            title = Strings["settings.windowTitle"],
            icon = windowIcon,
            // Match the 580x560 macOS content area; the extra 32 is the
            // Windows title bar, which counts towards the frame here.
            resizable = false,
            state = rememberWindowState(size = DpSize(580.dp, 592.dp))
        ) {
            LaunchedEffect(Unit) { WindowEffects.applyWindowChrome(window, dark = true) }
            SettingsContent(settingsViewModel)
        }
    }
}

package dev.mjablonski.rewordme.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import dev.mjablonski.rewordme.platform.WindowEffects

/**
 * The floating panel window. Undecorated and transparent so DWM can paint
 * the acrylic backdrop behind the rounded panel.
 */
@Composable
fun PopupWindow(
    viewModel: PopupViewModel,
    state: WindowState,
    visible: Boolean,
    onCloseRequest: () -> Unit
) {
    Window(
        onCloseRequest = onCloseRequest,
        visible = visible,
        state = state,
        title = "RewordMe",
        undecorated = true,
        transparent = true,
        resizable = false,
        alwaysOnTop = true,
        focusable = true
    ) {
        // The backdrop only shows through a transparent surface, so the
        // chrome has to be applied after the window has a real HWND.
        var acrylic by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            acrylic = WindowEffects.applyPopupChrome(window, dark = true)
        }
        val density = LocalDensity.current
        // Unbounded so the panel measures at its natural size instead of the
        // frame's. Feeding a measured size back into the state that constrains
        // it otherwise latches the window at whichever stage was smallest.
        Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)) {
            Box(
                Modifier
                    // The frame has to follow the panel as stages change. DWM paints
                    // the acrylic and the rounded corners over the whole window, so
                    // any slack shows up as an empty pane hanging off the panel.
                    .onSizeChanged { size ->
                        with(density) {
                            state.size = DpSize(size.width.toDp(), size.height.toDp())
                        }
                    }
                    .background(
                        // Enough tint that white text stays legible when the
                        // window behind the popup is light.
                        if (acrylic) Color(0xB3121218) else Color(0xF2181820),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                PopupContent(viewModel) { header ->
                    WindowDraggableArea { header() }
                }
            }
        }
    }
}

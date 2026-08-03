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
import java.awt.Insets
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import javax.swing.SwingUtilities

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
        LaunchedEffect(visible) {
            if (visible) {
                val pointerScreen = runCatching {
                    MouseInfo.getPointerInfo()?.device?.defaultConfiguration
                }.getOrNull()
                SwingUtilities.invokeLater { clampPopupToVisibleScreen(window, pointerScreen) }
            }
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
                        if (visible) {
                            SwingUtilities.invokeLater { clampPopupToVisibleScreen(window) }
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

/** Keeps a cursor-anchored popup fully inside the current monitor's work area. */
private fun clampPopupToVisibleScreen(
    window: java.awt.Window,
    preferredScreen: java.awt.GraphicsConfiguration? = null
) {
    if (window.width <= 0 || window.height <= 0) return
    // Pointer screen is used only for the initial cursor-anchored placement.
    // Later content resizes stay on the window's own monitor, even if the user
    // moved the mouse elsewhere while a provider request was running.
    val configuration = preferredScreen ?: window.graphicsConfiguration
    val insets = runCatching {
        Toolkit.getDefaultToolkit().getScreenInsets(configuration)
    }.getOrDefault(Insets(0, 0, 0, 0))
    val point = clampedPopupLocation(
        window.x,
        window.y,
        window.width,
        window.height,
        configuration.bounds,
        insets
    )
    if (point.x != window.x || point.y != window.y) window.setLocation(point)
}

internal fun clampedPopupLocation(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    screen: Rectangle,
    insets: Insets
): Point {
    val left = screen.x + insets.left
    val top = screen.y + insets.top
    val right = screen.x + screen.width - insets.right
    val bottom = screen.y + screen.height - insets.bottom
    val maxX = (right - width).coerceAtLeast(left)
    val maxY = (bottom - height).coerceAtLeast(top)
    return Point(x.coerceIn(left, maxX), y.coerceIn(top, maxY))
}

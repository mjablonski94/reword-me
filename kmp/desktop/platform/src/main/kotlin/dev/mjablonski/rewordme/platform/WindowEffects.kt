package dev.mjablonski.rewordme.platform

import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import java.awt.Window

/**
 * The Windows equivalent of the macOS app's frosted sheet: the DWM
 * acrylic backdrop (the same material Windows 11 uses for its own
 * flyouts), system rounded corners, dark title-bar hint - plus the
 * tool-window style so the popup never appears in the taskbar or
 * Alt-Tab.
 */
object WindowEffects {
    private const val GWL_EXSTYLE = -20
    private const val WS_EX_TOOLWINDOW = 0x0000_0080
    private const val WS_EX_APPWINDOW = 0x0004_0000

    /** Returns true when the acrylic backdrop was applied (Win 11 22H2+). */
    fun applyPopupChrome(window: Window, dark: Boolean): Boolean {
        if (!isWindows) return false
        return runCatching {
            val hwnd = WinDef.HWND(Native.getComponentPointer(window))
            val dwm = Dwm.INSTANCE

            dwm.DwmSetWindowAttribute(
                hwnd, Dwm.DWMWA_USE_IMMERSIVE_DARK_MODE, IntByReference(if (dark) 1 else 0), 4
            )
            dwm.DwmSetWindowAttribute(
                hwnd, Dwm.DWMWA_WINDOW_CORNER_PREFERENCE, IntByReference(Dwm.DWMWCP_ROUND), 4
            )
            val acrylic = dwm.DwmSetWindowAttribute(
                hwnd, Dwm.DWMWA_SYSTEMBACKDROP_TYPE, IntByReference(Dwm.DWMSBT_TRANSIENTWINDOW), 4
            )

            hideFromTaskbar(hwnd)
            acrylic == 0
        }.getOrDefault(false)
    }

    private fun hideFromTaskbar(hwnd: WinDef.HWND) {
        val user32 = com.sun.jna.platform.win32.User32.INSTANCE
        val style = user32.GetWindowLong(hwnd, GWL_EXSTYLE)
        user32.SetWindowLong(hwnd, GWL_EXSTYLE, (style or WS_EX_TOOLWINDOW) and WS_EX_APPWINDOW.inv())
    }
}

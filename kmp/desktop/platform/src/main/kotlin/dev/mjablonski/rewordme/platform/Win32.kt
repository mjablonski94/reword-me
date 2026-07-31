package dev.mjablonski.rewordme.platform

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

val isWindows: Boolean = System.getProperty("os.name").startsWith("Windows")

/** dwmapi.dll - only the one call the acrylic look needs. */
internal interface Dwm : StdCallLibrary {
    @Suppress("FunctionName")
    fun DwmSetWindowAttribute(
        hwnd: WinDef.HWND,
        attribute: Int,
        value: com.sun.jna.ptr.IntByReference,
        size: Int
    ): Int

    companion object {
        val INSTANCE: Dwm by lazy {
            Native.load("dwmapi", Dwm::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }

        const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
        const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
        const val DWMWA_SYSTEMBACKDROP_TYPE = 38
        const val DWMWCP_ROUND = 2
        const val DWMSBT_TRANSIENTWINDOW = 3 // acrylic, the flyout backdrop
    }
}

/** Sends key combinations to whatever window is foreground. */
object KeySynthesizer {
    const val VK_CONTROL = 0x11
    const val VK_C = 0x43
    const val VK_V = 0x56
    private const val KEYEVENTF_KEYUP = 2

    fun sendCtrl(key: Int) {
        if (!isWindows) return
        val inputs = arrayOf(
            keyInput(VK_CONTROL, down = true),
            keyInput(key, down = true),
            keyInput(key, down = false),
            keyInput(VK_CONTROL, down = false)
        )
        // SendInput needs a contiguous array built from one prototype.
        val prototype = WinUser.INPUT()
        val array = prototype.toArray(inputs.size)
        inputs.forEachIndexed { index, source ->
            val target = array[index] as WinUser.INPUT
            target.type = source.type
            target.input.setType("ki")
            target.input.ki.wVk = source.input.ki.wVk
            target.input.ki.dwFlags = source.input.ki.dwFlags
            target.write()
        }
        User32.INSTANCE.SendInput(
            WinDef.DWORD(array.size.toLong()),
            array.map { it as WinUser.INPUT }.toTypedArray(),
            array[0].size()
        )
    }

    private fun keyInput(vk: Int, down: Boolean): WinUser.INPUT {
        val input = WinUser.INPUT()
        input.type = WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD.toLong())
        input.input.setType("ki")
        input.input.ki.wVk = WinDef.WORD(vk.toLong())
        input.input.ki.dwFlags = WinDef.DWORD(if (down) 0 else KEYEVENTF_KEYUP.toLong())
        return input
    }
}

/** Remembers and restores which window had focus before the popup. */
class ForegroundTracker {
    private var saved: WinDef.HWND? = null

    fun remember() {
        if (isWindows) saved = User32.INSTANCE.GetForegroundWindow()
    }

    fun restore() {
        if (isWindows) saved?.let { User32.INSTANCE.SetForegroundWindow(it) }
    }
}

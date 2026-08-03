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

/**
 * Clipboard reads straight from Win32.
 *
 * AWT cannot be used here. Once this process has put anything on the clipboard
 * it becomes the owner, and SunClipboard.getContents then answers from its own
 * cached copy instead of asking Windows - so a copy made by another app is
 * invisible until an ownership-lost event happens to be processed. The
 * sequence number is the reliable "something changed" signal, and it also
 * distinguishes a fresh copy from an unchanged clipboard that already held the
 * same text.
 */
object Win32Clipboard {
    private const val CF_UNICODETEXT = 13

    val sequence: Int
        get() = if (isWindows) ClipboardApi.INSTANCE.GetClipboardSequenceNumber() else 0

    fun text(): String? {
        if (!isWindows) return null
        return runCatching {
            // Whoever copied may still hold the clipboard open.
            if (!ClipboardApi.INSTANCE.OpenClipboard(null)) return null
            try {
                val handle = ClipboardApi.INSTANCE.GetClipboardData(CF_UNICODETEXT) ?: return null
                val locked = Kernel32Mem.INSTANCE.GlobalLock(handle) ?: return null
                try {
                    locked.getWideString(0)
                } finally {
                    Kernel32Mem.INSTANCE.GlobalUnlock(handle)
                }
            } finally {
                ClipboardApi.INSTANCE.CloseClipboard()
            }
        }.getOrNull()
    }
}

@Suppress("FunctionName")
internal interface ClipboardApi : StdCallLibrary {
    fun GetClipboardSequenceNumber(): Int
    fun OpenClipboard(hwnd: WinDef.HWND?): Boolean
    fun CloseClipboard(): Boolean
    fun GetClipboardData(format: Int): com.sun.jna.Pointer?

    companion object {
        val INSTANCE: ClipboardApi by lazy {
            Native.load("user32", ClipboardApi::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }
}

@Suppress("FunctionName")
internal interface Kernel32Mem : StdCallLibrary {
    fun GlobalLock(mem: com.sun.jna.Pointer): com.sun.jna.Pointer?
    fun GlobalUnlock(mem: com.sun.jna.Pointer): Boolean

    companion object {
        val INSTANCE: Kernel32Mem by lazy {
            Native.load("kernel32", Kernel32Mem::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }
}

/** Sends key combinations to whatever window is foreground. */
object KeySynthesizer {
    const val VK_CONTROL = 0x11
    const val VK_C = 0x43
    const val VK_V = 0x56
    private const val KEYEVENTF_KEYUP = 2

    fun sendCtrl(key: Int): Boolean {
        if (!isWindows) return false
        // One event per SendInput with a pause between. Sent as a single batch
        // all four events carry the same timestamp, and apps that handle input
        // asynchronously - the WinUI Notepad among them - drop a combination
        // pressed and released that fast.
        var sent = send(VK_CONTROL, down = true)
        sent = send(key, down = true) && sent
        sent = send(key, down = false) && sent
        // Always attempt the key-up events, even when an earlier SendInput
        // failed, so RewordMe can never leave Ctrl logically held down.
        sent = send(VK_CONTROL, down = false) && sent
        return sent
    }

    private fun send(vk: Int, down: Boolean): Boolean {
        val input = WinUser.INPUT()
        input.type = WinDef.DWORD(WinUser.INPUT.INPUT_KEYBOARD.toLong())
        input.input.setType("ki")
        input.input.ki.wVk = WinDef.WORD(vk.toLong())
        input.input.ki.dwFlags = WinDef.DWORD(if (down) 0 else KEYEVENTF_KEYUP.toLong())
        val count = User32.INSTANCE.SendInput(WinDef.DWORD(1), arrayOf(input), input.size())
        Thread.sleep(KEY_GAP_MS)
        return count.toInt() == 1
    }

    private const val KEY_GAP_MS = 30L
}

/** Remembers and restores which window had focus before the popup. */
class ForegroundTracker {
    private var saved: WinDef.HWND? = null

    fun remember() {
        if (isWindows) saved = User32.INSTANCE.GetForegroundWindow()
    }

    fun restore(): Boolean {
        if (!isWindows) return false
        val target = saved ?: return false
        User32.INSTANCE.SetForegroundWindow(target)
        repeat(FOCUS_WAIT_ATTEMPTS) {
            if (User32.INSTANCE.GetForegroundWindow() == target) return true
            Thread.sleep(FOCUS_WAIT_MS)
        }
        return false
    }

    private companion object {
        const val FOCUS_WAIT_ATTEMPTS = 10
        const val FOCUS_WAIT_MS = 20L
    }
}

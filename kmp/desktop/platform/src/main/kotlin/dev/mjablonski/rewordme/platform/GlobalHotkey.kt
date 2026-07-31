package dev.mjablonski.rewordme.platform

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import dev.mjablonski.rewordme.models.HotkeyConfig
import kotlin.concurrent.thread

/**
 * Global shortcut via RegisterHotKey - no permission needed on Windows.
 * The registration and its message loop must live on the same thread.
 * On other platforms (development on macOS/Linux) it is a logged no-op.
 */
class GlobalHotkey {
    private var worker: Thread? = null

    fun register(hotkey: HotkeyConfig, onHotkey: () -> Unit) {
        stop()
        if (!isWindows) {
            println("GlobalHotkey: not on Windows, hotkey ${hotkey.display} inactive")
            return
        }
        worker = thread(isDaemon = true, name = "rewordme-hotkey") {
            val user32 = User32.INSTANCE
            if (!user32.RegisterHotKey(null, HOTKEY_ID, hotkey.modifiers, hotkey.virtualKey)) {
                println("GlobalHotkey: RegisterHotKey failed for ${hotkey.display}")
                return@thread
            }
            val message = WinUser.MSG()
            while (!Thread.currentThread().isInterrupted &&
                user32.GetMessage(message, null, 0, 0) > 0
            ) {
                if (message.message == WM_HOTKEY) onHotkey()
            }
            user32.UnregisterHotKey(null, HOTKEY_ID)
        }
    }

    fun stop() {
        worker?.interrupt()
        worker = null
    }

    private companion object {
        const val HOTKEY_ID = 0x5257 // "RW"
        const val WM_HOTKEY = 0x0312
    }
}

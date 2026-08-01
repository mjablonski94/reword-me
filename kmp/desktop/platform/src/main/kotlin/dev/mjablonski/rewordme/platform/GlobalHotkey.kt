package dev.mjablonski.rewordme.platform

import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import dev.mjablonski.rewordme.models.HotkeyConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

sealed interface HotkeyResult {
    data object Registered : HotkeyResult

    /** Another application already owns the combination. */
    data object Conflict : HotkeyResult

    data class Failed(val errorCode: Int) : HotkeyResult

    /** Development on macOS/Linux: no global shortcut, the app still runs. */
    data object Unsupported : HotkeyResult
}

/**
 * Global shortcut via RegisterHotKey - no permission needed on Windows.
 * The registration and its message loop must live on the same thread, so the
 * worker reports its outcome back through a latch and is shut down by posting
 * WM_QUIT (Thread.interrupt cannot break the blocking GetMessage call).
 */
class GlobalHotkey {
    private var worker: Thread? = null

    @Volatile
    private var workerThreadId: Int = 0

    fun register(hotkey: HotkeyConfig, onHotkey: () -> Unit): HotkeyResult {
        stop()
        if (!isWindows) return HotkeyResult.Unsupported

        // countDown/await gives the write below a happens-before edge, so the
        // outcome is safely visible to this thread without extra synchronization.
        val ready = CountDownLatch(1)
        var outcome: HotkeyResult = HotkeyResult.Failed(0)

        worker = thread(isDaemon = true, name = "rewordme-hotkey") {
            val user32 = User32.INSTANCE
            workerThreadId = Kernel32.INSTANCE.GetCurrentThreadId()
            val registered =
                user32.RegisterHotKey(null, HOTKEY_ID, hotkey.modifiers, hotkey.virtualKey)
            outcome = if (registered) HotkeyResult.Registered else classify(Native.getLastError())
            ready.countDown()
            if (!registered) return@thread

            val message = WinUser.MSG()
            while (user32.GetMessage(message, null, 0, 0) > 0) {
                if (message.message == WM_HOTKEY) onHotkey()
            }
            user32.UnregisterHotKey(null, HOTKEY_ID)
        }

        // RegisterHotKey returns immediately; the timeout only guards against
        // the thread never starting, so the caller can never hang here.
        if (!ready.await(3, TimeUnit.SECONDS)) return HotkeyResult.Failed(0)
        return outcome
    }

    fun stop() {
        val thread = worker ?: return
        worker = null
        val threadId = workerThreadId
        workerThreadId = 0
        if (isWindows && threadId != 0) {
            User32.INSTANCE.PostThreadMessage(
                threadId, WM_QUIT, WinDef.WPARAM(0), WinDef.LPARAM(0)
            )
        }
        // Wait for UnregisterHotKey: re-registering the same combination must
        // not collide with our own outgoing registration.
        thread.join(JOIN_TIMEOUT_MS)
    }

    private companion object {
        const val HOTKEY_ID = 0x5257 // "RW"
        const val WM_HOTKEY = 0x0312
        const val WM_QUIT = 0x0012
        const val ERROR_HOTKEY_ALREADY_REGISTERED = 1409
        const val JOIN_TIMEOUT_MS = 1000L

        fun classify(errorCode: Int): HotkeyResult =
            if (errorCode == ERROR_HOTKEY_ALREADY_REGISTERED) HotkeyResult.Conflict
            else HotkeyResult.Failed(errorCode)
    }
}

package dev.mjablonski.rewordme.platform

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection

/** Reads the selected text from whatever app is foreground. */
interface SelectionReading {
    /** Blocking; call off the UI thread. */
    fun readSelection(): String?
}

/** Puts the reworded text back where the selection was. */
interface TextReplacing {
    /** Blocking; call off the UI thread. */
    fun replaceSelection(text: String)
}

private object ClipboardAccess {
    fun read(): String? = runCatching {
        Toolkit.getDefaultToolkit().systemClipboard
            .getData(DataFlavor.stringFlavor) as? String
    }.getOrNull()

    fun write(text: String?) {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard
                .setContents(StringSelection(text ?: ""), null)
        }
    }
}

/**
 * Phase-1 capture: synthesized Ctrl+C with clipboard save/restore.
 * TODO(phase 2): UI Automation TextPattern for clipboard-free reads and
 * the selection's screen rectangle (popup positioning).
 */
class WindowsSelectionReader : SelectionReading {
    override fun readSelection(): String? {
        if (!isWindows) return null
        val previous = Win32Clipboard.text()
        val before = Win32Clipboard.sequence

        // The hotkey's own modifiers (Ctrl+Alt) are usually still held
        // when we get here; a Ctrl+C synthesized now reaches the app as
        // Ctrl+Alt+C and copies nothing. Wait for release first.
        waitForModifierRelease()
        KeySynthesizer.sendCtrl(KeySynthesizer.VK_C)

        // Slow apps can take several hundred ms to write the clipboard.
        var copied: String? = null
        for (attempt in 0 until 30) {
            Thread.sleep(25)
            if (Win32Clipboard.sequence == before) continue
            copied = Win32Clipboard.text()?.takeIf(String::isNotEmpty)
            if (copied != null) break
        }
        // Nothing was copied means the clipboard was never touched, and
        // rewriting it would only steal ownership for no reason.
        if (copied != null) ClipboardAccess.write(previous)
        return copied
    }

    private fun waitForModifierRelease() {
        val user32 = com.sun.jna.platform.win32.User32.INSTANCE
        val modifiers = intArrayOf(0x11, 0x12, 0x10, 0x5B) // Ctrl, Alt, Shift, Win
        repeat(20) {
            val held = modifiers.any { vk ->
                (user32.GetAsyncKeyState(vk).toInt() and 0x8000) != 0
            }
            if (!held) return
            Thread.sleep(50)
        }
    }
}

/**
 * Phase-1 replace: focus back to the host window, paste over the
 * selection, restore the clipboard.
 */
class WindowsTextReplacer(private val foreground: ForegroundTracker) : TextReplacing {
    override fun replaceSelection(text: String) {
        if (!isWindows) return
        val previous = Win32Clipboard.text()
        ClipboardAccess.write(text)
        foreground.restore()
        Thread.sleep(80)
        KeySynthesizer.sendCtrl(KeySynthesizer.VK_V)
        Thread.sleep(250)
        ClipboardAccess.write(previous)
    }
}

/** Development stand-ins so the app runs on macOS/Linux while iterating. */
class DevSelectionReader : SelectionReading {
    override fun readSelection(): String? = ClipboardAccess.read()
}

class DevTextReplacer : TextReplacing {
    override fun replaceSelection(text: String) {
        ClipboardAccess.write(text)
        println("DevTextReplacer: result copied to clipboard")
    }
}

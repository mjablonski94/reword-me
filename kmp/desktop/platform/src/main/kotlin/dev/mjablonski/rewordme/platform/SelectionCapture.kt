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
        val previous = ClipboardAccess.read()
        ClipboardAccess.write("")
        KeySynthesizer.sendCtrl(KeySynthesizer.VK_C)
        Thread.sleep(200)
        val copied = ClipboardAccess.read()?.takeIf(String::isNotEmpty)
        ClipboardAccess.write(previous)
        return copied
    }
}

/**
 * Phase-1 replace: focus back to the host window, paste over the
 * selection, restore the clipboard.
 */
class WindowsTextReplacer(private val foreground: ForegroundTracker) : TextReplacing {
    override fun replaceSelection(text: String) {
        if (!isWindows) return
        val previous = ClipboardAccess.read()
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

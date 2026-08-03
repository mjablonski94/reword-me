package dev.mjablonski.rewordme.platform

import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.CharBuffer

/** Reads the selected text from whatever app is foreground. */
interface SelectionReading {
    /** Blocking; call off the UI thread. */
    fun readSelection(): String?
}

/** Puts the reworded text back where the selection was. */
interface TextReplacing {
    /** Blocking; call off the UI thread. */
    fun replaceSelection(text: String): Boolean
}

private object ClipboardAccess {
    private val clipboard get() = Toolkit.getDefaultToolkit().systemClipboard

    fun read(): String? {
        repeat(RETRY_COUNT) { attempt ->
            try {
                return clipboard.getData(DataFlavor.stringFlavor) as? String
            } catch (_: IllegalStateException) {
                if (attempt < RETRY_COUNT - 1) Thread.sleep(RETRY_DELAY_MS)
            } catch (_: Exception) {
                return null
            }
        }
        return null
    }

    /**
     * Materialises every advertised AWT flavor before this process takes
     * clipboard ownership. Keeping only CF_UNICODETEXT would silently discard
     * copied images, files, HTML and rich text when the original clipboard is
     * restored after Ctrl+C/Ctrl+V.
     */
    fun snapshot(): MaterializedTransferable? {
        repeat(RETRY_COUNT) { attempt ->
            try {
                return MaterializedTransferable.capture(clipboard.getContents(null))
            } catch (_: IllegalStateException) {
                if (attempt < RETRY_COUNT - 1) Thread.sleep(RETRY_DELAY_MS)
            } catch (_: Exception) {
                return null
            }
        }
        return null
    }

    fun write(text: String): Boolean = set(StringSelection(text))

    fun restore(snapshot: MaterializedTransferable): Boolean = set(snapshot)

    private fun set(contents: Transferable): Boolean {
        repeat(RETRY_COUNT) { attempt ->
            try {
                clipboard.setContents(contents, null)
                return true
            } catch (_: IllegalStateException) {
                if (attempt < RETRY_COUNT - 1) Thread.sleep(RETRY_DELAY_MS)
            } catch (_: Exception) {
                return false
            }
        }
        return false
    }

    private const val RETRY_COUNT = 10
    private const val RETRY_DELAY_MS = 25L
}

/**
 * A replayable clipboard payload. Clipboard streams and readers are normally
 * one-shot views backed by the current owner, so they must be consumed before
 * RewordMe writes its temporary text and recreated each time Windows asks for
 * the restored flavor.
 */
internal class MaterializedTransferable private constructor(
    private val values: LinkedHashMap<DataFlavor, StoredValue>
) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = values.keys.toTypedArray()

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor in values

    override fun getTransferData(flavor: DataFlavor): Any =
        values[flavor]?.fresh() ?: throw UnsupportedFlavorException(flavor)

    private fun interface StoredValue {
        fun fresh(): Any
    }

    companion object {
        fun capture(source: Transferable?): MaterializedTransferable? {
            if (source == null) return MaterializedTransferable(linkedMapOf())

            val captured = linkedMapOf<DataFlavor, StoredValue>()
            for (flavor in source.transferDataFlavors) {
                val value = try {
                    source.getTransferData(flavor)
                } catch (_: Exception) {
                    // A partial snapshot would make restoration destructive.
                    return null
                }
                captured[flavor] = materialize(flavor, value) ?: return null
            }
            return MaterializedTransferable(captured)
        }

        private fun materialize(flavor: DataFlavor, value: Any): StoredValue? = when (value) {
            is InputStream -> {
                if (!flavor.representationClass.isAssignableFrom(ByteArrayInputStream::class.java)) {
                    null
                } else {
                    val bytes = value.use(InputStream::readBytes)
                    StoredValue { ByteArrayInputStream(bytes) }
                }
            }
            is Reader -> {
                if (!flavor.representationClass.isAssignableFrom(StringReader::class.java)) {
                    null
                } else {
                    val text = value.use(Reader::readText)
                    StoredValue { StringReader(text) }
                }
            }
            is ByteBuffer -> {
                val copy = value.asReadOnlyBuffer()
                val bytes = ByteArray(copy.remaining())
                copy.get(bytes)
                StoredValue { ByteBuffer.wrap(bytes).asReadOnlyBuffer() }
            }
            is CharBuffer -> {
                val copy = value.asReadOnlyBuffer()
                val chars = CharArray(copy.remaining())
                copy.get(chars)
                StoredValue { CharBuffer.wrap(chars).asReadOnlyBuffer() }
            }
            is ByteArray -> {
                val copy = value.copyOf()
                StoredValue { copy.copyOf() }
            }
            is CharArray -> {
                val copy = value.copyOf()
                StoredValue { copy.copyOf() }
            }
            is List<*> -> {
                val copy = value.toList()
                StoredValue { ArrayList(copy) }
            }
            else -> StoredValue { value }
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
        val previous = ClipboardAccess.snapshot() ?: return null
        val before = Win32Clipboard.sequence

        // The hotkey's own modifiers (Ctrl+Alt) are usually still held
        // when we get here; a Ctrl+C synthesized now reaches the app as
        // Ctrl+Alt+C and copies nothing. Wait for release first.
        if (!waitForModifierRelease()) return null
        val sent = KeySynthesizer.sendCtrl(KeySynthesizer.VK_C)

        // Slow apps can take several hundred ms to write the clipboard.
        var copied: String? = null
        var changed = false
        for (attempt in 0 until 30) {
            Thread.sleep(25)
            if (Win32Clipboard.sequence == before) continue
            changed = true
            copied = Win32Clipboard.text()?.takeIf(String::isNotEmpty)
            if (copied != null || !sent) break
        }
        // Restore whenever Ctrl+C changed the clipboard, even if the copied
        // selection was empty or a non-text format. Returning text while the
        // restore failed would make a successful rewrite destroy user data.
        val restored = !changed || ClipboardAccess.restore(previous)
        return copied?.takeIf { sent && restored }
    }

    private fun waitForModifierRelease(): Boolean {
        val user32 = com.sun.jna.platform.win32.User32.INSTANCE
        val modifiers = intArrayOf(0x11, 0x12, 0x10, 0x5B, 0x5C) // Ctrl, Alt, Shift, Win
        repeat(20) {
            val held = modifiers.any { vk ->
                (user32.GetAsyncKeyState(vk).toInt() and 0x8000) != 0
            }
            if (!held) return true
            Thread.sleep(50)
        }
        return false
    }
}

/**
 * Phase-1 replace: focus back to the host window, paste over the
 * selection, restore the clipboard.
 */
class WindowsTextReplacer(private val foreground: ForegroundTracker) : TextReplacing {
    override fun replaceSelection(text: String): Boolean {
        if (!isWindows) return false
        val previous = ClipboardAccess.snapshot() ?: return false
        if (!ClipboardAccess.write(text)) return false

        val focused = foreground.restore()
        val pasted = if (focused) {
            Thread.sleep(80)
            KeySynthesizer.sendCtrl(KeySynthesizer.VK_V).also { Thread.sleep(250) }
        } else {
            false
        }
        val restored = ClipboardAccess.restore(previous)
        return focused && pasted && restored
    }
}

/** Development stand-ins so the app runs on macOS/Linux while iterating. */
class DevSelectionReader : SelectionReading {
    override fun readSelection(): String? = ClipboardAccess.read()
}

class DevTextReplacer : TextReplacing {
    override fun replaceSelection(text: String): Boolean {
        val written = ClipboardAccess.write(text)
        if (written) println("DevTextReplacer: result copied to clipboard")
        return written
    }
}

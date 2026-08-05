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
import java.nio.charset.Charset

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

internal object ClipboardRestorePolicy {
    fun shouldRestore(temporarySequence: Int, currentSequence: Int): Boolean =
        temporarySequence == currentSequence
}

internal object ClipboardCopyPolicy {
    fun observedSyntheticCopy(baseline: Int, current: Int): Boolean = baseline != current
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

        private fun materialize(flavor: DataFlavor, value: Any): StoredValue? {
            if (flavor.isFlavorTextType) return materializeText(flavor, value)
            return when (value) {
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

        /**
         * Windows clipboard owners sometimes return a Reader for a text flavor
         * advertised as an InputStream (Chromium does this for text/plain).
         * Normalize the payload, then replay the representation promised by
         * the DataFlavor instead of rejecting that valid mismatch.
         */
        private fun materializeText(flavor: DataFlavor, value: Any): StoredValue? {
            val charset = runCatching {
                Charset.forName(flavor.getParameter("charset") ?: Charset.defaultCharset().name())
            }.getOrDefault(Charset.defaultCharset())
            val text = when (value) {
                is String -> value
                is Reader -> value.use(Reader::readText)
                is CharBuffer -> value.asReadOnlyBuffer().toString()
                is CharArray -> String(value)
                is InputStream -> value.use(InputStream::readBytes).toString(charset)
                is ByteBuffer -> {
                    val copy = value.asReadOnlyBuffer()
                    val bytes = ByteArray(copy.remaining())
                    copy.get(bytes)
                    bytes.toString(charset)
                }
                is ByteArray -> value.toString(charset)
                else -> return null
            }
            val representation = flavor.representationClass
            return when {
                representation.isAssignableFrom(String::class.java) -> StoredValue { text }
                representation.isAssignableFrom(StringReader::class.java) ->
                    StoredValue { StringReader(text) }
                representation.isAssignableFrom(CharBuffer::class.java) ->
                    StoredValue { CharBuffer.wrap(text).asReadOnlyBuffer() }
                representation == CharArray::class.java -> {
                    val chars = text.toCharArray()
                    StoredValue { chars.copyOf() }
                }
                representation.isAssignableFrom(ByteArrayInputStream::class.java) -> {
                    val bytes = text.toByteArray(charset)
                    StoredValue { ByteArrayInputStream(bytes) }
                }
                representation.isAssignableFrom(ByteBuffer::class.java) -> {
                    val bytes = text.toByteArray(charset)
                    StoredValue { ByteBuffer.wrap(bytes).asReadOnlyBuffer() }
                }
                representation == ByteArray::class.java -> {
                    val bytes = text.toByteArray(charset)
                    StoredValue { bytes.copyOf() }
                }
                else -> null
            }
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

        // The hotkey's own modifiers (Ctrl+Alt) are usually still held
        // when we get here; a Ctrl+C synthesized now reaches the app as
        // Ctrl+Alt+C and copies nothing. Snapshot only after release so a
        // user's intervening copy is the new baseline, never our result.
        if (!waitForModifierRelease()) return null
        // Clipboard owners can expose private or delayed formats that AWT
        // cannot safely replay. Selection capture must still work in that
        // case; the copied selection is left on the clipboard as a fallback.
        val previous = ClipboardAccess.snapshot()
        val before = Win32Clipboard.sequence
        val sent = KeySynthesizer.sendCtrl(KeySynthesizer.VK_C)
        if (!sent) return null

        // Slow apps can take several hundred ms to write the clipboard.
        var copied: String? = null
        var copiedSequence: Int? = null
        var newerOwnerAppeared = false
        for (attempt in 0 until 30) {
            Thread.sleep(25)
            val currentSequence = Win32Clipboard.sequence
            if (!ClipboardCopyPolicy.observedSyntheticCopy(before, currentSequence)) continue
            if (copiedSequence == null) {
                copiedSequence = currentSequence
            } else if (currentSequence != copiedSequence) {
                // The first generation is the only one attributable to the
                // synthetic copy. A later generation belongs to someone else.
                newerOwnerAppeared = true
                copied = null
                break
            }
            copied = Win32Clipboard.text()?.takeIf(String::isNotEmpty)
            if (copied != null) break
        }
        // Restore only the generation produced by Ctrl+C, even if it was an
        // empty or non-text selection. A newer clipboard owner always wins.
        if (
            copiedSequence != null &&
            !newerOwnerAppeared &&
            previous != null &&
            ClipboardRestorePolicy.shouldRestore(copiedSequence, Win32Clipboard.sequence)
        ) {
            ClipboardAccess.restore(previous)
        }
        // Clipboard restoration is best-effort and must never turn a
        // successful text capture into a false "no text selected" result.
        return copied
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
        val temporarySequence = Win32Clipboard.sequence

        val focused = foreground.restore()
        val pasted = if (focused) {
            Thread.sleep(80)
            KeySynthesizer.sendCtrl(KeySynthesizer.VK_V).also { Thread.sleep(250) }
        } else {
            false
        }
        // Preserve a copy the user made while the paste was settling. The
        // Win32 sequence number changes on every clipboard ownership change.
        val restored = if (ClipboardRestorePolicy.shouldRestore(
            temporarySequence, Win32Clipboard.sequence
        )) {
            ClipboardAccess.restore(previous)
        } else {
            true
        }
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

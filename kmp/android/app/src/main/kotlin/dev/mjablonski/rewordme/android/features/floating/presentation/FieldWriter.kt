package dev.mjablonski.rewordme.android.features.floating.presentation

import dev.mjablonski.rewordme.android.platform.Clipboard
import kotlinx.coroutines.delay

/** The host's text field, as far as writing into it goes. */
interface EditableField {
    /** Re-reads the field; false once it is gone from the screen. */
    fun refresh(): Boolean
    val text: String?
    val editable: Boolean
    fun setText(text: String): Boolean
    fun select(start: Int, end: Int): Boolean
    fun paste(): Boolean
}

enum class WriteOutcome {
    /** The field took the whole new text. */
    SET_TEXT,

    /** The field refused that, but took a paste over the selection. */
    PASTED,

    /** Nothing stuck: the rewrite is on the clipboard for the user to paste. */
    COPIED
}

/**
 * Puts a rewrite back into another app's field, the way the floating icon has
 * to without PROCESS_TEXT's result channel. Setting the text keeps the
 * clipboard untouched, so it goes first; rich editors and some web fields
 * ignore it, so a paste over the re-selected range follows; and when neither
 * changes the field, the user still has the text on the clipboard.
 *
 * Apps apply both actions asynchronously (Compose and web fields update a
 * frame or a renderer round-trip later), so success is judged by the field
 * text actually changing within [settleMillis] - deciding too early would
 * paste on top of a set-text that was merely late.
 */
class FieldWriter(
    private val clipboard: Clipboard,
    private val settleMillis: Long = 600,
    private val pollMillis: Long = 150
) {

    suspend fun write(
        field: EditableField,
        snapshot: SelectionSnapshot,
        replacement: String
    ): WriteOutcome {
        if (!field.refresh() || !field.editable) return copyInstead(replacement)
        val before = field.text.orEmpty()
        // The field moved on (the user edited it, the app reformatted it):
        // only a single unambiguous match of the original is safe to replace.
        val splice = FloatingRules.splice(before, snapshot.from, snapshot.selected, replacement)
            ?: return copyInstead(replacement)

        if (field.setText(splice.text) && changed(field, before)) {
            field.select(splice.cursor, splice.cursor)
            return WriteOutcome.SET_TEXT
        }
        if (clipboard.copy(replacement) &&
            field.select(splice.start, splice.end) &&
            field.paste() &&
            changed(field, before)
        ) {
            return WriteOutcome.PASTED
        }
        return copyInstead(replacement)
    }

    private suspend fun changed(field: EditableField, before: String): Boolean {
        var waited = 0L
        while (true) {
            if (field.refresh() && field.text != before) return true
            if (waited >= settleMillis) return false
            delay(pollMillis)
            waited += pollMillis
        }
    }

    private fun copyInstead(text: String): WriteOutcome {
        clipboard.copy(text)
        return WriteOutcome.COPIED
    }
}

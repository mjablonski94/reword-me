package dev.mjablonski.rewordme.android.features.floating.presentation

import dev.mjablonski.rewordme.android.support.RecordingClipboard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** A host field whose reaction to each action the test scripts. */
private class FakeField(
    var current: String,
    private val isEditable: Boolean = true,
    private val setTextApplies: Boolean = true,
    /** Refreshes it takes before an accepted set-text shows up. */
    private val setTextLag: Int = 0,
    private val pasteApplies: Boolean = true,
    private val clipboard: RecordingClipboard? = null
) : EditableField {
    var gone = false
    var selection: Pair<Int, Int>? = null
    private var pendingText: String? = null
    private var lag = 0

    override fun refresh(): Boolean {
        pendingText?.let {
            if (lag-- <= 0) {
                current = it
                pendingText = null
            }
        }
        return !gone
    }

    override val text: String get() = current
    override val editable: Boolean get() = isEditable

    override fun setText(text: String): Boolean {
        if (setTextApplies) {
            pendingText = text
            lag = setTextLag
        }
        return true
    }

    override fun select(start: Int, end: Int): Boolean {
        selection = start to end
        return true
    }

    override fun paste(): Boolean {
        if (!pasteApplies) return false
        val (start, end) = selection ?: return false
        current = current.substring(0, start) + clipboard!!.copies.last() + current.substring(end)
        return true
    }
}

class FieldWriterTests {

    private val snapshot = SelectionSnapshot(
        packageName = "com.google.android.apps.messaging",
        text = "hej sorki ze dopiero",
        start = 4,
        end = 9,
        editable = true
    )

    @Test
    fun setTextWinsAndLeavesTheClipboardAlone() = runTest {
        val clipboard = RecordingClipboard()
        val field = FakeField("hej sorki ze dopiero")

        val outcome = FieldWriter(clipboard).write(field, snapshot, "sorry")

        assertEquals(WriteOutcome.SET_TEXT, outcome)
        assertEquals("hej sorry ze dopiero", field.current)
        assertEquals(9 to 9, field.selection, "cursor right after the rewrite")
        assertTrue(clipboard.copies.isEmpty())
    }

    @Test
    fun aLateSetTextIsWaitedForInsteadOfPastedOver() = runTest {
        val clipboard = RecordingClipboard()
        // Compose and web fields apply the new text a frame or more later.
        val field = FakeField("hej sorki ze dopiero", setTextLag = 2, clipboard = clipboard)

        val outcome = FieldWriter(clipboard).write(field, snapshot, "sorry")

        assertEquals(WriteOutcome.SET_TEXT, outcome)
        assertEquals("hej sorry ze dopiero", field.current, "pasted on top of a late set-text")
    }

    @Test
    fun aFieldIgnoringSetTextGetsAPasteOverTheSelection() = runTest {
        val clipboard = RecordingClipboard()
        val field = FakeField("hej sorki ze dopiero", setTextApplies = false, clipboard = clipboard)

        val outcome = FieldWriter(clipboard).write(field, snapshot, "sorry")

        assertEquals(WriteOutcome.PASTED, outcome)
        assertEquals("hej sorry ze dopiero", field.current)
        assertEquals(4 to 9, field.selection, "pasted over the original selection")
    }

    @Test
    fun whenNothingSticksTheRewriteIsLeftOnTheClipboard() = runTest {
        val clipboard = RecordingClipboard()
        val field = FakeField(
            "hej sorki ze dopiero",
            setTextApplies = false,
            pasteApplies = false,
            clipboard = clipboard
        )

        val outcome = FieldWriter(clipboard).write(field, snapshot, "sorry")

        assertEquals(WriteOutcome.COPIED, outcome)
        assertEquals("hej sorki ze dopiero", field.current)
        assertEquals("sorry", clipboard.copies.last())
    }

    @Test
    fun aClosedOrReadOnlyFieldIsNeverTouched() = runTest {
        val clipboard = RecordingClipboard()
        val gone = FakeField("hej sorki ze dopiero").apply { this.gone = true }
        assertEquals(WriteOutcome.COPIED, FieldWriter(clipboard).write(gone, snapshot, "sorry"))

        val readOnly = FakeField("hej sorki ze dopiero", isEditable = false)
        assertEquals(WriteOutcome.COPIED, FieldWriter(clipboard).write(readOnly, snapshot, "sorry"))
        assertEquals("hej sorki ze dopiero", readOnly.current)
    }

    @Test
    fun aFieldThatChangedUnderTheSelectionIsNotGuessedAt() = runTest {
        val clipboard = RecordingClipboard()
        val field = FakeField("completely different text now")

        val outcome = FieldWriter(clipboard).write(field, snapshot, "sorry")

        assertEquals(WriteOutcome.COPIED, outcome)
        assertEquals("completely different text now", field.current)
    }
}

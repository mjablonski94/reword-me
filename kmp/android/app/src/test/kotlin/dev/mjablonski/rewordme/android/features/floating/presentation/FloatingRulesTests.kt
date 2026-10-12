package dev.mjablonski.rewordme.android.features.floating.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FloatingRulesTests {

    private val own = "dev.mjablonski.rewordme"

    private fun selection(
        text: String = "hej sorki ze dopiero teraz pisze",
        start: Int = 4,
        end: Int = 9,
        packageName: String = "com.google.android.apps.messaging",
        editable: Boolean = true,
        password: Boolean = false,
        focused: Boolean = true,
        viewId: String? = null
    ) = SelectionSnapshot(packageName, text, start, end, editable, password, focused, viewId)

    @Test
    fun anyAppsSelectedTextGetsTheIcon() {
        assertTrue(FloatingRules.shouldShow(selection(), own))
        // Read-only text too: the popup then offers Copy instead of Replace.
        assertTrue(FloatingRules.shouldShow(selection(editable = false, focused = false), own))
    }

    @Test
    fun noSelectionNoIcon() {
        assertFalse(FloatingRules.shouldShow(null, own))
        assertFalse(FloatingRules.shouldShow(selection(start = 6, end = 6), own))
        assertFalse(FloatingRules.shouldShow(selection(start = -1, end = -1), own))
        assertFalse(FloatingRules.shouldShow(selection(text = "a     b", start = 1, end = 5), own))
    }

    @Test
    fun aBackwardSelectionCountsLikeAForwardOne() {
        // Dragging the start handle past the end reports start > end.
        val backward = selection(start = 9, end = 4)
        assertTrue(FloatingRules.shouldShow(backward, own))
        assertEquals("sorki", backward.selected)
    }

    @Test
    fun aStaleSelectionPastTheTextNeverCrashes() {
        assertEquals("", selection(text = "short", start = 2, end = 40).selected)
    }

    @Test
    fun passwordsAndTheSystemUiNeverGetTheIcon() {
        assertFalse(FloatingRules.shouldShow(selection(password = true), own))
        assertFalse(FloatingRules.shouldShow(selection(packageName = "com.android.systemui"), own))
    }

    @Test
    fun anUnfocusedFieldsLeftoverSelectionIsNotShown() {
        assertFalse(FloatingRules.shouldShow(selection(focused = false), own))
    }

    @Test
    fun rewordMeItselfShowsTheIconOnlyInTheTutorialEditor() {
        // Its own screens list "Reword me" in the menu already, and the
        // popup's steering field must not offer to reword itself.
        assertFalse(FloatingRules.shouldShow(selection(packageName = own), own))
        assertFalse(
            FloatingRules.shouldShow(selection(packageName = own, viewId = "$own:id/steering"), own)
        )
        assertTrue(
            FloatingRules.shouldShow(
                selection(packageName = own, viewId = "$own:id/home_try_it_editor"),
                own
            )
        )
    }

    private val screen = Box(0, 0, 1080, 2400)

    private fun Spot.box(size: Int) = Box(x, y, x + size, y + size)

    private fun Box.overlaps(other: Box) =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    private fun place(first: Box, last: Box = first, obstacles: List<Box> = emptyList()) =
        FloatingRules.place(first, last, screen, obstacles, size = 120, handle = 70, gap = 16)

    @Test
    fun theIconSitsJustAboveTheSelectionNearItsEnd() {
        assertEquals(Spot(420 - 60, 1000 - 16 - 120), place(Box(400, 1000, 420, 1050)))
    }

    @Test
    fun aMultiLineSelectionKeepsItsTextUncovered() {
        val spot = place(first = Box(100, 900, 120, 950), last = Box(400, 1000, 420, 1050))
        assertEquals(900 - 16 - 120, spot.y)
    }

    @Test
    fun theIconGoesAboveTheSelectionToolbarNeverOnIt() {
        // Google Messages on a 1080x2400 phone: "teraz" selected in the
        // compose field, the toolbar right above it, the keyboard right below.
        val word = Box(616, 1360, 660, 1412)
        val toolbar = Box(185, 1213, 1040, 1334)
        val keyboard = Box(0, 1510, 1080, 2400)
        val spot = FloatingRules.place(
            word, word, screen, listOf(toolbar, keyboard), size = 105, handle = 68, gap = 16
        )

        val icon = spot.box(105)
        assertFalse(icon.overlaps(toolbar), "covers the toolbar: $icon")
        assertFalse(icon.overlaps(keyboard), "covers the keyboard: $icon")
        assertEquals(Spot(660 - 52, 1213 - 16 - 105), spot)
    }

    @Test
    fun withNoRoomAtTheTopTheIconGoesBelowPastTheToolbar() {
        // Near the top Android drops the toolbar below the selection.
        val word = Box(400, 100, 420, 150)
        val statusBar = Box(0, 0, 1080, 140)
        val toolbar = Box(185, 170, 1040, 290)
        val spot = place(word, obstacles = listOf(statusBar, toolbar))
        assertEquals(Spot(420 + 70, 290 + 16), spot)
    }

    @Test
    fun belowTheSelectionTheIconClearsTheEndHandleEvenAtTheRightEdge() {
        val word = Box(1000, 100, 1020, 150)
        val statusBar = Box(0, 0, 1080, 140)
        val spot = place(word, obstacles = listOf(statusBar))
        assertEquals(Spot(1020 - 35 - 16 - 120, 166), spot)
    }

    @Test
    fun withNoClearSpotTheIconCoversOnlyTheTextAfterTheSelection() {
        val everything = Box(0, 0, 1080, 2400)
        val spot = place(Box(400, 1000, 420, 1050), obstacles = listOf(everything))
        assertEquals(Spot(436, 1025 - 60), spot)
    }

    @Test
    fun theIconStaysOnScreenNearTheTopLeftCorner() {
        val spot = place(Box(-50, -40, -30, 10))
        assertTrue(spot.x >= 16 && spot.y >= 16, "$spot")
    }

    @Test
    fun splicingReplacesTheSelectionAndPutsTheCursorAfterIt() {
        val splice = FloatingRules.splice("hej sorki ze dopiero", 4, "sorki", "sorry")
        assertEquals(Splice("hej sorry ze dopiero", 4, 9, cursor = 9), splice)
    }

    @Test
    fun aShiftedSelectionIsFoundAgainWhenItIsUnambiguous() {
        // Two characters were typed in front of it meanwhile.
        val splice = FloatingRules.splice("a hej sorki ze", 4, "sorki", "sorry")
        assertEquals("a hej sorry ze", splice?.text)
    }

    @Test
    fun anAmbiguousOrVanishedSelectionIsNeverGuessed() {
        assertNull(FloatingRules.splice("ok ok ok", 1, "ok", "fine"))
        assertNull(FloatingRules.splice("something else", 0, "sorki", "sorry"))
        assertNull(FloatingRules.splice("text", 0, "", "x"))
    }

    @Test
    fun theServiceCountsAsOnInEitherComponentSpelling() {
        val pkg = "dev.mjablonski.rewordme"
        val cls = "dev.mjablonski.rewordme.android.features.floating.presentation.FloatingIconService"
        assertTrue(FloatingRules.isServiceEnabled("com.other/.Svc:$pkg/$cls", pkg, cls))
        assertTrue(
            FloatingRules.isServiceEnabled(
                "$pkg/.android.features.floating.presentation.FloatingIconService",
                pkg,
                cls
            )
        )
        assertFalse(FloatingRules.isServiceEnabled(null, pkg, cls))
        assertFalse(FloatingRules.isServiceEnabled("", pkg, cls))
        // The debug build is another package with the same class.
        assertFalse(FloatingRules.isServiceEnabled("$pkg.debug/$cls", pkg, cls))
    }
}

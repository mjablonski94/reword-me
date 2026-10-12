package dev.mjablonski.rewordme.android.features.popup.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectionRulesTests {

    @Test
    fun anEditableSelectionCanBeReplaced() {
        val begin = beginFor("hey there", shared = false, readonlyHost = false, callerIsBrowser = false)

        assertEquals("hey there", begin.text)
        assertTrue(begin.canReplace)
        assertFalse(begin.fromBrowser)
        assertFalse(begin.tooShort)
    }

    @Test
    fun readOnlyHostsOnlyGetCopy() {
        val begin = beginFor("hey there", shared = false, readonlyHost = true, callerIsBrowser = false)

        assertFalse(begin.canReplace)
    }

    @Test
    fun sharedTextHasNoWayBackButTheClipboard() {
        // Share is the fallback for apps that cannot list "Reword me" at all.
        val begin = beginFor("hey there", shared = true, readonlyHost = false, callerIsBrowser = true)

        assertFalse(begin.canReplace)
        assertFalse(begin.fromBrowser)
    }

    @Test
    fun browsersAreFlaggedSoReplaceAlsoCopies() {
        val begin = beginFor("hey there", shared = false, readonlyHost = false, callerIsBrowser = true)

        assertTrue(begin.canReplace)
        assertTrue(begin.fromBrowser)
    }

    @Test
    fun tooShortSelectionsAreToldApartFromMissingOnes() {
        val short = beginFor("ok", shared = false, readonlyHost = false, callerIsBrowser = false)
        assertEquals("", short.text)
        assertTrue(short.tooShort)

        val blank = beginFor("   ", shared = false, readonlyHost = false, callerIsBrowser = false)
        assertEquals("", blank.text)
        assertFalse(blank.tooShort)
    }
}

package dev.mjablonski.rewordme.android.features.floating.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatingHandoffTests {

    /** Accepts only the token it handed out, like the service. */
    private class TokenReceiver(private val token: String) : FloatingHandoff.Receiver {
        val outcomes = mutableListOf<String?>()
        override fun finished(token: String, replacement: String?): Boolean {
            if (token != this.token) return false
            outcomes += replacement
            return true
        }
    }

    @Test
    fun thePopupsOutcomeReachesTheServiceThatOpenedIt() {
        val handoff = FloatingHandoff()
        val service = TokenReceiver("t-1")
        handoff.attach(service)

        assertTrue(handoff.finish("t-1", "sorry"))
        assertTrue(handoff.finish("t-1", null))
        assertEquals(listOf("sorry", null), service.outcomes)
    }

    @Test
    fun aForeignTokenCannotMakeTheServiceEditAField() {
        val handoff = FloatingHandoff()
        val service = TokenReceiver("t-1")
        handoff.attach(service)

        assertFalse(handoff.finish("guessed", "pwned"))
        assertTrue(service.outcomes.isEmpty())
    }

    @Test
    fun withTheServiceGoneThePopupIsToldToFallBackToCopy() {
        val handoff = FloatingHandoff()
        assertFalse(handoff.finish("t-1", "sorry"))

        val service = TokenReceiver("t-1")
        handoff.attach(service)
        handoff.detach(service)
        assertFalse(handoff.finish("t-1", "sorry"))
    }

    @Test
    fun aStaleServiceDetachingDoesNotUnhookItsReplacement() {
        // Android may connect a new instance before destroying the old one.
        val handoff = FloatingHandoff()
        val old = TokenReceiver("t-1")
        val new = TokenReceiver("t-2")
        handoff.attach(old)
        handoff.attach(new)
        handoff.detach(old)

        assertTrue(handoff.finish("t-2", "sorry"))
    }
}

package dev.mjablonski.rewordme.android.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalizedTests {

    @Test
    fun gatewayHtmlIsReducedToItsText() {
        assertEquals(
            "502 Bad Gateway nginx",
            readableDetail("<html><body><h1>502 Bad Gateway</h1>nginx</body></html>")
        )
    }

    @Test
    fun plainDetailsPassThroughUntouched() {
        assertEquals("quota exceeded for model x", readableDetail("quota exceeded for model x"))
        assertEquals("a < b", readableDetail("a < b"))
    }

    @Test
    fun hugeHtmlPagesAreCapped() {
        val page = "<html><body>" + "error ".repeat(100) + "</body></html>"
        assertTrue(readableDetail(page).length <= 160)
    }
}

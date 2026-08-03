package dev.mjablonski.rewordme.app

import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

class PopupRegressionTests {
    @Test
    fun newlyEditedSteeringWinsWhenRunningAgain() {
        assertEquals(
            "Make it shorter",
            instructionForAgain("  Make it shorter  ", "Make it friendly")
        )
    }

    @Test
    fun blankSteeringRepeatsThePreviousInstruction() {
        assertEquals("Make it friendly", instructionForAgain("  ", "Make it friendly"))
    }

    @Test
    fun popupIsClampedInsideASecondaryMonitorWorkArea() {
        val location = clampedPopupLocation(
            x = -1_050,
            y = 850,
            width = 340,
            height = 260,
            screen = Rectangle(-1_200, 0, 1_200, 900),
            insets = Insets(25, 0, 40, 0)
        )

        assertEquals(Point(-1_050, 600), location)
    }
}

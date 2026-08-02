package dev.mjablonski.rewordme.app

import androidx.compose.ui.input.key.Key
import dev.mjablonski.rewordme.models.HotkeyConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * What the recorder makes of a key press, driven through [HotkeyRecorder.decide]
 * rather than a Compose KeyEvent - that type wraps an internal event with no
 * public constructor, so going through it would need a real focused window.
 */
class HotkeyRecorderTests {

    private fun press(
        key: Key,
        ctrl: Boolean = false,
        alt: Boolean = false,
        shift: Boolean = false,
        meta: Boolean = false,
        isKeyDown: Boolean = true
    ): RecorderOutcome = HotkeyRecorder.decide(isKeyDown, key, ctrl, alt, shift, meta)

    @Test
    fun `ctrl alt letter is recorded with the right mask, key and label`() {
        val recorded = assertIs<RecorderOutcome.Recorded>(
            press(Key.J, ctrl = true, alt = true)
        )

        assertEquals(
            HotkeyConfig.MOD_CONTROL or HotkeyConfig.MOD_ALT,
            recorded.hotkey.modifiers
        )
        assertEquals(0x4A, recorded.hotkey.virtualKey, "J is 0x4A")
        assertEquals("Ctrl+Alt+J", recorded.hotkey.display)
    }

    @Test
    fun `escape cancels`() {
        assertIs<RecorderOutcome.Cancelled>(press(Key.Escape))
    }

    /** Shift+letter is ordinary typing, so such a shortcut would fire constantly. */
    @Test
    fun `shift alone is not enough to anchor a shortcut`() {
        assertIs<RecorderOutcome.Ignored>(press(Key.R, shift = true))
    }

    @Test
    fun `a bare letter is ignored`() {
        assertIs<RecorderOutcome.Ignored>(press(Key.R))
    }

    /** These arrive while the user is still reaching for the real key. */
    @Test
    fun `a modifier pressed on its own is ignored`() {
        assertIs<RecorderOutcome.Ignored>(press(Key.CtrlLeft, ctrl = true))
        assertIs<RecorderOutcome.Ignored>(press(Key.AltLeft, alt = true))
        assertIs<RecorderOutcome.Ignored>(press(Key.ShiftLeft, shift = true))
        assertIs<RecorderOutcome.Ignored>(press(Key.MetaLeft, meta = true))
    }

    @Test
    fun `key up is ignored, so only the press counts`() {
        assertIs<RecorderOutcome.Ignored>(
            press(Key.J, ctrl = true, alt = true, isKeyDown = false)
        )
    }

    @Test
    fun `shift is carried when it rides along with a real anchor`() {
        val recorded = assertIs<RecorderOutcome.Recorded>(
            press(Key.R, ctrl = true, shift = true)
        )

        assertEquals(
            HotkeyConfig.MOD_CONTROL or HotkeyConfig.MOD_SHIFT,
            recorded.hotkey.modifiers
        )
        assertEquals("Ctrl+Shift+R", recorded.hotkey.display)
    }

    @Test
    fun `the windows key anchors a shortcut on its own`() {
        val recorded = assertIs<RecorderOutcome.Recorded>(press(Key.R, meta = true))

        assertEquals(HotkeyConfig.MOD_WIN, recorded.hotkey.modifiers)
        assertEquals("Win+R", recorded.hotkey.display)
    }

    /**
     * Enter, Delete and Insert are where the AWT code and the Win32 virtual key
     * disagree, which is why the recorder spells them out rather than trusting
     * nativeKeyCode.
     */
    @Test
    fun `enter maps to the win32 virtual key, not the awt one`() {
        val recorded = assertIs<RecorderOutcome.Recorded>(
            press(Key.Enter, ctrl = true, alt = true)
        )

        assertEquals(0x0D, recorded.hotkey.virtualKey)
        assertEquals("Ctrl+Alt+Enter", recorded.hotkey.display)
    }

    @Test
    fun `delete maps to the win32 virtual key`() {
        val recorded = assertIs<RecorderOutcome.Recorded>(
            press(Key.Delete, ctrl = true, alt = true)
        )

        assertEquals(0x2E, recorded.hotkey.virtualKey)
        assertEquals("Ctrl+Alt+Del", recorded.hotkey.display)
    }

    @Test
    fun `function keys are bindable`() {
        val recorded = assertIs<RecorderOutcome.Recorded>(press(Key.F5, ctrl = true))

        assertEquals(0x74, recorded.hotkey.virtualKey)
        assertEquals("Ctrl+F5", recorded.hotkey.display)
    }

    @Test
    fun `digits are bindable`() {
        val recorded = assertIs<RecorderOutcome.Recorded>(press(Key.Seven, alt = true))

        assertEquals(0x37, recorded.hotkey.virtualKey)
        assertEquals("Alt+7", recorded.hotkey.display)
    }

    @Test
    fun `every modifier at once is carried in the display order macOS uses`() {
        val recorded = assertIs<RecorderOutcome.Recorded>(
            press(Key.R, ctrl = true, alt = true, shift = true, meta = true)
        )

        assertEquals("Ctrl+Alt+Shift+Win+R", recorded.hotkey.display)
    }
}

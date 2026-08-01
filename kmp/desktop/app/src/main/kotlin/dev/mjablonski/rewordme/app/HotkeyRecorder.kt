package dev.mjablonski.rewordme.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import dev.mjablonski.rewordme.models.HotkeyConfig

/** What a key press during recording means. */
sealed interface RecorderOutcome {
    data class Recorded(val hotkey: HotkeyConfig) : RecorderOutcome
    data object Cancelled : RecorderOutcome

    /** Not a usable combination; stay in recording mode and wait for another. */
    data object Ignored : RecorderOutcome
}

/**
 * Turns a Compose key event into a RegisterHotKey combination. Shift alone is
 * rejected because Shift+letter is ordinary typing, which would make the
 * shortcut fire constantly.
 */
object HotkeyRecorder {
    fun interpret(event: KeyEvent): RecorderOutcome {
        if (event.type != KeyEventType.KeyDown) return RecorderOutcome.Ignored
        if (event.key == Key.Escape) return RecorderOutcome.Cancelled
        if (event.key in MODIFIER_KEYS) return RecorderOutcome.Ignored

        var modifiers = 0
        if (event.isCtrlPressed) modifiers = modifiers or HotkeyConfig.MOD_CONTROL
        if (event.isAltPressed) modifiers = modifiers or HotkeyConfig.MOD_ALT
        if (event.isShiftPressed) modifiers = modifiers or HotkeyConfig.MOD_SHIFT
        if (event.isMetaPressed) modifiers = modifiers or HotkeyConfig.MOD_WIN

        val anchored = modifiers and
            (HotkeyConfig.MOD_CONTROL or HotkeyConfig.MOD_ALT or HotkeyConfig.MOD_WIN)
        if (anchored == 0) return RecorderOutcome.Ignored

        val bindable = bindable(event.key) ?: return RecorderOutcome.Ignored
        return RecorderOutcome.Recorded(
            HotkeyConfig(
                modifiers = modifiers,
                virtualKey = bindable.virtualKey,
                display = display(modifiers, bindable.label)
            )
        )
    }

    private fun display(modifiers: Int, keyLabel: String): String = buildList {
        if (modifiers and HotkeyConfig.MOD_CONTROL != 0) add("Ctrl")
        if (modifiers and HotkeyConfig.MOD_ALT != 0) add("Alt")
        if (modifiers and HotkeyConfig.MOD_SHIFT != 0) add("Shift")
        if (modifiers and HotkeyConfig.MOD_WIN != 0) add("Win")
        add(keyLabel)
    }.joinToString("+")

    private class Bindable(val virtualKey: Int, val label: String)

    /**
     * Null for keys RegisterHotKey cannot bind sensibly - dead keys and the
     * modifier-only presses that arrive while the user is still reaching for
     * the real key.
     *
     * The codes are spelled out because Key.nativeKeyCode is an AWT key code,
     * which disagrees with the Win32 virtual key RegisterHotKey wants for
     * Enter, Delete and Insert.
     */
    private fun bindable(key: Key): Bindable? = when (key) {
        Key.Spacebar -> Bindable(0x20, "Space")
        Key.Tab -> Bindable(0x09, "Tab")
        Key.Enter -> Bindable(0x0D, "Enter")
        Key.Backspace -> Bindable(0x08, "Backspace")
        Key.Delete -> Bindable(0x2E, "Del")
        Key.Insert -> Bindable(0x2D, "Ins")
        Key.Home -> Bindable(0x24, "Home")
        Key.MoveEnd -> Bindable(0x23, "End")
        Key.PageUp -> Bindable(0x21, "PgUp")
        Key.PageDown -> Bindable(0x22, "PgDn")
        Key.DirectionLeft -> Bindable(0x25, "Left")
        Key.DirectionUp -> Bindable(0x26, "Up")
        Key.DirectionRight -> Bindable(0x27, "Right")
        Key.DirectionDown -> Bindable(0x28, "Down")
        // Letters, digits and function keys number identically in both.
        else -> {
            val code = key.nativeKeyCode
            when {
                code in CODE_0..CODE_9 -> Bindable(code, ('0' + (code - CODE_0)).toString())
                code in CODE_A..CODE_Z -> Bindable(code, ('A' + (code - CODE_A)).toString())
                code in CODE_F1..CODE_F12 -> Bindable(code, "F${code - CODE_F1 + 1}")
                else -> null
            }
        }
    }

    private const val CODE_0 = 0x30
    private const val CODE_9 = 0x39
    private const val CODE_A = 0x41
    private const val CODE_Z = 0x5A
    private const val CODE_F1 = 0x70
    private const val CODE_F12 = 0x7B

    private val MODIFIER_KEYS = setOf(
        Key.CtrlLeft, Key.CtrlRight,
        Key.AltLeft, Key.AltRight,
        Key.ShiftLeft, Key.ShiftRight,
        Key.MetaLeft, Key.MetaRight
    )
}

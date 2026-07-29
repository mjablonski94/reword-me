import AppKit
import ApplicationServices
import RewordMeAppSupport

/// Puts the reworded text back where the selection was.
/// Sets AXSelectedText directly when the focused element allows it;
/// otherwise pastes over the selection and restores the clipboard.
enum TextReplacer {
    static func replaceSelection(with text: String) {
        if replaceViaAccessibility(text) { return }
        replaceViaPaste(text)
    }

    private static func replaceViaAccessibility(_ text: String) -> Bool {
        let systemWide = AXUIElementCreateSystemWide()
        var focusedRef: CFTypeRef?
        guard AXUIElementCopyAttributeValue(
            systemWide,
            kAXFocusedUIElementAttribute as CFString,
            &focusedRef
        ) == .success else {
            return false
        }
        let focused = focusedRef as! AXUIElement

        var settable = DarwinBoolean(false)
        guard AXUIElementIsAttributeSettable(
            focused,
            kAXSelectedTextAttribute as CFString,
            &settable
        ) == .success, settable.boolValue else {
            return false
        }

        return AXUIElementSetAttributeValue(
            focused,
            kAXSelectedTextAttribute as CFString,
            text as CFString
        ) == .success
    }

    private static func replaceViaPaste(_ text: String) {
        let pasteboard = NSPasteboard.general
        let snapshot = PasteboardSnapshot.capture()
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)

        KeySynthesizer.postCommandShortcut(keyCode: 9) // Cmd+V

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            snapshot.restore()
        }
    }
}

enum KeySynthesizer {
    /// Posts Cmd+<key> to the frontmost app (keycode 8 = C, 9 = V).
    static func postCommandShortcut(keyCode: CGKeyCode) {
        let source = CGEventSource(stateID: .combinedSessionState)
        guard let keyDown = CGEvent(keyboardEventSource: source, virtualKey: keyCode, keyDown: true),
              let keyUp = CGEvent(keyboardEventSource: source, virtualKey: keyCode, keyDown: false) else {
            return
        }
        keyDown.flags = .maskCommand
        keyUp.flags = .maskCommand
        keyDown.post(tap: .cghidEventTap)
        keyUp.post(tap: .cghidEventTap)
    }
}

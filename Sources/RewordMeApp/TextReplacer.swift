import AppKit
import ApplicationServices
import RewordMeAppSupport

/// Puts the reworded text back where the selection was.
@MainActor
protocol TextReplacing {
    func replaceSelection(with text: String)
}

/// Sets AXSelectedText directly when the focused element allows it;
/// otherwise pastes over the selection and restores the clipboard.
@MainActor
final class AXTextReplacer: TextReplacing {
    private let keySynthesizer: KeySynthesizing

    init(keySynthesizer: KeySynthesizing = CGKeySynthesizer()) {
        self.keySynthesizer = keySynthesizer
    }

    func replaceSelection(with text: String) {
        if replaceViaAccessibility(text) { return }
        replaceViaPaste(text)
    }

    private func replaceViaAccessibility(_ text: String) -> Bool {
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

    private func replaceViaPaste(_ text: String) {
        let pasteboard = NSPasteboard.general
        let snapshot = PasteboardSnapshot.capture()
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)

        keySynthesizer.postCommandShortcut(keyCode: 9) // Cmd+V

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            snapshot.restore()
        }
    }
}

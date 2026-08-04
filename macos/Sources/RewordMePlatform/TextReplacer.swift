import AppKit
import ApplicationServices

/// Puts the reworded text back where the selection was.
@MainActor
public protocol TextReplacing {
    func replaceSelection(with text: String)
}

enum ClipboardRestorePolicy {
    static func shouldRestore(temporaryChangeCount: Int, currentChangeCount: Int) -> Bool {
        temporaryChangeCount == currentChangeCount
    }
}

/// Sets AXSelectedText directly when the focused element allows it;
/// otherwise pastes over the selection and restores the clipboard.
@MainActor
public final class AXTextReplacer: TextReplacing {
    private let keySynthesizer: KeySynthesizing

    public init(keySynthesizer: KeySynthesizing = CGKeySynthesizer()) {
        self.keySynthesizer = keySynthesizer
    }

    public func replaceSelection(with text: String) {
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
        let temporaryChangeCount = pasteboard.changeCount

        keySynthesizer.postCommandShortcut(keyCode: 9) // Cmd+V

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            // A copy made by the user after RewordMe pasted is newer than our
            // snapshot and must win. NSPasteboard's change count is monotonic.
            if ClipboardRestorePolicy.shouldRestore(
                temporaryChangeCount: temporaryChangeCount,
                currentChangeCount: pasteboard.changeCount
            ) {
                snapshot.restore()
            }
        }
    }
}

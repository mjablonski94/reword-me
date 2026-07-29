import AppKit
import ApplicationServices

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

/// Saves and restores the full pasteboard contents so the copy/paste
/// fallbacks never clobber whatever the user had on the clipboard.
struct PasteboardSnapshot {
    private let items: [[NSPasteboard.PasteboardType: Data]]

    static func capture() -> PasteboardSnapshot {
        let items = (NSPasteboard.general.pasteboardItems ?? []).map { item in
            var entry: [NSPasteboard.PasteboardType: Data] = [:]
            for type in item.types {
                if let data = item.data(forType: type) {
                    entry[type] = data
                }
            }
            return entry
        }
        return PasteboardSnapshot(items: items)
    }

    func restore() {
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        guard !items.isEmpty else { return }
        let restored = items.map { entry in
            let item = NSPasteboardItem()
            for (type, data) in entry {
                item.setData(data, forType: type)
            }
            return item
        }
        pasteboard.writeObjects(restored)
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

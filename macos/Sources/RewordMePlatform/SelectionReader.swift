import AppKit
import ApplicationServices

enum ClipboardCopyPolicy {
    static func observedSyntheticCopy(baseline: Int, current: Int) -> Bool {
        baseline != current
    }
}

/// Posts keyboard shortcuts to the frontmost app.
public protocol KeySynthesizing {
    func postCommandShortcut(keyCode: CGKeyCode)
}

public struct CGKeySynthesizer: KeySynthesizing {
    public init() {}

    /// Cmd+<key> (keycode 8 = C, 9 = V).
    public func postCommandShortcut(keyCode: CGKeyCode) {
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

/// Reads the selected text from whatever app is frontmost.
@MainActor
public protocol SelectionReading {
    func readSelection(completion: @escaping @MainActor (String?, CGRect?) -> Void)
}

/// Accessibility API first (clean, no clipboard involved); a synthesized
/// Cmd+C with clipboard save/restore as the fallback for apps that don't
/// expose AXSelectedText (Electron apps, browser web content).
@MainActor
public final class AXSelectionReader: SelectionReading {
    private let keySynthesizer: KeySynthesizing

    public init(keySynthesizer: KeySynthesizing = CGKeySynthesizer()) {
        self.keySynthesizer = keySynthesizer
    }

    public func readSelection(completion: @escaping @MainActor (String?, CGRect?) -> Void) {
        let axResult = selectionViaAccessibility()
        if let text = axResult.text, !text.isEmpty {
            completion(text, axResult.bounds)
            return
        }
        selectionViaClipboard { text in
            completion(text, axResult.bounds)
        }
    }

    // MARK: - Accessibility path

    private func selectionViaAccessibility() -> (text: String?, bounds: CGRect?) {
        let systemWide = AXUIElementCreateSystemWide()
        var focusedRef: CFTypeRef?
        guard AXUIElementCopyAttributeValue(
            systemWide,
            kAXFocusedUIElementAttribute as CFString,
            &focusedRef
        ) == .success else {
            return (nil, nil)
        }
        let focused = focusedRef as! AXUIElement

        var selectedRef: CFTypeRef?
        AXUIElementCopyAttributeValue(
            focused,
            kAXSelectedTextAttribute as CFString,
            &selectedRef
        )
        let text = selectedRef as? String

        return (text, selectionBounds(of: focused))
    }

    /// Screen rectangle of the selected range, in AppKit (bottom-left
    /// origin) coordinates, ready for window positioning.
    private func selectionBounds(of element: AXUIElement) -> CGRect? {
        var rangeRef: CFTypeRef?
        guard AXUIElementCopyAttributeValue(
            element,
            kAXSelectedTextRangeAttribute as CFString,
            &rangeRef
        ) == .success, let rangeRef, CFGetTypeID(rangeRef) == AXValueGetTypeID() else {
            return nil
        }

        var boundsRef: CFTypeRef?
        guard AXUIElementCopyParameterizedAttributeValue(
            element,
            kAXBoundsForRangeParameterizedAttribute as CFString,
            rangeRef,
            &boundsRef
        ) == .success, let boundsRef, CFGetTypeID(boundsRef) == AXValueGetTypeID() else {
            return nil
        }

        var rect = CGRect.zero
        guard AXValueGetValue(boundsRef as! AXValue, .cgRect, &rect), rect.width >= 0 else {
            return nil
        }

        // AX coordinates are top-left origin; flip to AppKit's bottom-left.
        guard let screenHeight = NSScreen.screens.first?.frame.height else { return nil }
        return CGRect(
            x: rect.origin.x,
            y: screenHeight - rect.origin.y - rect.height,
            width: rect.width,
            height: rect.height
        )
    }

    // MARK: - Clipboard fallback

    private func selectionViaClipboard(completion: @escaping @MainActor (String?) -> Void) {
        // The hotkey's own modifiers are usually still physically held
        // when we get here; a Cmd+C synthesized now reaches the app as
        // Option+Cmd+C and copies nothing. Capture the clipboard only after
        // release so a copy made by the user while waiting becomes the new
        // baseline instead of being mistaken for our synthetic Cmd+C.
        waitForModifierRelease(attemptsLeft: 20) { [keySynthesizer] in
            let pasteboard = NSPasteboard.general
            let snapshot = PasteboardSnapshot.capture(from: pasteboard)
            let changeCountBefore = pasteboard.changeCount
            keySynthesizer.postCommandShortcut(keyCode: 8) // Cmd+C
            self.pollForCopy(
                pasteboard: pasteboard,
                before: changeCountBefore,
                attemptsLeft: 20
            ) { copied, copyChangeCount in
                if let copyChangeCount,
                   ClipboardRestorePolicy.shouldRestore(
                       temporaryChangeCount: copyChangeCount,
                       currentChangeCount: pasteboard.changeCount
                   ) {
                    snapshot.restore()
                }
                completion(copied)
            }
        }
    }

    private func waitForModifierRelease(attemptsLeft: Int, then: @escaping @MainActor () -> Void) {
        let held = NSEvent.modifierFlags.intersection([.command, .option, .control, .shift])
        guard !held.isEmpty, attemptsLeft > 0 else {
            then()
            return
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
            self.waitForModifierRelease(attemptsLeft: attemptsLeft - 1, then: then)
        }
    }

    /// Slow apps (browsers, Electron) can take several hundred ms to
    /// write the clipboard - poll instead of a single fixed wait.
    private func pollForCopy(
        pasteboard: NSPasteboard,
        before: Int,
        attemptsLeft: Int,
        completion: @escaping @MainActor (String?, Int?) -> Void
    ) {
        let observedChangeCount = pasteboard.changeCount
        if ClipboardCopyPolicy.observedSyntheticCopy(
            baseline: before,
            current: observedChangeCount
        ) {
            let copied = pasteboard.string(forType: .string)
            // If ownership changed while the value was being materialized,
            // do not return unrelated newer text as the selection.
            guard pasteboard.changeCount == observedChangeCount else {
                completion(nil, nil)
                return
            }
            completion(copied, observedChangeCount)
            return
        }
        guard attemptsLeft > 0 else {
            completion(nil, nil)
            return
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.03) {
            self.pollForCopy(
                pasteboard: pasteboard,
                before: before,
                attemptsLeft: attemptsLeft - 1,
                completion: completion
            )
        }
    }
}

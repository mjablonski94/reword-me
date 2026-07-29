import AppKit
import ApplicationServices
import RewordMeAppSupport

/// Reads the selected text from whatever app is frontmost.
/// Accessibility API first (clean, no clipboard involved); a synthesized
/// Cmd+C with clipboard save/restore as the fallback for apps that don't
/// expose AXSelectedText (Electron apps, browser web content).
@MainActor
enum SelectionReader {
    static func readSelection(completion: @escaping @MainActor (String?, CGRect?) -> Void) {
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

    static func selectionViaAccessibility() -> (text: String?, bounds: CGRect?) {
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

    /// Screen rectangle of the selected range, in AppKit (bottom-left origin)
    /// coordinates, ready for window positioning.
    private static func selectionBounds(of element: AXUIElement) -> CGRect? {
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

    private static func selectionViaClipboard(completion: @escaping @MainActor (String?) -> Void) {
        let pasteboard = NSPasteboard.general
        let snapshot = PasteboardSnapshot.capture()
        let changeCountBefore = pasteboard.changeCount

        KeySynthesizer.postCommandShortcut(keyCode: 8) // Cmd+C

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            let copied: String? = pasteboard.changeCount != changeCountBefore
                ? pasteboard.string(forType: .string)
                : nil
            snapshot.restore()
            completion(copied)
        }
    }
}

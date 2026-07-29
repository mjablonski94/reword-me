import AppKit

/// Backs the "Reword with RewordMe" entry in the macOS Services menu
/// (right-click on selected text in any app). No Accessibility permission
/// needed on this path - the system hands us the selection directly.
final class ServicesProvider: NSObject {
    private let handler: @MainActor (String) -> Void

    init(handler: @escaping @MainActor (String) -> Void) {
        self.handler = handler
    }

    @objc func rewordSelection(
        _ pasteboard: NSPasteboard,
        userData: String?,
        error: AutoreleasingUnsafeMutablePointer<NSString>
    ) {
        guard let text = pasteboard.string(forType: .string),
              !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            error.pointee = "No text was selected." as NSString
            return
        }
        let handler = self.handler
        DispatchQueue.main.async {
            handler(text)
        }
    }
}

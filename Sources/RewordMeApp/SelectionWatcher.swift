import AppKit

/// PopClip-style trigger: watches global mouse-ups and fires when they
/// end with a fresh text selection. Needs the same Accessibility grant as
/// the hotkey path; events in RewordMe's own windows never reach a global
/// monitor, so the popup can't trigger itself.
@MainActor
final class SelectionWatcher {
    var onSelection: ((String, CGRect?) -> Void)?

    private var monitor: Any?
    private var lastText: String?

    var isRunning: Bool { monitor != nil }

    func start() {
        guard monitor == nil else { return }
        monitor = NSEvent.addGlobalMonitorForEvents(matching: [.leftMouseUp]) { [weak self] _ in
            // Give the host app a beat to commit the selection.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                self?.checkSelection()
            }
        }
    }

    func stop() {
        if let monitor {
            NSEvent.removeMonitor(monitor)
            self.monitor = nil
        }
        lastText = nil
    }

    private func checkSelection() {
        let result = SelectionReader.selectionViaAccessibility()
        let text = (result.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else {
            // Selection cleared - the next one should trigger again.
            lastText = nil
            return
        }
        guard text != lastText else { return }
        lastText = text
        onSelection?(text, result.bounds)
    }
}

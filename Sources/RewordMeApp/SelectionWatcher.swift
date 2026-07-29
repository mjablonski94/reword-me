import AppKit
import RewordMeCore

/// PopClip-style trigger: watches global mouse-ups and fires when they
/// end with a fresh text selection. Needs the same Accessibility grant as
/// the hotkey path; events in RewordMe's own windows never reach a global
/// monitor, so the popup can't trigger itself.
///
/// Two guards keep it calm: the selection must be meaningful text (see
/// SelectionFilter), and it must survive unchanged for a confirmation
/// delay - so dragging around, double-click noise and transient
/// selections never summon the popup.
@MainActor
final class SelectionWatcher {
    var onSelection: ((String, CGRect?) -> Void)?

    /// How long a selection must stay unchanged before the popup shows.
    private let confirmationDelay: TimeInterval = 1

    private var monitor: Any?
    private var lastTriggeredText: String?
    private var pending: DispatchWorkItem?

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
        pending?.cancel()
        pending = nil
        lastTriggeredText = nil
    }

    private func checkSelection() {
        let result = SelectionReader.selectionViaAccessibility()
        let text = (result.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)

        guard SelectionFilter.isMeaningful(text) else {
            // Selection cleared or is noise - reset so the next real
            // selection triggers again, and drop any pending popup.
            lastTriggeredText = nil
            pending?.cancel()
            pending = nil
            return
        }
        guard text != lastTriggeredText else { return }

        // Restart the confirmation timer for the new candidate.
        pending?.cancel()
        let work = DispatchWorkItem { [weak self] in
            MainActor.assumeIsolated {
                self?.confirmAndFire(candidate: text)
            }
        }
        pending = work
        DispatchQueue.main.asyncAfter(deadline: .now() + confirmationDelay, execute: work)
    }

    /// Fires only if the very same selection is still active after the
    /// delay - anything the user changed or cleared in the meantime is
    /// silently dropped.
    private func confirmAndFire(candidate: String) {
        pending = nil
        let result = SelectionReader.selectionViaAccessibility()
        let text = (result.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard text == candidate, SelectionFilter.isMeaningful(text) else { return }
        lastTriggeredText = text
        onSelection?(text, result.bounds)
    }
}

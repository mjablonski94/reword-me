import AppKit
import SwiftUI

/// The floating panel that appears over the selection. Non-activating is
/// the key property: it takes clicks and key input without deactivating
/// the host app, so the user's selection underneath stays alive.
final class RewordPanel: NSPanel {
    override var canBecomeKey: Bool { true }
    override var canBecomeMain: Bool { false }

    override func cancelOperation(_ sender: Any?) {
        close()
    }
}

@MainActor
final class PopupController {
    private var panel: RewordPanel?
    private var session: RewordSession?

    func present(text: String, near bounds: CGRect?) {
        dismiss()

        let session = RewordSession(original: text)
        session.onClose = { [weak self] in self?.dismiss() }
        self.session = session

        let view = PopupView(session: session)
        let hosting = NSHostingController(rootView: view)

        let panel = RewordPanel(
            contentRect: NSRect(x: 0, y: 0, width: 460, height: 280),
            styleMask: [.nonactivatingPanel, .borderless, .fullSizeContentView],
            backing: .buffered,
            defer: false
        )
        // The glass sheet draws its own shape; the panel itself is invisible.
        panel.isOpaque = false
        panel.backgroundColor = .clear
        panel.hasShadow = true
        panel.isFloatingPanel = true
        panel.level = .floating
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]
        panel.hidesOnDeactivate = false
        panel.becomesKeyOnlyIfNeeded = false
        panel.isMovableByWindowBackground = true
        panel.isReleasedWhenClosed = false
        hosting.view.wantsLayer = true
        panel.contentViewController = hosting
        self.panel = panel

        position(panel, near: bounds)
        panel.alphaValue = 0
        panel.makeKeyAndOrderFront(nil)
        NSAnimationContext.runAnimationGroup { context in
            context.duration = 0.18
            panel.animator().alphaValue = 1
        }

        session.generate()
    }

    func presentNoSelectionHint() {
        present(text: "", near: nil)
    }

    func dismiss() {
        session?.cancel()
        session = nil
        panel?.close()
        panel = nil
    }

    /// Below the selection when we know where it is, at the mouse otherwise.
    private func position(_ panel: NSPanel, near bounds: CGRect?) {
        let panelSize = panel.frame.size
        var origin: NSPoint
        if let bounds {
            origin = NSPoint(x: bounds.minX, y: bounds.minY - panelSize.height - 8)
        } else {
            let mouse = NSEvent.mouseLocation
            origin = NSPoint(x: mouse.x - panelSize.width / 2, y: mouse.y - panelSize.height - 16)
        }

        if let screen = screenContaining(origin) ?? NSScreen.main {
            let visible = screen.visibleFrame
            origin.x = min(max(origin.x, visible.minX + 8), visible.maxX - panelSize.width - 8)
            origin.y = min(max(origin.y, visible.minY + 8), visible.maxY - panelSize.height - 8)
        }
        panel.setFrameOrigin(origin)
    }

    private func screenContaining(_ point: NSPoint) -> NSScreen? {
        NSScreen.screens.first { $0.frame.contains(point) }
    }
}

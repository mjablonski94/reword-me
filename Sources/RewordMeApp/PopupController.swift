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
    private let dependencies: AppDependencies
    private var panel: RewordPanel?
    private var viewModel: RewordViewModel?
    private var anchor: CGRect?
    private var fallbackPoint: NSPoint = .zero
    private var resizeObserver: NSObjectProtocol?

    init(dependencies: AppDependencies) {
        self.dependencies = dependencies
    }

    func present(text: String, near bounds: CGRect?) {
        dismiss()
        anchor = bounds
        fallbackPoint = NSEvent.mouseLocation

        let viewModel = RewordViewModel(original: text, dependencies: dependencies)
        viewModel.onClose = { [weak self] in self?.dismiss() }
        viewModel.onReplace = { [weak self] replacement in
            self?.replaceWithAnimation(replacement)
        }
        self.viewModel = viewModel

        let view = PopupView(viewModel: viewModel)
        let hosting = NSHostingController(rootView: view)

        let panel = RewordPanel(
            contentRect: NSRect(x: 0, y: 0, width: 460, height: 280),
            styleMask: [.nonactivatingPanel, .borderless, .fullSizeContentView],
            backing: .buffered,
            defer: false
        )
        // The frosted sheet draws its own shape and shadow; the panel itself
        // is invisible. AppKit's window shadow must stay off - on a
        // transparent borderless panel it renders as a dark outline around
        // the full window bounds.
        panel.isOpaque = false
        panel.backgroundColor = .clear
        panel.hasShadow = false
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

        reposition()
        // Content height changes with state (loading -> result), so keep
        // the panel glued to the selection on every resize.
        resizeObserver = NotificationCenter.default.addObserver(
            forName: NSWindow.didResizeNotification,
            object: panel,
            queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.reposition() }
        }

        panel.alphaValue = 0
        panel.makeKeyAndOrderFront(nil)
        NSAnimationContext.runAnimationGroup { context in
            context.duration = 0.18
            panel.animator().alphaValue = 1
        }
        // Menu-first: nothing is generated until the user picks an action.
    }

    /// The replace animation: the panel shrinks and fades into the text
    /// it is about to replace, then the replacement lands.
    private func replaceWithAnimation(_ text: String) {
        guard let panel else {
            dependencies.textReplacer.replaceSelection(with: text)
            return
        }
        let frame = panel.frame
        let target: NSRect
        if let anchor {
            target = NSRect(x: anchor.midX - 20, y: anchor.midY - 12, width: 40, height: 24)
        } else {
            target = NSRect(x: frame.midX - 20, y: frame.midY - 12, width: 40, height: 24)
        }
        NSAnimationContext.runAnimationGroup({ context in
            context.duration = 0.22
            context.timingFunction = CAMediaTimingFunction(name: .easeIn)
            panel.animator().alphaValue = 0
            panel.animator().setFrame(target, display: false)
        }, completionHandler: {
            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                self.dismiss()
                self.dependencies.textReplacer.replaceSelection(with: text)
            }
        })
    }

    func presentNoSelectionHint() {
        present(text: "", near: nil)
    }

    func dismiss() {
        if let resizeObserver {
            NotificationCenter.default.removeObserver(resizeObserver)
            self.resizeObserver = nil
        }
        viewModel?.cancel()
        viewModel = nil
        panel?.close()
        panel = nil
    }

    /// Writing-Tools-style placement: beside the selection (left first,
    /// then right), top-aligned with it; below or above as fallbacks -
    /// never on top of the selected text, and always fully on screen.
    private func reposition() {
        guard let panel else { return }
        let size = panel.frame.size
        let gap: CGFloat = 12
        let margin: CGFloat = 8

        let reference = anchor ?? CGRect(
            x: fallbackPoint.x - size.width / 2,
            y: fallbackPoint.y,
            width: 0,
            height: 0
        )
        let referenceCenter = NSPoint(x: reference.midX, y: reference.midY)
        guard let visible = (screenContaining(referenceCenter) ?? NSScreen.main)?.visibleFrame else {
            return
        }

        var origin: NSPoint
        let leftX = reference.minX - gap - size.width
        let rightX = reference.maxX + gap
        let belowY = reference.minY - gap - size.height
        let sideY = reference.maxY - size.height // top-aligned with the selection

        if anchor != nil, leftX >= visible.minX + margin {
            origin = NSPoint(x: leftX, y: sideY)
        } else if anchor != nil, rightX + size.width <= visible.maxX - margin {
            origin = NSPoint(x: rightX, y: sideY)
        } else if belowY >= visible.minY + margin {
            origin = NSPoint(x: reference.minX, y: belowY)
        } else {
            origin = NSPoint(x: reference.minX, y: reference.maxY + gap)
        }

        origin.x = min(max(origin.x, visible.minX + margin), visible.maxX - size.width - margin)
        origin.y = min(max(origin.y, visible.minY + margin), visible.maxY - size.height - margin)
        panel.setFrameOrigin(origin)
    }

    private func screenContaining(_ point: NSPoint) -> NSScreen? {
        NSScreen.screens.first { $0.frame.contains(point) }
    }
}

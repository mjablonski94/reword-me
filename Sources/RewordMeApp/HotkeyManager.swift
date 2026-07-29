import AppKit
import Carbon.HIToolbox
import Foundation
import RewordMeCore

/// Registers the global reword hotkey via Carbon. RegisterEventHotKey
/// needs no special permission and works in every app; re-applying with a
/// new combination swaps the registration in place.
final class HotkeyManager {
    var onHotkey: (() -> Void)?

    private var hotKeyRef: EventHotKeyRef?
    private var eventHandler: EventHandlerRef?

    func apply(_ hotkey: HotkeyConfig) {
        installHandlerIfNeeded()
        if let hotKeyRef {
            UnregisterEventHotKey(hotKeyRef)
            self.hotKeyRef = nil
        }
        let hotKeyID = EventHotKeyID(signature: fourCharCode("RWMe"), id: 1)
        RegisterEventHotKey(
            hotkey.keyCode,
            hotkey.carbonModifiers,
            hotKeyID,
            GetEventDispatcherTarget(),
            0,
            &hotKeyRef
        )
    }

    private func installHandlerIfNeeded() {
        guard eventHandler == nil else { return }
        var eventType = EventTypeSpec(
            eventClass: OSType(kEventClassKeyboard),
            eventKind: UInt32(kEventHotKeyPressed)
        )
        let selfPointer = Unmanaged.passUnretained(self).toOpaque()
        InstallEventHandler(
            GetEventDispatcherTarget(),
            { _, _, userData -> OSStatus in
                guard let userData else { return noErr }
                let manager = Unmanaged<HotkeyManager>.fromOpaque(userData).takeUnretainedValue()
                DispatchQueue.main.async { manager.onHotkey?() }
                return noErr
            },
            1,
            &eventType,
            selfPointer,
            &eventHandler
        )
    }

    deinit {
        if let hotKeyRef { UnregisterEventHotKey(hotKeyRef) }
        if let eventHandler { RemoveEventHandler(eventHandler) }
    }

    private func fourCharCode(_ string: String) -> FourCharCode {
        string.utf8.reduce(0) { ($0 << 8) + FourCharCode($1) }
    }
}

extension HotkeyConfig {
    /// The Cocoa flags matching the stored Carbon mask, for menu items.
    var cocoaModifiers: NSEvent.ModifierFlags {
        var flags: NSEvent.ModifierFlags = []
        if carbonModifiers & Self.carbonCommand != 0 { flags.insert(.command) }
        if carbonModifiers & Self.carbonShift != 0 { flags.insert(.shift) }
        if carbonModifiers & Self.carbonOption != 0 { flags.insert(.option) }
        if carbonModifiers & Self.carbonControl != 0 { flags.insert(.control) }
        return flags
    }
}

/// Captures the next key combination typed while recording, for the
/// shortcut field in Settings. Esc cancels; a combination must include
/// Command, Option or Control so plain typing can't become the hotkey.
@MainActor
final class HotkeyRecorder: ObservableObject {
    @Published var isRecording = false

    private var monitor: Any?
    private var completion: ((HotkeyConfig) -> Void)?

    func begin(_ completion: @escaping (HotkeyConfig) -> Void) {
        cancel()
        self.completion = completion
        isRecording = true
        monitor = NSEvent.addLocalMonitorForEvents(matching: [.keyDown]) { [weak self] event in
            guard let self else { return event }
            return self.handle(event)
        }
    }

    func cancel() {
        if let monitor {
            NSEvent.removeMonitor(monitor)
            self.monitor = nil
        }
        isRecording = false
        completion = nil
    }

    private func handle(_ event: NSEvent) -> NSEvent? {
        if event.keyCode == UInt16(kVK_Escape) {
            cancel()
            return nil
        }
        let flags = event.modifierFlags.intersection([.command, .option, .control, .shift])
        guard !flags.intersection([.command, .option, .control]).isEmpty else {
            return nil // swallow plain keys, keep recording
        }

        var carbon: UInt32 = 0
        if flags.contains(.command) { carbon |= HotkeyConfig.carbonCommand }
        if flags.contains(.shift) { carbon |= HotkeyConfig.carbonShift }
        if flags.contains(.option) { carbon |= HotkeyConfig.carbonOption }
        if flags.contains(.control) { carbon |= HotkeyConfig.carbonControl }

        let raw = event.charactersIgnoringModifiers ?? ""
        let keyLabel: String
        if event.keyCode == UInt16(kVK_Space) {
            keyLabel = "Space"
        } else if let first = raw.uppercased().first, !first.isWhitespace {
            keyLabel = String(first)
        } else {
            keyLabel = "Key \(event.keyCode)"
        }

        var display = ""
        if flags.contains(.control) { display += "⌃" }
        if flags.contains(.option) { display += "⌥" }
        if flags.contains(.shift) { display += "⇧" }
        if flags.contains(.command) { display += "⌘" }
        display += keyLabel

        let lowered = raw.lowercased()
        let hotkey = HotkeyConfig(
            keyCode: UInt32(event.keyCode),
            carbonModifiers: carbon,
            character: lowered.count == 1 ? lowered : "",
            display: display
        )
        let captured = completion
        cancel()
        captured?(hotkey)
        return nil
    }
}

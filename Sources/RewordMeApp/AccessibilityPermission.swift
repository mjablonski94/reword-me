import AppKit
import ApplicationServices

enum AccessibilityPermission {
    static var isTrusted: Bool {
        AXIsProcessTrusted()
    }

    static func openSystemSettings() {
        let url = URL(
            string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility"
        )!
        NSWorkspace.shared.open(url)
    }

    /// Explains why the permission is needed before macOS asks for
    /// anything. Never shown unprompted - only from the menu item or the
    /// first hotkey press without the grant.
    @MainActor
    static func showOnboarding() {
        NSApp.activate(ignoringOtherApps: true)
        let alert = NSAlert()
        alert.messageText = Loc.axTitle
        alert.informativeText = Loc.axBody
        alert.alertStyle = .informational
        alert.addButton(withTitle: Loc.openSystemSettings)
        alert.addButton(withTitle: Loc.axNotNow)
        if alert.runModal() == .alertFirstButtonReturn {
            // Registers the app in the Accessibility list, then opens it.
            let options = [
                kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String: false
            ] as CFDictionary
            AXIsProcessTrustedWithOptions(options)
            openSystemSettings()
        }
    }
}

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
        alert.messageText = "Let RewordMe read your selection"
        alert.informativeText = """
        To grab the text you select and to replace it in place, RewordMe \
        needs macOS Accessibility access.

        Grant it in System Settings > Privacy & Security > Accessibility by \
        turning on RewordMe.

        Already enabled in the list? Then macOS is remembering a previous \
        build of RewordMe - remove the entry with the minus button and add \
        the current app again.

        Prefer not to? You can still right-click selected text and use \
        Services > Reword with RewordMe - that path needs no permission.
        """
        alert.alertStyle = .informational
        alert.addButton(withTitle: "Open System Settings")
        alert.addButton(withTitle: "Not Now")
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

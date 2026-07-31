import AppKit
import ApplicationServices

/// The Accessibility grant: status, System Settings, and the onboarding
/// explanation shown before macOS asks for anything.
@MainActor
protocol AccessibilityChecking {
    var isTrusted: Bool { get }
    func openSystemSettings()
    func showOnboarding()
}

@MainActor
struct SystemAccessibilityPermission: AccessibilityChecking {
    var isTrusted: Bool {
        AXIsProcessTrusted()
    }

    func openSystemSettings() {
        let url = URL(
            string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility"
        )!
        NSWorkspace.shared.open(url)
    }

    /// Never shown unprompted - only from the menu item or the first
    /// hotkey press without the grant.
    func showOnboarding() {
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

import AppKit
import RewordMePlatform

/// The explanation shown before macOS asks for anything - presentation on
/// top of the platform capability. Never shown unprompted: only from the
/// menu item or the first hotkey press without the grant.
@MainActor
struct AccessibilityOnboarding {
    let accessibility: any AccessibilityChecking

    func show() {
        NSApp.activate(ignoringOtherApps: true)
        let alert = NSAlert()
        alert.messageText = Loc.axTitle
        alert.informativeText = Loc.axBody
        alert.alertStyle = .informational
        alert.addButton(withTitle: Loc.openSystemSettings)
        alert.addButton(withTitle: Loc.axNotNow)
        if alert.runModal() == .alertFirstButtonReturn {
            accessibility.registerInAccessibilityList()
            accessibility.openSystemSettings()
        }
    }
}

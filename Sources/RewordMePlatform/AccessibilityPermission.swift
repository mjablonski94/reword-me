import AppKit
import ApplicationServices

/// The Accessibility grant: status, System Settings, and registration.
/// The onboarding explanation is presentation and lives in the app layer.
@MainActor
public protocol AccessibilityChecking {
    var isTrusted: Bool { get }
    func openSystemSettings()
    /// Puts the app into the Accessibility list (unticked) without any
    /// system prompt, so the user finds it ready to enable.
    func registerInAccessibilityList()
}

@MainActor
public struct SystemAccessibilityPermission: AccessibilityChecking {
    public init() {}

    public var isTrusted: Bool {
        AXIsProcessTrusted()
    }

    public func openSystemSettings() {
        let url = URL(
            string: "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility"
        )!
        NSWorkspace.shared.open(url)
    }

    public func registerInAccessibilityList() {
        let options = [
            kAXTrustedCheckOptionPrompt.takeUnretainedValue() as String: false
        ] as CFDictionary
        AXIsProcessTrustedWithOptions(options)
    }
}

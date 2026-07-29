import AppKit
import RewordMeCore
import SwiftUI

@MainActor
final class AppDelegate: NSObject, NSApplicationDelegate {
    private var statusItem: NSStatusItem!
    private let hotkeyManager = HotkeyManager()
    private let popupController = PopupController()
    private var servicesProvider: ServicesProvider!
    private var settingsWindowController: SettingsWindowController?

    func applicationDidFinishLaunching(_ notification: Notification) {
        setupStatusItem()
        setupHotkey()
        setupServicesProvider()
        AccessibilityPermission.promptIfNeeded()
    }

    // MARK: - Status item

    private func setupStatusItem() {
        statusItem = NSStatusBar.system.statusItem(withLength: NSStatusItem.variableLength)
        if let button = statusItem.button {
            button.image = NSImage(
                systemSymbolName: "arrow.trianglehead.2.clockwise.rotate.90",
                accessibilityDescription: "RewordMe"
            )
        }

        let menu = NSMenu()
        let rewordItem = NSMenuItem(
            title: "Reword Selection",
            action: #selector(rewordSelection),
            keyEquivalent: "r"
        )
        rewordItem.keyEquivalentModifierMask = [.command, .option]
        rewordItem.target = self
        menu.addItem(rewordItem)
        menu.addItem(.separator())
        let settingsItem = NSMenuItem(
            title: "Settings...",
            action: #selector(openSettings),
            keyEquivalent: ","
        )
        settingsItem.target = self
        menu.addItem(settingsItem)
        menu.addItem(.separator())
        menu.addItem(NSMenuItem(
            title: "Quit RewordMe",
            action: #selector(NSApplication.terminate(_:)),
            keyEquivalent: "q"
        ))
        statusItem.menu = menu
    }

    // MARK: - Triggers

    private func setupHotkey() {
        hotkeyManager.onHotkey = { [weak self] in
            self?.rewordSelection()
        }
        hotkeyManager.register()
    }

    private func setupServicesProvider() {
        servicesProvider = ServicesProvider { [weak self] text in
            self?.popupController.present(text: text, near: nil)
        }
        NSApp.servicesProvider = servicesProvider
        NSUpdateDynamicServices()
    }

    @objc private func rewordSelection() {
        SelectionReader.readSelection { [weak self] text, bounds in
            guard let self else { return }
            guard let text, !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                self.popupController.presentNoSelectionHint()
                return
            }
            self.popupController.present(text: text, near: bounds)
        }
    }

    // MARK: - Settings

    @objc private func openSettings() {
        if settingsWindowController == nil {
            settingsWindowController = SettingsWindowController()
        }
        settingsWindowController?.show()
    }
}

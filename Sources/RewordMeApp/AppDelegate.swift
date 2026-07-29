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
        setupMainMenu()
        setupStatusItem()
        setupHotkey()
        setupServicesProvider()
        // Deliberately no Accessibility prompt here: the grant is explained
        // and requested from the tray menu or on the first hotkey press.
    }

    /// The menu bar is never shown (accessory app), but Cmd+C/V/X/A and
    /// undo/redo in text fields only work when an Edit menu with the
    /// standard key equivalents exists on the main menu.
    private func setupMainMenu() {
        let mainMenu = NSMenu()

        let appMenuItem = NSMenuItem()
        let appMenu = NSMenu()
        appMenu.addItem(withTitle: "Quit RewordMe",
                        action: #selector(NSApplication.terminate(_:)),
                        keyEquivalent: "q")
        appMenuItem.submenu = appMenu
        mainMenu.addItem(appMenuItem)

        let editMenuItem = NSMenuItem()
        let editMenu = NSMenu(title: "Edit")
        editMenu.addItem(withTitle: "Undo", action: Selector(("undo:")), keyEquivalent: "z")
        let redo = NSMenuItem(title: "Redo", action: Selector(("redo:")), keyEquivalent: "z")
        redo.keyEquivalentModifierMask = [.command, .shift]
        editMenu.addItem(redo)
        editMenu.addItem(.separator())
        editMenu.addItem(withTitle: "Cut", action: #selector(NSText.cut(_:)), keyEquivalent: "x")
        editMenu.addItem(withTitle: "Copy", action: #selector(NSText.copy(_:)), keyEquivalent: "c")
        editMenu.addItem(withTitle: "Paste", action: #selector(NSText.paste(_:)), keyEquivalent: "v")
        editMenu.addItem(withTitle: "Select All",
                         action: #selector(NSText.selectAll(_:)),
                         keyEquivalent: "a")
        editMenuItem.submenu = editMenu
        mainMenu.addItem(editMenuItem)

        NSApp.mainMenu = mainMenu
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
        menu.delegate = self
        statusItem.menu = menu
    }

    /// Rebuilt on every open so the Accessibility hint appears and
    /// disappears with the actual grant.
    private func rebuildMenu(_ menu: NSMenu) {
        menu.removeAllItems()

        if !AccessibilityPermission.isTrusted {
            let grantItem = NSMenuItem(
                title: Loc.menuGrantAccess,
                action: #selector(showAccessibilityOnboarding),
                keyEquivalent: ""
            )
            grantItem.image = NSImage(
                systemSymbolName: "exclamationmark.triangle.fill",
                accessibilityDescription: "Warning"
            )
            grantItem.target = self
            menu.addItem(grantItem)
            menu.addItem(.separator())
        }

        let hotkey = ConfigStore().load().hotkey
        let rewordItem = NSMenuItem(
            title: Loc.menuReword,
            action: #selector(rewordSelection),
            keyEquivalent: hotkey.character
        )
        rewordItem.keyEquivalentModifierMask = hotkey.cocoaModifiers
        rewordItem.target = self
        menu.addItem(rewordItem)
        menu.addItem(.separator())
        let settingsItem = NSMenuItem(
            title: Loc.menuSettings,
            action: #selector(openSettings),
            keyEquivalent: ","
        )
        settingsItem.target = self
        menu.addItem(settingsItem)
        menu.addItem(.separator())
        let coffeeItem = NSMenuItem(
            title: Loc.menuBuyCoffee,
            action: #selector(openBuyMeACoffee),
            keyEquivalent: ""
        )
        coffeeItem.image = NSImage(
            systemSymbolName: "cup.and.saucer.fill",
            accessibilityDescription: "Buy Me a Coffee"
        )
        coffeeItem.target = self
        menu.addItem(coffeeItem)
        menu.addItem(NSMenuItem(
            title: Loc.menuQuit,
            action: #selector(NSApplication.terminate(_:)),
            keyEquivalent: "q"
        ))
    }

    @objc private func openBuyMeACoffee() {
        NSWorkspace.shared.open(URL(string: "https://buymeacoffee.com/kofcio94f")!)
    }

    @objc private func showAccessibilityOnboarding() {
        AccessibilityPermission.showOnboarding()
    }

    // MARK: - Triggers

    private func setupHotkey() {
        hotkeyManager.onHotkey = { [weak self] in
            self?.rewordSelection()
        }
        applyHotkey()
        NotificationCenter.default.addObserver(
            forName: .rewordConfigChanged,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated {
                self?.applyHotkey()
            }
        }
    }

    private func applyHotkey() {
        hotkeyManager.apply(ConfigStore().load().hotkey)
    }

    private func setupServicesProvider() {
        servicesProvider = ServicesProvider { [weak self] text in
            self?.popupController.present(text: text, near: nil)
        }
        NSApp.servicesProvider = servicesProvider
        NSUpdateDynamicServices()
    }

    @objc private func rewordSelection() {
        guard AccessibilityPermission.isTrusted else {
            AccessibilityPermission.showOnboarding()
            return
        }
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

extension AppDelegate: NSMenuDelegate {
    func menuNeedsUpdate(_ menu: NSMenu) {
        rebuildMenu(menu)
    }
}

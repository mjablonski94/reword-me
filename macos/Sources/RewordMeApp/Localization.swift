import Foundation
import RewordMeModels

/// Localized UI strings, resolved from the app bundle's
/// <lang>.lproj/Localizable.strings. The product name "RewordMe" and the
/// provider names stay untranslated, and so do the preset instructions
/// sent to the model (the core prompt keeps the text's own language).
enum Loc {
    // Menu
    static var menuGrantAccess: String { t("menu.grantAccess") }
    static var menuReword: String { t("menu.reword") }
    static var menuSettings: String { t("menu.settings") }
    static var menuBuyCoffee: String { t("menu.buyCoffee") }
    static var menuQuit: String { t("menu.quit") }

    // Popup
    static var describePlaceholder: String { t("popup.describePlaceholder") }
    static var proofread: String { t("popup.proofread") }
    static var rewrite: String { t("popup.rewrite") }
    static var friendly: String { t("popup.friendly") }
    static var professional: String { t("popup.professional") }
    static var concise: String { t("popup.concise") }
    static var rewording: String { t("popup.rewording") }
    static var cancel: String { t("popup.cancel") }
    static var again: String { t("popup.again") }
    static var copy: String { t("popup.copy") }
    static var replace: String { t("popup.replace") }
    static var tryAgain: String { t("popup.tryAgain") }
    static var errorTitle: String { t("popup.errorTitle") }
    static func noSelection(_ shortcut: String) -> String {
        String(format: t("popup.noSelection"), shortcut)
    }

    // Settings window
    static var settingsTitle: String { t("settings.windowTitle") }
    static var tabProvider: String { t("tab.provider") }
    static var tabRewriting: String { t("tab.rewriting") }
    static var tabGeneral: String { t("tab.general") }

    // Provider tab
    static var providerSection: String { t("provider.section") }
    static var apiKeySection: String { t("provider.apiKeySection") }
    static func getKey(_ console: String) -> String {
        String(format: t("provider.getKey"), console)
    }
    static var saveKey: String { t("provider.saveKey") }
    static var saved: String { t("provider.saved") }
    static var keychainCaption: String { t("provider.keychainCaption") }
    static var modelSection: String { t("provider.modelSection") }
    static var modelLabel: String { t("provider.modelLabel") }
    static var automaticModel: String { t("provider.automatic") }
    static func automaticHint(_ model: String) -> String {
        String(format: t("provider.automaticHint"), model)
    }
    static var noModels: String { t("provider.noModels") }
    static var loadModels: String { t("provider.loadModels") }
    static var ollamaSection: String { t("ollama.section") }
    static var ollamaBlurb: String { t("ollama.blurb") }
    static var ollamaServer: String { t("ollama.serverLabel") }
    static func ollamaCaption(_ defaultHost: String) -> String {
        String(format: t("ollama.caption"), defaultHost)
    }
    static func getOllama(_ site: String) -> String {
        String(format: t("ollama.getLink"), site)
    }

    // Rewriting tab
    static var rulesSection: String { t("rules.section") }
    static var rulesFooter: String { t("rules.footer") }
    static var addRule: String { t("rules.add") }
    static var rulePlaceholder: String { t("rules.placeholder") }
    static var ruleDo: String { t("rules.do") }
    static var ruleDont: String { t("rules.dont") }
    static var basePromptSection: String { t("base.section") }
    static var basePromptFooter: String { t("base.footer") }

    // General tab
    static var shortcutSection: String { t("shortcut.section") }
    static var shortcutLabel: String { t("shortcut.label") }
    static var shortcutRecording: String { t("shortcut.recording") }
    static var shortcutHintIdle: String { t("shortcut.hintIdle") }
    static var shortcutHintRecording: String { t("shortcut.hintRecording") }
    static var servicesHint: String { t("shortcut.services") }
    static var startupSection: String { t("general.startup") }
    static var launchAtLogin: String { t("general.launchAtLogin") }
    static var permissionsSection: String { t("general.permissions") }
    static var accessibility: String { t("general.accessibility") }
    static var granted: String { t("general.granted") }
    static var openSystemSettings: String { t("general.openSystemSettings") }
    static var accessibilityCaption: String { t("general.accessibilityCaption") }
    static var supportSection: String { t("general.support") }
    static var buyCoffee: String { t("general.buyCoffee") }

    // Keychain explainer
    static var keychainAlertTitle: String { t("keychain.alertTitle") }
    static var keychainAlertBody: String { t("keychain.alertBody") }
    static var gotIt: String { t("keychain.gotIt") }

    // Accessibility onboarding
    static var axTitle: String { t("ax.title") }
    static var axBody: String { t("ax.body") }
    static var axNotNow: String { t("ax.notNow") }

    // Provider errors, shown in the popup
    static func message(for error: RewordError) -> String {
        switch error {
        case .missingAPIKey:
            return t("error.missingKey")
        case .invalidAPIKey:
            return t("error.invalidKey")
        case let .rateLimited(retryAfter):
            if let retryAfter {
                return String(format: t("error.rateLimitedRetry"), retryAfter)
            }
            return t("error.rateLimited")
        case let .refused(explanation):
            return explanation ?? t("error.refused")
        case let .apiError(status, message):
            return String(format: t("error.api"), status, message)
        case .emptyResponse:
            return t("error.empty")
        case .invalidResponse:
            return t("error.invalidResponse")
        case .noModelAvailable:
            return t("error.noModel")
        }
    }

    private static func t(_ key: String) -> String {
        NSLocalizedString(key, bundle: .main, comment: "")
    }
}

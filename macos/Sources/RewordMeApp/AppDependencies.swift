import Foundation
import RewordMeData
import RewordMeDomain
import RewordMePlatform
import ServiceManagement

/// The composition root: every service is built exactly once, here, and
/// handed down through initializers. Nothing below this level reaches for
/// a singleton or constructs its own dependencies.
@MainActor
final class AppDependencies {
    let configStore: ConfigStore
    let keyStore: any APIKeyStore
    let rewordService: RewordService
    let accountProviderService: AccountProviderService
    let localModelManager: LocalModelManager
    let modelResolver: ModelResolver
    let selectionReader: any SelectionReading
    let textReplacer: any TextReplacing
    let accessibility: any AccessibilityChecking

    init(
        configStore: ConfigStore = ConfigStore(),
        keyStore: any APIKeyStore = KeychainAPIKeyStore(),
        rewordService: RewordService? = nil,
        accountProviderService: AccountProviderService = AccountProviderService(),
        localModelManager: LocalModelManager = LocalModelManager(),
        modelResolver: ModelResolver = ModelResolver(),
        selectionReader: (any SelectionReading)? = nil,
        textReplacer: (any TextReplacing)? = nil,
        accessibility: (any AccessibilityChecking)? = nil
    ) {
        self.configStore = configStore
        self.keyStore = keyStore
        self.accountProviderService = accountProviderService
        self.localModelManager = localModelManager
        self.rewordService = rewordService ?? RewordService(
            accountProviders: accountProviderService,
            localModel: localModelManager
        )
        self.modelResolver = modelResolver
        self.selectionReader = selectionReader ?? AXSelectionReader()
        self.textReplacer = textReplacer ?? AXTextReplacer()
        self.accessibility = accessibility ?? SystemAccessibilityPermission()

        registerForStartupOnFirstRun()
    }

    /// A menu-bar app that is not running cannot answer its shortcut, so
    /// RewordMe registers itself the first time it launches. The answer is
    /// recorded, so a user who switches it back off is never overridden on the
    /// next launch.
    private func registerForStartupOnFirstRun() {
        var config = configStore.load()
        guard config.launchAtLogin == nil else { return }
        try? SMAppService.mainApp.register()
        config.launchAtLogin = SMAppService.mainApp.status == .enabled
        try? configStore.save(config)
    }
}

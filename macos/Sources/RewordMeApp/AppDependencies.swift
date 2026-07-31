import Foundation
import RewordMeData
import RewordMeDomain
import RewordMePlatform

/// The composition root: every service is built exactly once, here, and
/// handed down through initializers. Nothing below this level reaches for
/// a singleton or constructs its own dependencies.
@MainActor
final class AppDependencies {
    let configStore: ConfigStore
    let keyStore: any APIKeyStore
    let rewordService: RewordService
    let modelResolver: ModelResolver
    let selectionReader: any SelectionReading
    let textReplacer: any TextReplacing
    let accessibility: any AccessibilityChecking

    init(
        configStore: ConfigStore = ConfigStore(),
        keyStore: any APIKeyStore = KeychainAPIKeyStore(),
        rewordService: RewordService = RewordService(),
        modelResolver: ModelResolver = ModelResolver(),
        selectionReader: (any SelectionReading)? = nil,
        textReplacer: (any TextReplacing)? = nil,
        accessibility: (any AccessibilityChecking)? = nil
    ) {
        self.configStore = configStore
        self.keyStore = keyStore
        self.rewordService = rewordService
        self.modelResolver = modelResolver
        self.selectionReader = selectionReader ?? AXSelectionReader()
        self.textReplacer = textReplacer ?? AXTextReplacer()
        self.accessibility = accessibility ?? SystemAccessibilityPermission()
    }
}

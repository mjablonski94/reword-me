package dev.mjablonski.rewordme.app

import dev.mjablonski.rewordme.data.FileApiKeyStore
import dev.mjablonski.rewordme.data.JsonConfigStore
import dev.mjablonski.rewordme.data.RewordService
import dev.mjablonski.rewordme.domain.ApiKeyStore
import dev.mjablonski.rewordme.domain.ConfigStore
import dev.mjablonski.rewordme.domain.ModelResolver
import dev.mjablonski.rewordme.domain.Rewording
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.platform.CredentialApiKeyStore
import dev.mjablonski.rewordme.platform.DevSelectionReader
import dev.mjablonski.rewordme.platform.DevTextReplacer
import dev.mjablonski.rewordme.platform.ForegroundTracker
import dev.mjablonski.rewordme.platform.SelectionReading
import dev.mjablonski.rewordme.platform.StartupRegistration
import dev.mjablonski.rewordme.platform.TextReplacing
import dev.mjablonski.rewordme.platform.WindowsSelectionReader
import dev.mjablonski.rewordme.platform.WindowsTextReplacer
import dev.mjablonski.rewordme.platform.isWindows

/**
 * The composition root: every service is built exactly once, here, and
 * handed down through constructors.
 */
class AppDependencies {
    val configStore: ConfigStore = JsonConfigStore()
    val keyStore: ApiKeyStore = if (isWindows) vaultKeyStore() else FileApiKeyStore()
    val rewordService: Rewording = RewordService()
    val modelResolver = ModelResolver()
    val foreground = ForegroundTracker()
    val selectionReader: SelectionReading =
        if (isWindows) WindowsSelectionReader() else DevSelectionReader()
    val textReplacer: TextReplacing =
        if (isWindows) WindowsTextReplacer(foreground) else DevTextReplacer()

    init {
        registerForStartupOnFirstRun()
    }

    /**
     * A tray app that is not running cannot answer its shortcut, so RewordMe
     * puts itself in the startup list the first time it launches. The answer is
     * recorded, so a user who switches it back off is never overridden on the
     * next launch. Does nothing in development, where the running command is
     * the JDK launcher rather than the packaged app.
     */
    private fun registerForStartupOnFirstRun() {
        val config = configStore.load()
        if (config.launchAtLogin != null || !StartupRegistration.isSupported) return
        StartupRegistration.isEnabled = true
        configStore.save(config.copy(launchAtLogin = StartupRegistration.isEnabled))
    }
}

/**
 * Moves keys from the phase-1 plaintext file into the Credential Manager. The
 * file survives a failed move, because losing the user's key is worse than
 * leaving it on disk for one more launch.
 */
private fun vaultKeyStore(): ApiKeyStore {
    val plaintext = FileApiKeyStore()
    val credentials = CredentialApiKeyStore()
    val moved = ProviderKind.entries.all { provider ->
        val key = plaintext.apiKey(provider) ?: return@all true
        credentials.setApiKey(provider, key) && credentials.apiKey(provider) == key
    }
    if (!moved) return plaintext
    plaintext.discard()
    return credentials
}

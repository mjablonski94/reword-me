package dev.mjablonski.rewordme.app

import dev.mjablonski.rewordme.data.FileApiKeyStore
import dev.mjablonski.rewordme.data.JsonConfigStore
import dev.mjablonski.rewordme.data.RewordService
import dev.mjablonski.rewordme.domain.ApiKeyStore
import dev.mjablonski.rewordme.domain.ConfigStore
import dev.mjablonski.rewordme.domain.ModelResolver
import dev.mjablonski.rewordme.domain.Rewording
import dev.mjablonski.rewordme.platform.DevSelectionReader
import dev.mjablonski.rewordme.platform.DevTextReplacer
import dev.mjablonski.rewordme.platform.ForegroundTracker
import dev.mjablonski.rewordme.platform.SelectionReading
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
    val keyStore: ApiKeyStore = FileApiKeyStore()
    val rewordService: Rewording = RewordService()
    val modelResolver = ModelResolver()
    val foreground = ForegroundTracker()
    val selectionReader: SelectionReading =
        if (isWindows) WindowsSelectionReader() else DevSelectionReader()
    val textReplacer: TextReplacing =
        if (isWindows) WindowsTextReplacer(foreground) else DevTextReplacer()
}

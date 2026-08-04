package dev.mjablonski.rewordme.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.mjablonski.rewordme.domain.ApiKeyStore
import dev.mjablonski.rewordme.domain.ConfigStore
import dev.mjablonski.rewordme.domain.Rewording
import dev.mjablonski.rewordme.models.ModelInfo
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordConfig
import dev.mjablonski.rewordme.models.RewriteRule
import dev.mjablonski.rewordme.platform.WindowEffects
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

/**
 * Documentation screenshots of the real settings window - title bar, rounded
 * corners and all - which is the part no offscreen render can show.
 *
 * Everything is held in memory: this never reads the user's config or their
 * saved keys, and never registers anything for startup.
 *
 * `./gradlew :desktop:app:settingsShots`
 */
fun main(args: Array<String>) = application {
    val out = File(args.firstOrNull() ?: "settings-shots").apply { mkdirs() }
    val viewModel = remember {
        SettingsViewModel(
            configStore = InMemoryConfig(SHOWCASE),
            keyStore = InMemoryKeys(),
            rewordService = ShotModels,
            scope = CoroutineScope(Dispatchers.Default),
            hotkeys = ShotHotkeys
        )
    }
    var tab by remember { mutableStateOf(SettingsTab.PROVIDER) }

    Window(
        onCloseRequest = ::exitApplication,
        title = Strings["settings.windowTitle"],
        resizable = false,
        // Robot photographs the screen, not the window: whatever sits on top of
        // these coordinates is what lands in the file. Staying on top is what
        // makes the shot be of RewordMe and not of whatever else is open.
        alwaysOnTop = true,
        state = rememberWindowState(size = DpSize(560.dp, 512.dp))
    ) {
        LaunchedEffect(Unit) { WindowEffects.applyWindowChrome(window, dark = true) }
        // Keyed so each pass rebuilds the screen on the tab being shot; the tab
        // the content remembers is otherwise fixed at first composition.
        key(tab) { SettingsContent(viewModel, tab) }
    }

    // Outside the Window on purpose. Run inside it, this restarts whenever the
    // window recomposes, and two loops then race over which tab is on screen -
    // which is how a shot of one tab got written under another tab's name.
    LaunchedEffect(Unit) {
        viewModel.editApiKey(PLACEHOLDER_KEY)
        delay(1500)
        var failed = false
        for (entry in SettingsTab.entries) {
            tab = entry
            val frame = java.awt.Window.getWindows().firstOrNull { it.isShowing }
            if (frame == null) {
                println("REFUSED ${entry.name}: no window on screen")
                failed = true
                continue
            }
            frame.toFront()
            // Long enough for the frame to settle and DWM to paint the dark
            // title bar before the shot is taken.
            delay(1400)
            if (!capture(out, entry, frame)) failed = true
        }
        if (failed) error("Some captures were rejected; see above.")
        exitApplication()
    }
}

/**
 * Captures the window, and writes the file only once the pixels prove it really
 * is the settings window. A screen grab of the wrong window would otherwise be
 * committed as documentation - and could carry whatever was on screen.
 */
private fun capture(out: File, tab: SettingsTab, window: java.awt.Window): Boolean {
    val bounds = window.bounds
    val shot = java.awt.Robot().createScreenCapture(bounds)

    var cardPixels = 0
    var selectedPixels = 0
    var selectedX = 0L
    for (y in 0 until shot.height) {
        for (x in 0 until shot.width) {
            when (shot.getRGB(x, y) and 0xFFFFFF) {
                CARD_RGB -> cardPixels++
                SEGMENT_SELECTED_RGB -> {
                    selectedPixels++
                    selectedX += x
                }
            }
        }
    }

    // Is it our window at all?
    if (cardPixels < MIN_CARD_PIXELS) {
        println(
            "REFUSED ${tab.name}: only $cardPixels card-coloured pixels, expected at " +
                "least $MIN_CARD_PIXELS - the window was probably not in front."
        )
        return false
    }

    // Is it the tab we think it is? The selected segment slides left to right
    // across the picker, so where it sits identifies the tab on screen.
    if (selectedPixels == 0) {
        println("REFUSED ${tab.name}: no selected tab segment found")
        return false
    }
    val centre = (selectedX / selectedPixels).toInt()
    val expected = expectedSelectionCentre(tab)
    if (centre !in expected) {
        println(
            "REFUSED ${tab.name}: selected segment centred at x=$centre, expected " +
                "$expected - a different tab was on screen."
        )
        return false
    }

    // Windows 11 reports an invisible resize border in the window bounds, so a
    // raw grab carries a strip of whatever is behind the window down each side.
    // The chrome colours give the real edges.
    var left = shot.width
    var right = 0
    var bottom = 0
    for (y in 0 until shot.height) {
        for (x in 0 until shot.width) {
            if ((shot.getRGB(x, y) and 0xFFFFFF) in CHROME_RGB) {
                if (x < left) left = x
                if (x > right) right = x
                if (y > bottom) bottom = y
            }
        }
    }
    val cropped = shot.getSubimage(left, 0, right - left + 1, bottom + 1)

    ImageIO.write(cropped, "png", File(out, "settings-${tab.name.lowercase()}.png"))
    println(
        "captured ${tab.name} ${cropped.width}x${cropped.height} " +
            "(from ${bounds.width}x${bounds.height}) segment x=$centre"
    )
    return true
}

/** The opaque fills that only the settings window paints. */
private val CHROME_RGB = setOf(CARD_RGB, 0x292B2E, 0x27292D)

/** Where the selected pill sits for each tab, in window pixels. */
private fun expectedSelectionCentre(tab: SettingsTab): IntRange = when (tab) {
    SettingsTab.PROVIDER -> 120..245
    SettingsTab.REWRITING -> 246..324
    SettingsTab.GENERAL -> 325..450
}

/** Palette.card, the grouped-section fill that covers much of every tab. */
private const val CARD_RGB = 0x2F3134

/** Palette.segmentSelected, the raised pill behind the current tab. */
private const val SEGMENT_SELECTED_RGB = 0x525457
private const val MIN_CARD_PIXELS = 20_000

/**
 * Obviously not a key: the field is masked, so only the length shows, and a
 * screenshot must never carry anything that resembles real credentials.
 */
private const val PLACEHOLDER_KEY = "0000000000000000000000000000"

private val SHOWCASE = RewordConfig(
    provider = ProviderKind.ANTHROPIC,
    rules = listOf(
        RewriteRule(text = "Never use exclamation marks"),
        RewriteRule(text = "Keep it under two sentences", isEnabled = false)
    ),
    basePrompt = "I am a non-native speaker; fix grammar but keep my voice."
)

private class InMemoryConfig(private var config: RewordConfig) : ConfigStore {
    override fun load(): RewordConfig = config
    override fun save(config: RewordConfig) {
        this.config = config
    }
}

private class InMemoryKeys : ApiKeyStore {
    private val keys = mutableMapOf<ProviderKind, String>()
    override fun apiKey(provider: ProviderKind): String? = keys[provider]
    override fun setApiKey(provider: ProviderKind, key: String?): Boolean {
        if (key == null) keys.remove(provider) else keys[provider] = key
        return true
    }
}

private object ShotModels : Rewording {
    override suspend fun listModels(
        provider: ProviderKind,
        apiKey: String,
        endpoint: String?
    ): List<ModelInfo> = emptyList()

    override suspend fun reword(
        provider: ProviderKind,
        apiKey: String,
        model: String,
        systemPrompt: String,
        text: String,
        endpoint: String?
    ): String = ""
}

private object ShotHotkeys : HotkeyBinder {
    override fun bind(config: RewordConfig) = HotkeyStatus.Active
    override fun release() = Unit
}

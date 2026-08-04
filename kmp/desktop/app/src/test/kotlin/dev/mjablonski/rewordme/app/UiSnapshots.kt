package dev.mjablonski.rewordme.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import dev.mjablonski.rewordme.domain.ApiKeyStore
import dev.mjablonski.rewordme.domain.ConfigStore
import dev.mjablonski.rewordme.domain.Rewording
import dev.mjablonski.rewordme.models.LocalModelCatalog
import dev.mjablonski.rewordme.models.ModelInfo
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordConfig
import dev.mjablonski.rewordme.models.RewriteRule
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Renders every screen offscreen to a PNG so the layout can be reviewed
 * without a running app, a provider key or a live selection.
 *
 * `./gradlew :desktop:app:renderUi`
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "ui-snapshots").apply { mkdirs() }
    val dependencies = AppDependencies()
    val scope = CoroutineScope(Dispatchers.Default)

    fun popup(name: String, pose: PopupViewModel.() -> Unit) {
        val viewModel = PopupViewModel(dependencies, scope).apply(pose)
        snapshot(out, "popup-$name", 420, 520) {
            Box(Modifier.background(Color(0xB3121218), RoundedCornerShape(12.dp))) {
                PopupContent(viewModel)
            }
        }
    }

    popup("empty") { begin("") }
    popup("menu") { begin(SAMPLE) }
    popup("loading") {
        begin(SAMPLE)
        stage = PopupViewModel.Stage.LOADING
    }
    popup("result") {
        begin(SAMPLE)
        result = REWORDED
        modelLabel = "gemini-3.5-flash-lite · Gemini (Recommended)"
        stage = PopupViewModel.Stage.RESULT
    }
    popup("failed") {
        begin(SAMPLE)
        errorMessage = "No API key saved for Gemini."
        stage = PopupViewModel.Stage.FAILED
    }

    val settings = SettingsViewModel(dependencies, scope, NoHotkeys)
    SettingsTab.entries.forEach { tab ->
        snapshot(out, "settings-${tab.name.lowercase()}", 576, 536) {
            // Pinned to the real window size: the screen fills its window, so
            // an unconstrained render says nothing about the actual layout.
            Box(Modifier.size(560.dp, 520.dp)) { SettingsContent(settings, tab) }
        }
    }

    val offlineConfig = RewordConfig(
        provider = ProviderKind.LOCAL,
        modelsByProvider = mapOf(ProviderKind.LOCAL.id to LocalModelCatalog.DEFAULT.id)
    )
    val offlineSettings = SettingsViewModel(
        configStore = SnapshotConfigStore(offlineConfig),
        keyStore = SnapshotKeyStore,
        rewordService = SnapshotRewording,
        scope = scope,
        hotkeys = NoHotkeys
    )
    snapshot(out, "settings-offline", 576, 650) {
        Box(Modifier.size(560.dp, 634.dp)) {
            SettingsContent(offlineSettings, SettingsTab.PROVIDER)
        }
    }

    val rulesSettings = SettingsViewModel(
        configStore = SnapshotConfigStore(
            RewordConfig(
                rules = listOf(
                    RewriteRule(text = "Never use em dashes; use a hyphen instead."),
                    RewriteRule(text = "Keep the result under two sentences", isEnabled = false)
                ),
                basePrompt = "Preserve my direct, conversational voice."
            )
        ),
        keyStore = SnapshotKeyStore,
        rewordService = SnapshotRewording,
        scope = scope,
        hotkeys = NoHotkeys
    )
    snapshot(out, "settings-rules", 576, 650) {
        Box(Modifier.size(560.dp, 634.dp)) {
            SettingsContent(rulesSettings, SettingsTab.REWRITING)
        }
    }

    println("wrote ${out.absolutePath}")
}

@OptIn(ExperimentalComposeUiApi::class)
private fun snapshot(out: File, name: String, width: Int, height: Int, content: @Composable () -> Unit) {
    ImageComposeScene(width, height, Density(1f)) {
        // A busy backdrop, because the popup is translucent and a flat fill
        // would hide contrast problems the acrylic version really has.
        Box(
            Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF7A8CA8), Color(0xFF2A2530))))
                .padding(8.dp)
        ) {
            content()
        }
    }.use { scene ->
        val size = scene.calculateContentSize()
        println("$name content=${size.width}x${size.height} frame=${width}x$height")
        File(out, "$name.png").writeBytes(scene.render().encodeToData()!!.bytes)
    }
}

private object NoHotkeys : HotkeyBinder {
    override fun bind(config: RewordConfig) = HotkeyStatus.Active
    override fun release() = Unit
}

private class SnapshotConfigStore(private var config: RewordConfig) : ConfigStore {
    override fun load() = config
    override fun save(config: RewordConfig) {
        this.config = config
    }
}

private object SnapshotKeyStore : ApiKeyStore {
    override fun apiKey(provider: ProviderKind): String? = null
    override fun setApiKey(provider: ProviderKind, key: String?): Boolean = true
}

private object SnapshotRewording : Rewording {
    override suspend fun listModels(
        provider: ProviderKind,
        apiKey: String,
        endpoint: String?
    ): List<ModelInfo> = LocalModelCatalog.ALL.map { ModelInfo(it.id, it.displayName) }

    override suspend fun reword(
        provider: ProviderKind,
        apiKey: String,
        model: String,
        systemPrompt: String,
        text: String,
        endpoint: String?
    ): String = REWORDED
}

internal const val SAMPLE =
    "hey so i was thinkin maybe we could push the deadline back a bit becuase " +
        "the api stuff isnt done yet and i dont wanna ship somethin broken"

internal const val REWORDED =
    "Hi, I was thinking we might push the deadline back slightly. The API work " +
        "isn't finished yet, and I'd rather not ship something broken."

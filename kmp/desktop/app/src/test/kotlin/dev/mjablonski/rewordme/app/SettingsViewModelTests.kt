package dev.mjablonski.rewordme.app

import dev.mjablonski.rewordme.domain.ApiKeyStore
import dev.mjablonski.rewordme.domain.ConfigStore
import dev.mjablonski.rewordme.domain.Rewording
import dev.mjablonski.rewordme.models.HotkeyConfig
import dev.mjablonski.rewordme.models.ModelInfo
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordConfig
import dev.mjablonski.rewordme.models.RewriteRule
import dev.mjablonski.rewordme.models.RuleKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

/**
 * The settings state, against fakes for the three ports it talks to. Hand
 * written rather than mocked, matching the stubs the core tests already use -
 * and deliberately never building AppDependencies, which would open the real
 * config file and the real credential store.
 */
class SettingsViewModelTests {

    // MARK: - Fakes

    private class FakeConfigStore(var stored: RewordConfig = RewordConfig()) : ConfigStore {
        var saveCount = 0
        override fun load(): RewordConfig = stored
        override fun save(config: RewordConfig) {
            stored = config
            saveCount++
        }
    }

    private class FakeKeyStore(
        private val keys: MutableMap<ProviderKind, String> = mutableMapOf(),
        private val acceptsWrites: Boolean = true
    ) : ApiKeyStore {
        override fun apiKey(provider: ProviderKind): String? = keys[provider]
        override fun setApiKey(provider: ProviderKind, key: String?): Boolean {
            if (!acceptsWrites) return false
            if (key.isNullOrBlank()) keys.remove(provider) else keys[provider] = key
            return true
        }
    }

    private object UnusedRewording : Rewording {
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

    /** Records what the screen asked of the shortcut, and answers as told. */
    private class FakeHotkeys(var answer: HotkeyStatus = HotkeyStatus.Active) : HotkeyBinder {
        val bound = mutableListOf<HotkeyConfig>()
        var releases = 0
        override fun bind(config: RewordConfig): HotkeyStatus {
            bound += config.hotkey
            return answer
        }

        override fun release() {
            releases++
        }
    }

    private fun viewModel(
        config: RewordConfig = RewordConfig(),
        keyStore: ApiKeyStore = FakeKeyStore(),
        hotkeys: HotkeyBinder = FakeHotkeys(),
        rewording: Rewording = UnusedRewording,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined)
    ): Pair<SettingsViewModel, FakeConfigStore> {
        val store = FakeConfigStore(config)
        val model = SettingsViewModel(
            configStore = store,
            keyStore = keyStore,
            rewordService = rewording,
            scope = scope,
            hotkeys = hotkeys
        )
        return model to store
    }

    // MARK: - Shortcut recording

    @Test
    fun `a shortcut that binds is kept and saved`() {
        val hotkeys = FakeHotkeys(HotkeyStatus.Active)
        val (model, store) = viewModel(hotkeys = hotkeys)
        val recorded = HotkeyConfig(modifiers = 0x3, virtualKey = 0x4A, display = "Ctrl+Alt+J")

        model.applyRecorded(recorded)

        assertEquals(recorded, model.config.hotkey)
        assertEquals(recorded, store.stored.hotkey, "the new shortcut must reach disk")
        assertEquals(HotkeyStatus.Active, model.hotkeyStatus)
        assertFalse(model.isRecordingHotkey)
    }

    /**
     * The branch that matters most: a combination another app already owns must
     * leave the previous shortcut both configured *and* re-bound, or the user is
     * left with no way into the app at all.
     */
    @Test
    fun `a conflicting shortcut is rejected and the old one is rebound`() {
        val original = RewordConfig()
        val hotkeys = FakeHotkeys(HotkeyStatus.Conflict)
        val (model, store) = viewModel(config = original, hotkeys = hotkeys)

        model.applyRecorded(HotkeyConfig(modifiers = 0x2, virtualKey = 0x43, display = "Ctrl+C"))

        assertEquals(original.hotkey, model.config.hotkey, "the old shortcut must survive")
        assertEquals(0, store.saveCount, "a shortcut we could not claim must not be saved")
        assertEquals(HotkeyStatus.Conflict, model.hotkeyStatus)
        assertEquals(
            original.hotkey,
            hotkeys.bound.last(),
            "the last bind must restore the working shortcut"
        )
    }

    @Test
    fun `a shortcut that fails to register is treated like a conflict`() {
        val original = RewordConfig()
        val hotkeys = FakeHotkeys(HotkeyStatus.Failed)
        val (model, store) = viewModel(config = original, hotkeys = hotkeys)

        model.applyRecorded(HotkeyConfig(modifiers = 0x8, virtualKey = 0x51, display = "Win+Q"))

        assertEquals(original.hotkey, model.config.hotkey)
        assertEquals(0, store.saveCount)
        assertEquals(original.hotkey, hotkeys.bound.last())
    }

    @Test
    fun `recording releases the live shortcut so the OS cannot swallow it`() {
        val hotkeys = FakeHotkeys()
        val (model, _) = viewModel(hotkeys = hotkeys)

        model.beginRecording()

        assertTrue(model.isRecordingHotkey)
        assertEquals(1, hotkeys.releases)
    }

    @Test
    fun `cancelling recording rebinds the shortcut that was released`() {
        val hotkeys = FakeHotkeys()
        val (model, _) = viewModel(hotkeys = hotkeys)
        model.beginRecording()

        model.cancelRecording()

        assertFalse(model.isRecordingHotkey)
        assertEquals(RewordConfig().hotkey, hotkeys.bound.last())
    }

    // MARK: - Provider and key

    @Test
    fun `switching provider shows only that provider's model and restores it later`() {
        val (model, store) = viewModel(
            config = RewordConfig(provider = ProviderKind.ANTHROPIC, model = "claude-haiku-4-5")
        )

        model.selectProvider(ProviderKind.GEMINI)

        assertEquals(ProviderKind.GEMINI, model.config.provider)
        assertNull(model.config.selectedModel, "a model id from another provider is meaningless")
        model.selectModel("gemini-2.5-flash")
        model.selectProvider(ProviderKind.ANTHROPIC)
        assertEquals("claude-haiku-4-5", model.config.selectedModel)
        assertEquals(ProviderKind.ANTHROPIC, store.stored.provider)
    }

    @Test
    fun `switching provider loads that provider's own key`() {
        val keys = FakeKeyStore(
            mutableMapOf(ProviderKind.ANTHROPIC to "one", ProviderKind.GEMINI to "two")
        )
        val (model, _) = viewModel(
            config = RewordConfig(provider = ProviderKind.ANTHROPIC),
            keyStore = keys
        )
        assertEquals("one", model.apiKey)

        model.selectProvider(ProviderKind.GEMINI)

        assertEquals("two", model.apiKey)
    }

    @Test
    fun `selecting the provider already in use changes nothing`() {
        val (model, store) = viewModel(config = RewordConfig(provider = ProviderKind.ANTHROPIC))

        model.selectProvider(ProviderKind.ANTHROPIC)

        assertEquals(0, store.saveCount, "a no-op selection must not rewrite the config")
    }

    @Test
    fun `editing the key clears the saved acknowledgement`() {
        val (model, _) = viewModel()
        model.editApiKey("first")
        model.saveApiKey()
        assertTrue(model.keySaved)

        model.editApiKey("second")

        assertFalse(model.keySaved, "an edited key has not been saved yet")
    }

    @Test
    fun `saving the key writes it to the store under the current provider`() {
        val keys = FakeKeyStore()
        val (model, _) = viewModel(
            config = RewordConfig(provider = ProviderKind.MISTRAL),
            keyStore = keys
        )
        model.editApiKey("written")

        model.saveApiKey()

        assertEquals("written", keys.apiKey(ProviderKind.MISTRAL))
        assertTrue(model.keySaved)
    }

    @Test
    fun `clearing a saved key enables save and removes it from the store`() {
        val keys = FakeKeyStore(mutableMapOf(ProviderKind.MISTRAL to "existing"))
        val (model, _) = viewModel(
            config = RewordConfig(provider = ProviderKind.MISTRAL),
            keyStore = keys
        )
        assertFalse(model.canSaveApiKey)

        model.editApiKey("   ")

        assertTrue(model.canSaveApiKey)
        model.saveApiKey()
        assertNull(keys.apiKey(ProviderKind.MISTRAL))
        assertFalse(model.canSaveApiKey)
        assertTrue(model.keySaved)
    }

    @Test
    fun `a rejected key write is never reported as saved`() {
        val (model, _) = viewModel(keyStore = FakeKeyStore(acceptsWrites = false))
        model.editApiKey("cannot-write")

        model.saveApiKey()

        assertFalse(model.keySaved)
        assertEquals(Strings["provider.saveFailed"], model.keySaveError)
    }

    @Test
    fun `an old model request cannot clear or replace a newer provider request`() = runBlocking {
        val service = ControlledRewording()
        val (model, _) = viewModel(rewording = service, scope = this)

        model.loadModels()
        val first = withTimeout(2_000) { service.requests.receive() }
        model.selectProvider(ProviderKind.OLLAMA)
        model.loadModels()
        val second = withTimeout(2_000) { service.requests.receive() }

        yield()
        assertTrue(model.isLoadingModels, "cancellation from the old request must not stop the new spinner")
        assertEquals(ProviderKind.GEMINI, first.provider)
        assertEquals(ProviderKind.OLLAMA, second.provider)

        second.answer.complete(listOf(ModelInfo("z-local"), ModelInfo("a-local")))
        withTimeout(2_000) {
            while (model.isLoadingModels) yield()
        }

        assertEquals(listOf("z-local", "a-local"), model.availableModels.map { it.id })
        assertEquals("z-local", model.automaticModelHint, "Ollama automatic keeps server order")
        first.answer.complete(listOf(ModelInfo("stale")))
        yield()
        assertEquals(listOf("z-local", "a-local"), model.availableModels.map { it.id })
    }

    // MARK: - Rules

    @Test
    fun `a new rule is appended and persisted`() {
        val (model, store) = viewModel()

        model.addRule()

        assertEquals(1, model.config.rules.size)
        assertEquals(RuleKind.DO, model.config.rules.single().kind)
        assertEquals(1, store.saveCount)
    }

    @Test
    fun `removing a rule leaves the others alone`() {
        val keep = RewriteRule(kind = RuleKind.DO, text = "keep")
        val drop = RewriteRule(kind = RuleKind.DONT, text = "drop")
        val (model, store) = viewModel(config = RewordConfig(rules = listOf(keep, drop)))

        model.removeRule(drop.id)

        assertEquals(listOf(keep), model.config.rules)
        assertEquals(listOf(keep), store.stored.rules)
    }

    @Test
    fun `updating a rule rewrites only the one addressed`() {
        val first = RewriteRule(kind = RuleKind.DO, text = "first")
        val second = RewriteRule(kind = RuleKind.DO, text = "second")
        val (model, _) = viewModel(config = RewordConfig(rules = listOf(first, second)))

        model.updateRule(second.id) { it.copy(text = "edited", isEnabled = false) }

        assertEquals("first", model.config.rules[0].text)
        assertTrue(model.config.rules[0].isEnabled)
        assertEquals("edited", model.config.rules[1].text)
        assertFalse(model.config.rules[1].isEnabled)
    }

    // MARK: - Other edits

    @Test
    fun `the base prompt and ollama host are written straight through`() {
        val (model, store) = viewModel()

        model.setBasePrompt("keep my voice")
        model.setOllamaHost("http://box:11434")

        assertEquals("keep my voice", store.stored.basePrompt)
        assertEquals("http://box:11434", store.stored.ollamaHost)
    }

    /**
     * The switch records its answer so that first-run registration, which turns
     * startup on for a fresh install, cannot turn it back on afterwards. The
     * registry itself is untouched here: StartupRegistration reports
     * unsupported under the JDK launcher, so the setter is a no-op in tests.
     */
    @Test
    fun `the launch-at-login answer is written to the config`() {
        val (model, store) = viewModel()

        model.toggleLaunchAtLogin(false)

        assertEquals(false, store.stored.launchAtLogin, "an explicit off must stick")
    }

    @Test
    fun `a fresh config has no launch-at-login answer yet`() {
        assertNull(
            RewordConfig().launchAtLogin,
            "null is what tells first run to register the app for startup"
        )
    }

    @Test
    fun `choosing automatic clears the pinned model`() {
        val (model, store) = viewModel(config = RewordConfig(model = "gpt-5-nano"))

        model.selectModel(null)

        assertNull(model.config.selectedModel)
        assertNull(store.stored.selectedModel)
    }

    private class ControlledRewording : Rewording {
        data class Request(
            val provider: ProviderKind,
            val answer: CompletableDeferred<List<ModelInfo>> = CompletableDeferred()
        )

        val requests = Channel<Request>(Channel.UNLIMITED)

        override suspend fun listModels(
            provider: ProviderKind,
            apiKey: String,
            endpoint: String?
        ): List<ModelInfo> {
            val request = Request(provider)
            requests.send(request)
            return request.answer.await()
        }

        override suspend fun reword(
            provider: ProviderKind,
            apiKey: String,
            model: String,
            systemPrompt: String,
            text: String,
            endpoint: String?
        ): String = ""
    }
}

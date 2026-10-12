package dev.mjablonski.rewordme.android.features.settings.presentation

import dev.mjablonski.rewordme.android.common.UiError
import dev.mjablonski.rewordme.android.platform.AndroidProviderAvailability
import dev.mjablonski.rewordme.android.support.FakeConfigStore
import dev.mjablonski.rewordme.android.support.FakeFloatingIconSettings
import dev.mjablonski.rewordme.android.support.FakeKeyStore
import dev.mjablonski.rewordme.android.support.FakeRewording
import dev.mjablonski.rewordme.android.support.MainDispatcherRule
import dev.mjablonski.rewordme.domain.ModelResolver
import dev.mjablonski.rewordme.domain.usecase.HasApiKeyUseCase
import dev.mjablonski.rewordme.domain.usecase.ListModelsUseCase
import dev.mjablonski.rewordme.domain.usecase.LoadConfigUseCase
import dev.mjablonski.rewordme.domain.usecase.SaveApiKeyUseCase
import dev.mjablonski.rewordme.domain.usecase.UpdateConfigUseCase
import dev.mjablonski.rewordme.models.ModelInfo
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordConfig
import dev.mjablonski.rewordme.models.RewordError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle

class SettingsViewModelTests {

    private val mainDispatcher = MainDispatcherRule()

    private fun TestScope.viewModel(
        configStore: FakeConfigStore = FakeConfigStore(),
        keyStore: FakeKeyStore = FakeKeyStore(),
        service: FakeRewording = FakeRewording(),
        floatingIcon: FakeFloatingIconSettings = FakeFloatingIconSettings(),
        appScope: CoroutineScope = backgroundScope
    ): SettingsViewModel {
        val resolver = ModelResolver()
        val dispatcher = mainDispatcher.dispatcher
        return SettingsViewModel(
            loadConfig = LoadConfigUseCase(configStore, dispatcher),
            updateConfig = UpdateConfigUseCase(configStore, resolver, dispatcher),
            hasApiKey = HasApiKeyUseCase(keyStore, dispatcher),
            saveApiKey = SaveApiKeyUseCase(keyStore, resolver, dispatcher),
            listModels = ListModelsUseCase(configStore, keyStore, service, dispatcher),
            availability = AndroidProviderAvailability(),
            floatingIcon = floatingIcon,
            appScope = appScope
        )
    }

    @Test
    fun offersOnlyProvidersThisPlatformCanServe() = mainDispatcher.runVm {
        val vm = viewModel()
        val providers = vm.state.value.providers
        assertFalse(ProviderKind.CODEX in providers)
        assertFalse(ProviderKind.CLAUDE_ACCOUNT in providers)
        assertFalse(ProviderKind.LOCAL in providers)
        assertTrue(ProviderKind.OLLAMA in providers)
        assertTrue(ProviderKind.GEMINI in providers)
    }

    @Test
    fun initLoadsConfigKeyPresenceAndSeedsDrafts() = mainDispatcher.runVm {
        val configStore = FakeConfigStore(
            RewordConfig(provider = ProviderKind.OPENAI, basePrompt = "Base.")
        )
        val keyStore = FakeKeyStore(mutableMapOf(ProviderKind.OPENAI to "sk"))
        val vm = viewModel(configStore, keyStore)
        advanceUntilIdle()

        assertEquals(ProviderKind.OPENAI, vm.state.value.config.provider)
        assertTrue(vm.state.value.hasStoredKey)
        assertEquals("Base.", vm.state.value.basePromptDraft)
    }

    @Test
    fun aLateInitSnapshotNeverOverwritesAnEarlierUserEdit() = mainDispatcher.runVm {
        val configStore = FakeConfigStore()
        val vm = viewModel(configStore)
        // No advanceUntilIdle: the init load has not landed yet when the
        // user already switches provider.
        vm.onIntent(SettingsIntent.SelectProvider(ProviderKind.MISTRAL))
        advanceUntilIdle()

        assertEquals(ProviderKind.MISTRAL, vm.state.value.config.provider)
        assertEquals(ProviderKind.MISTRAL, configStore.config.provider)
    }

    @Test
    fun selectingAProviderPersistsAndClearsTheOldProvidersArtifacts() = mainDispatcher.runVm {
        val configStore = FakeConfigStore()
        val vm = viewModel(configStore)
        advanceUntilIdle()
        vm.onIntent(SettingsIntent.KeyChanged("draft"))
        vm.onIntent(SettingsIntent.SelectProvider(ProviderKind.MISTRAL))
        advanceUntilIdle()

        assertEquals(ProviderKind.MISTRAL, configStore.config.provider)
        assertEquals("", vm.state.value.keyDraft)
        assertTrue(vm.state.value.models.isEmpty())
    }

    @Test
    fun reselectingTheCurrentProviderIsANoOp() = mainDispatcher.runVm {
        val configStore = FakeConfigStore()
        val vm = viewModel(configStore)
        advanceUntilIdle()
        val savesBefore = configStore.saves
        vm.onIntent(SettingsIntent.KeyChanged("half-typed"))
        vm.onIntent(SettingsIntent.SelectProvider(vm.state.value.config.provider))
        advanceUntilIdle()

        assertEquals(savesBefore, configStore.saves)
        assertEquals("half-typed", vm.state.value.keyDraft)
    }

    @Test
    fun savingAKeyReportsPersistenceAndClearsTheDraft() = mainDispatcher.runVm {
        val keyStore = FakeKeyStore()
        val vm = viewModel(keyStore = keyStore)
        advanceUntilIdle()
        vm.onIntent(SettingsIntent.KeyChanged("AIza-new"))
        vm.onIntent(SettingsIntent.SaveKey)
        advanceUntilIdle()

        assertTrue(vm.state.value.keySaved)
        assertTrue(vm.state.value.hasStoredKey)
        assertEquals("", vm.state.value.keyDraft)
        assertEquals("AIza-new", keyStore.apiKey(ProviderKind.GEMINI))
    }

    @Test
    fun removeKeyClearsTheVaultAndTheStoredFlag() = mainDispatcher.runVm {
        val keyStore = FakeKeyStore(mutableMapOf(ProviderKind.GEMINI to "old"))
        val vm = viewModel(keyStore = keyStore)
        advanceUntilIdle()
        assertTrue(vm.state.value.hasStoredKey)

        vm.onIntent(SettingsIntent.RemoveKey)
        advanceUntilIdle()

        assertEquals(null, keyStore.apiKey(ProviderKind.GEMINI))
        assertFalse(vm.state.value.hasStoredKey)
        assertFalse(vm.state.value.keySaved)
    }

    @Test
    fun failedKeySaveKeepsTheDraftForCorrection() = mainDispatcher.runVm {
        val keyStore = FakeKeyStore(persists = false)
        val vm = viewModel(keyStore = keyStore)
        advanceUntilIdle()
        vm.onIntent(SettingsIntent.KeyChanged("AIza-new"))
        vm.onIntent(SettingsIntent.SaveKey)
        advanceUntilIdle()

        assertTrue(vm.state.value.keySaveFailed)
        assertEquals("AIza-new", vm.state.value.keyDraft)
    }

    @Test
    fun loadModelsUsesTheDraftKeyAndReportsTypedErrors() = mainDispatcher.runVm {
        val service = FakeRewording(models = listOf(ModelInfo("gemini-2.0-flash")))
        val vm = viewModel(service = service)
        advanceUntilIdle()
        vm.onIntent(SettingsIntent.KeyChanged("AIza-draft"))
        vm.onIntent(SettingsIntent.LoadModels)
        advanceUntilIdle()
        assertEquals(listOf("gemini-2.0-flash"), vm.state.value.models.map(ModelInfo::id))

        service.failure = RewordError.InvalidApiKey
        vm.onIntent(SettingsIntent.LoadModels)
        advanceUntilIdle()
        val error = assertIs<UiError.Known>(vm.state.value.modelsError)
        assertEquals(RewordError.InvalidApiKey, error.error)
    }

    @Test
    fun typingTheBasePromptCommitsOncePerPauseNotPerKeystroke() = mainDispatcher.runVm {
        val configStore = FakeConfigStore()
        val vm = viewModel(configStore)
        advanceUntilIdle()
        val savesBefore = configStore.saves

        "Base.".indices.forEach { end ->
            vm.onIntent(SettingsIntent.BasePromptChanged("Base.".substring(0, end + 1)))
        }
        // The draft is synchronous, the disk untouched until the pause.
        assertEquals("Base.", vm.state.value.basePromptDraft)
        assertEquals(savesBefore, configStore.saves)

        advanceTimeBy(500)
        advanceUntilIdle()
        assertEquals("Base.", configStore.config.basePrompt)
        assertEquals(savesBefore + 1, configStore.saves)
    }

    @Test
    fun changingTheOllamaHostDropsTheStaleModelChoiceAndCatalog() = mainDispatcher.runVm {
        val configStore = FakeConfigStore(
            RewordConfig(provider = ProviderKind.OLLAMA).selectingModel("llama3.2")
        )
        val service = FakeRewording(models = listOf(ModelInfo("llama3.2")))
        val vm = viewModel(configStore, service = service)
        advanceUntilIdle()
        vm.onIntent(SettingsIntent.LoadModels)
        advanceUntilIdle()
        assertTrue(vm.state.value.models.isNotEmpty())

        vm.onIntent(SettingsIntent.OllamaHostChanged("http://box:9999"))
        assertTrue(vm.state.value.models.isEmpty())
        advanceTimeBy(500)
        advanceUntilIdle()

        assertEquals("http://box:9999", configStore.config.ollamaHost)
        assertEquals(null, configStore.config.selectedModel)
    }

    @Test
    fun rulesRoundTripThroughTheStore() = mainDispatcher.runVm {
        val configStore = FakeConfigStore()
        val vm = viewModel(configStore)
        advanceUntilIdle()

        vm.onIntent(SettingsIntent.NewRuleChanged("  Never use exclamation marks  "))
        vm.onIntent(SettingsIntent.AddRule)
        advanceUntilIdle()
        val rule = configStore.config.rules.single()
        assertEquals("Never use exclamation marks", rule.text)

        vm.onIntent(SettingsIntent.ToggleRule(rule.id, enabled = false))
        advanceUntilIdle()
        assertFalse(configStore.config.rules.single().isEnabled)

        vm.onIntent(SettingsIntent.RuleTextChanged(rule.id, "No emoji"))
        advanceTimeBy(500)
        advanceUntilIdle()
        assertEquals("No emoji", configStore.config.rules.single().text)

        vm.onIntent(SettingsIntent.RemoveRule(rule.id))
        advanceUntilIdle()
        assertTrue(configStore.config.rules.isEmpty())
    }

    @Test
    fun theFloatingIconSwitchReflectsBothAndroidsAndRewordMesHalf() = mainDispatcher.runVm {
        val floating = FakeFloatingIconSettings(serviceOn = true, iconEnabled = true)
        val vm = viewModel(floatingIcon = floating)
        assertTrue(vm.state.value.floatingIconShown)

        floating.serviceOn = false
        vm.onIntent(SettingsIntent.RefreshFloatingIcon)
        assertFalse(vm.state.value.floatingIconShown, "Android switched the service off")
        assertTrue(vm.state.value.floatingIconOn, "but RewordMe still wants it: show the warning")
    }

    @Test
    fun switchingOnWithTheServiceOffExplainsBeforeSendingAnyoneAway() = mainDispatcher.runVm {
        val floating = FakeFloatingIconSettings(serviceOn = false, iconEnabled = false)
        val vm = viewModel(floatingIcon = floating)

        vm.onIntent(SettingsIntent.FloatingIconToggled(true))
        assertTrue(vm.state.value.floatingDisclosure)
        assertFalse(floating.iconEnabled, "nothing changes before the user agrees")

        vm.onIntent(SettingsIntent.FloatingDisclosureAccepted)
        assertFalse(vm.state.value.floatingDisclosure)
        assertTrue(floating.iconEnabled)
        assertEquals(SettingsEffect.OpenAccessibilitySettings, vm.effects.first())
    }

    @Test
    fun dismissingTheDisclosureChangesNothing() = mainDispatcher.runVm {
        val floating = FakeFloatingIconSettings(serviceOn = false, iconEnabled = false)
        val vm = viewModel(floatingIcon = floating)

        vm.onIntent(SettingsIntent.FloatingIconToggled(true))
        vm.onIntent(SettingsIntent.FloatingDisclosureDismissed)

        assertFalse(vm.state.value.floatingDisclosure)
        assertFalse(floating.iconEnabled)
        assertFalse(vm.state.value.floatingIconShown)
    }

    @Test
    fun withTheServiceOnTheSwitchActsAtOnceBothWays() = mainDispatcher.runVm {
        val floating = FakeFloatingIconSettings(serviceOn = true, iconEnabled = true)
        val vm = viewModel(floatingIcon = floating)

        vm.onIntent(SettingsIntent.FloatingIconToggled(false))
        assertFalse(floating.iconEnabled)
        assertFalse(vm.state.value.floatingIconShown)

        vm.onIntent(SettingsIntent.FloatingIconToggled(true))
        assertTrue(floating.iconEnabled)
        assertTrue(vm.state.value.floatingIconShown)
        assertFalse(vm.state.value.floatingDisclosure, "no disclosure for RewordMe's own half")
    }
}

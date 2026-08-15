package dev.mjablonski.rewordme.android.features.settings.presentation

import dev.mjablonski.rewordme.android.common.UiError
import dev.mjablonski.rewordme.android.platform.AndroidProviderAvailability
import dev.mjablonski.rewordme.android.support.FakeConfigStore
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
import kotlinx.coroutines.test.advanceUntilIdle

class SettingsViewModelTests {

    private val mainDispatcher = MainDispatcherRule()

    private fun viewModel(
        configStore: FakeConfigStore = FakeConfigStore(),
        keyStore: FakeKeyStore = FakeKeyStore(),
        service: FakeRewording = FakeRewording()
    ): SettingsViewModel {
        val resolver = ModelResolver()
        val dispatcher = mainDispatcher.dispatcher
        return SettingsViewModel(
            loadConfig = LoadConfigUseCase(configStore, dispatcher),
            updateConfig = UpdateConfigUseCase(configStore, resolver, dispatcher),
            hasApiKey = HasApiKeyUseCase(keyStore, dispatcher),
            saveApiKey = SaveApiKeyUseCase(keyStore, resolver, dispatcher),
            listModels = ListModelsUseCase(configStore, keyStore, service, dispatcher),
            availability = AndroidProviderAvailability()
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
    fun initLoadsConfigAndKeyPresence() = mainDispatcher.runVm {
            val configStore = FakeConfigStore(RewordConfig(provider = ProviderKind.OPENAI))
            val keyStore = FakeKeyStore(mutableMapOf(ProviderKind.OPENAI to "sk"))
            val vm = viewModel(configStore, keyStore)
            advanceUntilIdle()

            assertEquals(ProviderKind.OPENAI, vm.state.value.config.provider)
            assertTrue(vm.state.value.hasStoredKey)
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

            vm.onIntent(SettingsIntent.RemoveRule(rule.id))
            advanceUntilIdle()
            assertTrue(configStore.config.rules.isEmpty())
    }
}

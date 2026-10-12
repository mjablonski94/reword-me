package dev.mjablonski.rewordme.android.features.popup.presentation

import dev.mjablonski.rewordme.android.common.UiError
import dev.mjablonski.rewordme.android.platform.Clipboard
import dev.mjablonski.rewordme.android.support.FakeConfigStore
import dev.mjablonski.rewordme.android.support.FakeKeyStore
import dev.mjablonski.rewordme.android.support.FakeRewording
import dev.mjablonski.rewordme.android.support.MainDispatcherRule
import dev.mjablonski.rewordme.domain.ModelResolver
import dev.mjablonski.rewordme.domain.usecase.HasApiKeyUseCase
import dev.mjablonski.rewordme.domain.usecase.LoadConfigUseCase
import dev.mjablonski.rewordme.domain.usecase.RewordTextUseCase
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordConfig
import dev.mjablonski.rewordme.models.RewordError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import java.net.ConnectException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle

private class FakeClipboard(var succeeds: Boolean = true) : Clipboard {
    var copied: String? = null
    override fun copy(text: String): Boolean {
        if (succeeds) copied = text
        return succeeds
    }
}

class PopupViewModelTests {

    private val mainDispatcher = MainDispatcherRule()

    private fun viewModel(
        service: FakeRewording = FakeRewording(),
        clipboard: Clipboard = FakeClipboard(),
        config: RewordConfig = RewordConfig(provider = ProviderKind.GEMINI)
            .selectingModel("gemini-2.0-flash"),
        keys: MutableMap<ProviderKind, String> = mutableMapOf(ProviderKind.GEMINI to "key")
    ): PopupViewModel {
        val configStore = FakeConfigStore(config)
        val keyStore = FakeKeyStore(keys)
        return PopupViewModel(
            rewordText = RewordTextUseCase(
                configStore, keyStore, ModelResolver(), service, mainDispatcher.dispatcher
            ),
            loadConfig = LoadConfigUseCase(configStore, mainDispatcher.dispatcher),
            hasApiKey = HasApiKeyUseCase(keyStore, mainDispatcher.dispatcher),
            clipboard = clipboard
        )
    }

    @Test
    fun beginOnlyCountsOnceAcrossActivityRecreation() = mainDispatcher.runVm {
            val vm = viewModel()
            vm.onIntent(PopupIntent.Begin("original", canReplace = true))
            vm.onIntent(PopupIntent.SteeringChanged("draft"))
            // The activity is recreated and re-delivers Begin.
            vm.onIntent(PopupIntent.Begin("original", canReplace = true))
            assertEquals("draft", vm.state.value.steering)
    }

    @Test
    fun runReachesResultWithModelLabel() = mainDispatcher.runVm {
            val vm = viewModel(FakeRewording(answer = "polished"))
            vm.onIntent(PopupIntent.Begin("raw text", canReplace = true))
            vm.onIntent(PopupIntent.Run(PopupAction.PROOFREAD))
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(PopupStage.RESULT, state.stage)
            assertEquals("polished", state.result)
            assertEquals("gemini-2.0-flash", state.modelName)
            assertEquals("Gemini", state.providerName)
    }

    @Test
    fun typedFailureStaysTypedAndNonRetryableHidesRetry() = mainDispatcher.runVm {
            val vm = viewModel(FakeRewording(failure = RewordError.InvalidApiKey))
            vm.onIntent(PopupIntent.Begin("raw text", canReplace = true))
            vm.onIntent(PopupIntent.Run(PopupAction.REWRITE))
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(PopupStage.FAILED, state.stage)
            val error = assertIs<UiError.Known>(state.error)
            assertEquals(RewordError.InvalidApiKey, error.error)
            assertFalse(error.isRetryable)
    }

    @Test
    fun backToMenuAbandonsAnInFlightGeneration() = mainDispatcher.runVm {
            val service = FakeRewording(answer = "late")
            val vm = viewModel(service)
            vm.onIntent(PopupIntent.Begin("raw text", canReplace = true))
            vm.onIntent(PopupIntent.Run(PopupAction.REWRITE))
            vm.onIntent(PopupIntent.BackToMenu)
            advanceUntilIdle()

            assertEquals(PopupStage.MENU, vm.state.value.stage)
            assertEquals("", vm.state.value.result)
    }

    @Test
    fun steeringSubmitUsesTheTypedInstruction() = mainDispatcher.runVm {
            val service = FakeRewording()
            val vm = viewModel(service)
            vm.onIntent(PopupIntent.Begin("raw text", canReplace = true))
            vm.onIntent(PopupIntent.SteeringChanged("  make it rhyme  "))
            vm.onIntent(PopupIntent.SubmitSteering)
            advanceUntilIdle()

            assertTrue("make it rhyme" in service.lastSystemPrompt.orEmpty())
    }

    @Test
    fun replaceEmitsTheFinishingEffect() = mainDispatcher.runVm {
            val vm = viewModel(FakeRewording(answer = "polished"))
            vm.onIntent(PopupIntent.Begin("raw text", canReplace = true))
            vm.onIntent(PopupIntent.Run(PopupAction.REWRITE))
            advanceUntilIdle()
            vm.onIntent(PopupIntent.Replace)

            assertEquals(
                PopupEffect.FinishWithReplacement("polished", alsoCopied = false),
                vm.effects.first()
            )
    }

    @Test
    fun copyFailureSurfacesInsteadOfClosing() = mainDispatcher.runVm {
            val clipboard = FakeClipboard(succeeds = false)
            val vm = viewModel(FakeRewording(answer = "polished"), clipboard)
            vm.onIntent(PopupIntent.Begin("raw text", canReplace = true))
            vm.onIntent(PopupIntent.Run(PopupAction.REWRITE))
            advanceUntilIdle()
            vm.onIntent(PopupIntent.Copy)

            assertTrue(vm.state.value.actionFailedCopy)
    }

    @Test
    fun againPrefersFreshSteeringOverThePreviousInstruction() {
        assertEquals("newer", instructionForAgain("  newer ", "older"))
        assertEquals("older", instructionForAgain("", "older"))
        assertEquals("older", instructionForAgain("older", "older"))
        assertEquals(null, instructionForAgain("  ", null))
    }

    @Test
    fun missingKeyIsCaughtWhenThePopupOpens() = mainDispatcher.runVm {
            val vm = viewModel(keys = mutableMapOf())
            vm.onIntent(PopupIntent.Begin("raw text", canReplace = true))
            advanceUntilIdle()

            assertEquals(PopupStage.SETUP, vm.state.value.stage)
            vm.onIntent(PopupIntent.OpenSettings)
            assertEquals(PopupEffect.OpenSettings, vm.effects.first())
    }

    @Test
    fun keylessProvidersSkipTheSetupStage() = mainDispatcher.runVm {
            val vm = viewModel(
                config = RewordConfig(provider = ProviderKind.OLLAMA),
                keys = mutableMapOf()
            )
            vm.onIntent(PopupIntent.Begin("raw text", canReplace = true))
            advanceUntilIdle()

            assertEquals(PopupStage.MENU, vm.state.value.stage)
            assertEquals("Ollama", vm.state.value.providerName)
            assertEquals(null, vm.state.value.modelName)
    }

    @Test
    fun backAndScrimNeverDiscardAResult() = mainDispatcher.runVm {
            val vm = viewModel(FakeRewording(answer = "polished"))
            vm.onIntent(PopupIntent.Begin("raw text", canReplace = true))
            vm.onIntent(PopupIntent.Run(PopupAction.REWRITE))
            advanceUntilIdle()

            vm.onIntent(PopupIntent.ScrimTapped)
            assertEquals(PopupStage.RESULT, vm.state.value.stage)
            vm.onIntent(PopupIntent.BackPressed)
            assertEquals(PopupStage.MENU, vm.state.value.stage)
            vm.onIntent(PopupIntent.BackPressed)
            assertEquals(PopupEffect.Finish, vm.effects.first())
    }

    @Test
    fun replaceFromABrowserAlsoCopies() = mainDispatcher.runVm {
            val clipboard = FakeClipboard()
            val vm = viewModel(FakeRewording(answer = "polished"), clipboard)
            vm.onIntent(PopupIntent.Begin("raw text", canReplace = true, fromBrowser = true))
            vm.onIntent(PopupIntent.Run(PopupAction.REWRITE))
            advanceUntilIdle()
            vm.onIntent(PopupIntent.Replace)

            assertEquals("polished", clipboard.copied)
            assertEquals(
                PopupEffect.FinishWithReplacement("polished", alsoCopied = true),
                vm.effects.first()
            )
    }

    @Test
    fun copyConfirmsThenCloses() = mainDispatcher.runVm {
            val vm = viewModel(FakeRewording(answer = "polished"))
            vm.onIntent(PopupIntent.Begin("raw text", canReplace = false))
            vm.onIntent(PopupIntent.Run(PopupAction.REWRITE))
            advanceUntilIdle()
            vm.onIntent(PopupIntent.Copy)

            assertEquals(
                listOf(PopupEffect.Copied, PopupEffect.Finish),
                vm.effects.take(2).toList()
            )
    }

    @Test
    fun transportFailuresBecomeFriendlyNetworkErrors() = mainDispatcher.runVm {
            val vm = viewModel(FakeRewording(failure = ConnectException("Failed to connect to /10.0.0.1")))
            vm.onIntent(PopupIntent.Begin("raw text", canReplace = true))
            vm.onIntent(PopupIntent.Run(PopupAction.REWRITE))
            advanceUntilIdle()

            val error = assertIs<UiError.Network>(vm.state.value.error)
            assertFalse(error.timedOut)
            assertTrue(error.isRetryable && error.needsSettings)
    }

    @Test
    fun reasoningBlocksNeverReachTheResult() {
        assertEquals("Fixed.", withoutReasoning("<think>\nhmm\n</think>\nFixed."))
        assertEquals("plain", withoutReasoning("plain"))
    }

    @Test
    fun backDuringLoadingCancelsInsteadOfClosing() = mainDispatcher.runVm {
            val vm = viewModel(FakeRewording(answer = "late"))
            vm.onIntent(PopupIntent.Begin("raw text", canReplace = true))
            vm.onIntent(PopupIntent.Run(PopupAction.REWRITE))
            assertEquals(PopupStage.LOADING, vm.state.value.stage)

            vm.onIntent(PopupIntent.ScrimTapped)
            assertEquals(PopupStage.LOADING, vm.state.value.stage)
            vm.onIntent(PopupIntent.BackPressed)
            advanceUntilIdle()

            assertEquals(PopupStage.MENU, vm.state.value.stage)
            assertEquals("", vm.state.value.result)
    }

    @Test
    fun backFromAnErrorReturnsToTheMenu() = mainDispatcher.runVm {
            val vm = viewModel(FakeRewording(failure = RewordError.EmptyResponse))
            vm.onIntent(PopupIntent.Begin("raw text", canReplace = true))
            vm.onIntent(PopupIntent.Run(PopupAction.REWRITE))
            advanceUntilIdle()
            assertEquals(PopupStage.FAILED, vm.state.value.stage)

            vm.onIntent(PopupIntent.BackPressed)

            assertEquals(PopupStage.MENU, vm.state.value.stage)
    }

    @Test
    fun aTapNextToTheMenuCloses() = mainDispatcher.runVm {
            val vm = viewModel()
            vm.onIntent(PopupIntent.Begin("raw text", canReplace = true))
            vm.onIntent(PopupIntent.ScrimTapped)

            assertEquals(PopupEffect.Finish, vm.effects.first())
    }

    @Test
    fun readOnlyBrowserSelectionsNeverTouchTheClipboardOnTheirOwn() = mainDispatcher.runVm {
            val vm = viewModel()
            vm.onIntent(PopupIntent.Begin("raw text", canReplace = false, fromBrowser = true))

            assertFalse(vm.state.value.replaceAlsoCopies)
            assertFalse(vm.state.value.canReplace)
    }

    @Test
    fun tooShortSelectionsLandOnTheExplainingStage() = mainDispatcher.runVm {
            val vm = viewModel()
            vm.onIntent(PopupIntent.Begin("", canReplace = true, tooShort = true))
            advanceUntilIdle()

            assertEquals(PopupStage.EMPTY, vm.state.value.stage)
            assertTrue(vm.state.value.tooShort)
    }

    @Test
    fun setupNeverOverridesARequestAlreadyRunning() = mainDispatcher.runVm {
            // The user taps an action before the config check finishes: the
            // request's own MissingApiKey error must win, with its Settings exit.
            val vm = viewModel(keys = mutableMapOf())
            vm.onIntent(PopupIntent.Begin("raw text", canReplace = true))
            vm.onIntent(PopupIntent.Run(PopupAction.PROOFREAD))
            advanceUntilIdle()

            assertEquals(PopupStage.FAILED, vm.state.value.stage)
            assertTrue(vm.state.value.error?.needsSettings == true)
    }
}

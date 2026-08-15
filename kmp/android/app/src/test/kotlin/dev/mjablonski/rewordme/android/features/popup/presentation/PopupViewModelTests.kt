package dev.mjablonski.rewordme.android.features.popup.presentation

import dev.mjablonski.rewordme.android.common.UiError
import dev.mjablonski.rewordme.android.platform.Clipboard
import dev.mjablonski.rewordme.android.support.FakeConfigStore
import dev.mjablonski.rewordme.android.support.FakeKeyStore
import dev.mjablonski.rewordme.android.support.FakeRewording
import dev.mjablonski.rewordme.android.support.MainDispatcherRule
import dev.mjablonski.rewordme.domain.ModelResolver
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
import kotlinx.coroutines.flow.first
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
            .selectingModel("gemini-2.0-flash")
    ): PopupViewModel {
        val configStore = FakeConfigStore(config)
        val keyStore = FakeKeyStore(mutableMapOf(ProviderKind.GEMINI to "key"))
        return PopupViewModel(
            rewordText = RewordTextUseCase(
                configStore, keyStore, ModelResolver(), service, mainDispatcher.dispatcher
            ),
            loadConfig = LoadConfigUseCase(configStore, mainDispatcher.dispatcher),
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
            assertTrue("gemini-2.0-flash" in state.modelLabel)
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
                PopupEffect.FinishWithReplacement("polished"),
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
}

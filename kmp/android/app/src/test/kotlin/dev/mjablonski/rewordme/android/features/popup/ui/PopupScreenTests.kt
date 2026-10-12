package dev.mjablonski.rewordme.android.features.popup.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mjablonski.rewordme.android.R
import dev.mjablonski.rewordme.android.common.RewordMeTheme
import dev.mjablonski.rewordme.android.common.UiError
import dev.mjablonski.rewordme.android.features.popup.presentation.PopupIntent
import dev.mjablonski.rewordme.android.features.popup.presentation.PopupStage
import dev.mjablonski.rewordme.android.features.popup.presentation.PopupState
import dev.mjablonski.rewordme.models.RewordError
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The popup on the phone the E2E run found it broken on: 320dp wide, 533dp
 * tall, Polish - the longest labels among the bundled languages. Native
 * graphics so text is measured for real; with Robolectric's stub metrics
 * every label is near zero wide and the overflow bugs cannot reproduce.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "pl-w320dp-h533dp")
class PopupScreenTests {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val intents = mutableListOf<PopupIntent>()

    private fun str(id: Int, vararg args: Any): String = compose.activity.getString(id, *args)

    private fun show(state: PopupState, fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                RewordMeTheme { PopupScreen(state) { intents += it } }
            }
        }
    }

    private val longResult = List(12) { "Akapit ${it + 1}: celowo długi wynik do przewijania." }
        .joinToString(" ")

    @Test
    fun replaceSpansTheCardOnASmallPolishScreen() {
        show(PopupState(stage = PopupStage.RESULT, original = "x", result = "Poprawione."))

        val replace = compose.onNodeWithText(str(R.string.popup_replace))
            .performScrollTo()
            .assertIsDisplayed()
            .fetchSemanticsNode()
        val widthDp = replace.size.width / compose.activity.resources.displayMetrics.density
        // The card leaves 264dp for content at this width; squeezed beside
        // Again and Copy the button used to collapse into an unlabeled blob.
        assertTrue(widthDp >= 240f, "Replace is only ${widthDp}dp wide")
    }

    @Test
    @Config(qualifiers = "+h420dp")
    fun everyResultActionStaysReachableWithLargeFonts() {
        show(
            PopupState(stage = PopupStage.RESULT, original = "x", result = longResult),
            fontScale = 1.3f
        )

        for (label in listOf(R.string.popup_again, R.string.popup_copy, R.string.popup_replace)) {
            compose.onNodeWithText(str(label)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun closeButtonFitsBesideALongModelLabel() {
        show(
            PopupState(
                stage = PopupStage.MENU,
                original = "który",
                modelName = "gemini-3.5-flash-lite-preview-09-2026",
                providerName = "Gemini"
            ),
            fontScale = 1.3f
        )

        val close = compose.onNodeWithContentDescription(str(R.string.android_close))
            .assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val contentEdge = compose.onNodeWithText(str(R.string.popup_rewrite))
            .fetchSemanticsNode().boundsInRoot.right
        assertTrue(
            close.right <= contentEdge + 1f,
            "close spills past the card: ${close.right} > $contentEdge"
        )
    }

    @Test
    fun theSelectionIsPreviewedBeforeAnyAction() {
        show(PopupState(stage = PopupStage.MENU, original = "który"))

        compose.onNodeWithText("który").assertIsDisplayed()
    }

    @Test
    fun anUnconfiguredProviderLeadsStraightToSettings() {
        show(PopupState(stage = PopupStage.SETUP, original = "x"))

        compose.onNodeWithText(str(R.string.home_openSettings)).performClick()

        assertEquals(listOf<PopupIntent>(PopupIntent.OpenSettings), intents)
    }

    @Test
    fun aRejectedKeyOffersSettingsInsteadOfARetry() {
        show(
            PopupState(
                stage = PopupStage.FAILED,
                original = "x",
                error = UiError.Known(RewordError.InvalidApiKey)
            )
        )

        compose.onNodeWithText(str(R.string.popup_tryAgain)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.home_openSettings)).performScrollTo().performClick()
        assertEquals(listOf<PopupIntent>(PopupIntent.OpenSettings), intents)
    }

    @Test
    fun networkFailuresReadAsPlainLanguageWithBothWaysOut() {
        show(
            PopupState(stage = PopupStage.FAILED, original = "x", error = UiError.Network(false)),
            fontScale = 1.3f
        )

        compose.onNodeWithText(str(R.string.android_errorNetwork)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.popup_tryAgain)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(str(R.string.home_openSettings)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun gatewayHtmlNeverReachesTheScreen() {
        val html = "<html><body><h1>502 Bad Gateway</h1>nginx</body></html>"
        show(
            PopupState(
                stage = PopupStage.FAILED,
                original = "x",
                error = UiError.Known(RewordError.Api(502, html))
            )
        )

        compose.onNodeWithText(str(R.string.error_api, 502, "502 Bad Gateway nginx"))
            .assertIsDisplayed()
    }

    @Test
    fun readOnlyHostsGetCopyAsTheMainAction() {
        show(
            PopupState(
                stage = PopupStage.RESULT,
                original = "x",
                canReplace = false,
                result = "Poprawione."
            )
        )

        compose.onNodeWithText(str(R.string.popup_replace)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.android_replaceUnavailable)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.popup_copy)).performScrollTo().performClick()
        assertEquals(listOf<PopupIntent>(PopupIntent.Copy), intents)
    }

    @Test
    fun tooShortSelectionsExplainThemselves() {
        show(PopupState(stage = PopupStage.EMPTY, tooShort = true))

        compose.onNodeWithText(str(R.string.android_selectionTooShort)).assertIsDisplayed()
    }

    @Test
    fun slowRequestsSayTheyAreStillWorking() {
        compose.mainClock.autoAdvance = false
        show(PopupState(stage = PopupStage.LOADING, original = "x"))
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(str(R.string.android_slowHint)).assertDoesNotExist()

        compose.mainClock.advanceTimeBy(8_500)

        compose.onNodeWithText(str(R.string.android_slowHint)).assertIsDisplayed()
    }

    @Test
    fun systemBackIsHandedToThePopup() {
        show(PopupState(stage = PopupStage.RESULT, original = "x", result = "y"))

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

        assertEquals(listOf<PopupIntent>(PopupIntent.BackPressed), intents)
    }

    @Test
    fun aTapOnTheBackdropIsReportedRatherThanObeyed() {
        show(PopupState(stage = PopupStage.RESULT, original = "x", result = "y"))

        compose.onRoot().performTouchInput { click(Offset(2f, 2f)) }

        assertEquals(listOf<PopupIntent>(PopupIntent.ScrimTapped), intents)
    }
}

package dev.mjablonski.rewordme.android.features.home.ui

import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mjablonski.rewordme.android.R
import dev.mjablonski.rewordme.android.common.RewordMeTheme
import dev.mjablonski.rewordme.android.features.home.presentation.HomeIntent
import dev.mjablonski.rewordme.android.features.home.presentation.HomeState
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w320dp-h533dp")
class HomeScreenTests {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int): String = compose.activity.getString(id)

    @Test
    fun theSetupHintWaitsForTheConfigInsteadOfFlashing() {
        var state by mutableStateOf(HomeState())
        compose.setContent { RewordMeTheme { HomeScreen(state) {} } }
        compose.onNodeWithText(str(R.string.home_setupHint)).assertDoesNotExist()

        state = HomeState(isConfigured = false)
        compose.onNodeWithText(str(R.string.home_setupHint)).assertExists()

        state = HomeState(isConfigured = true)
        compose.onNodeWithText(str(R.string.home_setupHint)).assertDoesNotExist()
    }

    @Test
    fun theTutorialExplainsTheOverflowMenuAndTheShareFallback() {
        compose.setContent { RewordMeTheme { HomeScreen(HomeState(isConfigured = true)) {} } }

        compose.onNodeWithText(str(R.string.android_menuHint)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(str(R.string.android_shareHint)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theSampleTextIsEditableButNeverSummonsTheKeyboard() {
        compose.setContent { RewordMeTheme { HomeScreen(HomeState(isConfigured = true)) {} } }

        val editor = compose.activity.findViewById<EditText>(R.id.home_try_it_editor)
        // Editable, or the rewrite could not land back in it...
        assertTrue(editor.onCheckIsTextEditor())
        // ...but a keyboard on every long-press covered the sample on small phones.
        assertFalse(editor.showSoftInputOnFocus)
    }

    @Test
    fun theTutorialPointsToTheFloatingIconUntilItIsOn() {
        val intents = mutableListOf<HomeIntent>()
        var state by mutableStateOf(HomeState(isConfigured = true))
        compose.setContent { RewordMeTheme { HomeScreen(state) { intents += it } } }
        // Unknown yet: neither the pointer nor the "it's on" line flashes.
        compose.onNodeWithText(str(R.string.floating_homeHint)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.floating_homeOn)).assertDoesNotExist()

        state = HomeState(isConfigured = true, floatingIconShown = false)
        compose.onNodeWithText(str(R.string.floating_homeHint)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(str(R.string.floating_homeAction)).performScrollTo().performClick()
        assertEquals(listOf<HomeIntent>(HomeIntent.SetUpFloatingIcon), intents)

        state = HomeState(isConfigured = true, floatingIconShown = true)
        compose.onNodeWithText(str(R.string.floating_homeAction)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.floating_homeOn)).performScrollTo().assertIsDisplayed()
    }
}

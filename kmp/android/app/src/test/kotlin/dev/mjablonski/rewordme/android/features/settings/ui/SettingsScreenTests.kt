package dev.mjablonski.rewordme.android.features.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mjablonski.rewordme.android.R
import dev.mjablonski.rewordme.android.common.RewordMeTheme
import dev.mjablonski.rewordme.android.features.settings.presentation.SettingsIntent
import dev.mjablonski.rewordme.android.features.settings.presentation.SettingsState
import dev.mjablonski.rewordme.android.features.settings.presentation.SettingsTab
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordConfig
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w320dp-h533dp")
class SettingsScreenTests {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int): String = compose.activity.getString(id)

    @Test
    fun theToolbarHasAVisibleWayBack() {
        var backs = 0
        compose.setContent {
            RewordMeTheme { SettingsScreen(SettingsState(), {}, onBack = { backs++ }) }
        }

        compose.onNodeWithContentDescription(str(R.string.android_back)).performClick()

        assertEquals(1, backs)
    }

    @Test
    fun ollamaExplainsWhatLocalhostMeansOnAPhone() {
        val state = SettingsState(
            config = RewordConfig(provider = ProviderKind.OLLAMA),
            ollamaHostDraft = "http://localhost:11434"
        )
        compose.setContent { RewordMeTheme { SettingsScreen(state, {}) } }

        compose.onNodeWithText(str(R.string.android_ollamaPhoneHint))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun theGeneralTabOffersTheFloatingIconSwitch() {
        val intents = mutableListOf<SettingsIntent>()
        val state = SettingsState(tab = SettingsTab.GENERAL)
        compose.setContent { RewordMeTheme { SettingsScreen(state, { intents += it }) } }

        compose.onNodeWithText(str(R.string.floating_switch)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.floating_description)).performScrollTo().assertIsDisplayed()
        compose.onNode(isToggleable()).assertIsOff().performClick()

        assertEquals(listOf<SettingsIntent>(SettingsIntent.FloatingIconToggled(true)), intents)
        // Nothing to warn about before the user ever asked for the icon.
        compose.onNodeWithText(str(R.string.floating_serviceOff)).assertDoesNotExist()
    }

    @Test
    fun aWantedIconWithTheServiceOffSaysSoAndHowToFixIt() {
        val intents = mutableListOf<SettingsIntent>()
        val state = SettingsState(
            tab = SettingsTab.GENERAL,
            floatingIconOn = true,
            floatingServiceOn = false
        )
        compose.setContent { RewordMeTheme { SettingsScreen(state, { intents += it }) } }

        compose.onNode(isToggleable()).assertIsOff()
        compose.onNodeWithText(str(R.string.floating_serviceOff)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(str(R.string.floating_openAccessibility)).performScrollTo().performClick()
        // Sideloaded APKs on Android 13+ meet a greyed-out switch first.
        compose.onNodeWithText(str(R.string.floating_restrictedHint)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(str(R.string.floating_openAppInfo)).performScrollTo().performClick()

        assertEquals(
            listOf(SettingsIntent.OpenAccessibilitySettings, SettingsIntent.OpenAppInfo),
            intents
        )
    }

    @Test
    fun theDisclosureSaysWhatTheServiceReadsBeforeAnyoneAgrees() {
        val intents = mutableListOf<SettingsIntent>()
        val state = SettingsState(tab = SettingsTab.GENERAL, floatingDisclosure = true)
        compose.setContent { RewordMeTheme { SettingsScreen(state, { intents += it }) } }

        compose.onNodeWithText(str(R.string.floating_disclosureTitle)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.floating_disclosureBody)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.floating_notNow)).performClick()
        compose.onNodeWithText(str(R.string.floating_continue)).performClick()

        assertEquals(
            listOf(SettingsIntent.FloatingDisclosureDismissed, SettingsIntent.FloatingDisclosureAccepted),
            intents
        )
    }
}

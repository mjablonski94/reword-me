package dev.mjablonski.rewordme.android.features.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mjablonski.rewordme.android.R
import dev.mjablonski.rewordme.android.common.RewordMeTheme
import dev.mjablonski.rewordme.android.features.settings.presentation.SettingsState
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
}

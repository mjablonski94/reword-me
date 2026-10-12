package dev.mjablonski.rewordme.android.features.settings.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.mjablonski.rewordme.android.common.RewordMeTheme
import dev.mjablonski.rewordme.android.features.settings.ui.SettingsScreen
import org.koin.androidx.viewmodel.ext.android.viewModel

class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_FLOATING_ICON, false)) {
            viewModel.onIntent(SettingsIntent.SelectTab(SettingsTab.GENERAL))
        }
        setContent {
            val state by viewModel.state.collectAsState()

            LaunchedEffect(Unit) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is SettingsEffect.OpenUrl -> runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(effect.url)))
                        }
                        SettingsEffect.OpenAccessibilitySettings -> runCatching {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }
                        SettingsEffect.OpenAppInfo -> runCatching {
                            startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", packageName, null)
                                )
                            )
                        }
                    }
                }
            }

            RewordMeTheme {
                SettingsScreen(state, viewModel::onIntent, onBack = ::finish)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from Android's accessibility settings, or the service was
        // switched off there meanwhile.
        viewModel.onIntent(SettingsIntent.RefreshFloatingIcon)
    }

    companion object {
        /** Opens straight on the floating icon's switch (General tab). */
        const val EXTRA_FLOATING_ICON = "dev.mjablonski.rewordme.extra.FLOATING_ICON"
    }
}

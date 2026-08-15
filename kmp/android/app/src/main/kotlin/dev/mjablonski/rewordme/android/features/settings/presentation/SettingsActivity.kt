package dev.mjablonski.rewordme.android.features.settings.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.mjablonski.rewordme.android.features.settings.ui.SettingsScreen
import org.koin.androidx.viewmodel.ext.android.viewModel

class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsState()

            LaunchedEffect(Unit) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is SettingsEffect.OpenUrl -> runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(effect.url)))
                        }
                    }
                }
            }

            SettingsScreen(state, viewModel::onIntent)
        }
    }
}

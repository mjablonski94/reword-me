package dev.mjablonski.rewordme.android.features.home.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.mjablonski.rewordme.android.features.home.ui.HomeScreen
import dev.mjablonski.rewordme.android.features.settings.presentation.SettingsActivity
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsState()

            LaunchedEffect(Unit) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        HomeEffect.NavigateToSettings ->
                            startActivity(Intent(this@HomeActivity, SettingsActivity::class.java))
                    }
                }
            }

            HomeScreen(state, viewModel::onIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from Settings: the setup hint may no longer apply.
        viewModel.onIntent(HomeIntent.Refresh)
    }
}

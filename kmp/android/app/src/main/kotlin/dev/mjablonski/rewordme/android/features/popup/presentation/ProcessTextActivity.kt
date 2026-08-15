package dev.mjablonski.rewordme.android.features.popup.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.mjablonski.rewordme.android.common.RewordMeTheme
import dev.mjablonski.rewordme.android.features.popup.ui.PopupScreen
import dev.mjablonski.rewordme.domain.SelectionFilter
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Answers ACTION_PROCESS_TEXT from any app's text-selection menu. Returning
 * RESULT_OK with EXTRA_PROCESS_TEXT makes the host EditText replace the
 * selection itself - the same "put it back where it came from" contract the
 * desktop app implements with synthetic paste, provided by the OS here.
 */
class ProcessTextActivity : ComponentActivity() {

    private val viewModel: PopupViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Real insets on every API level (imePadding, systemBars), not only
        // where Android 15 forces edge-to-edge.
        enableEdgeToEdge()

        val raw = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        // Same gate as the desktop entry point: whitespace, bare numbers and
        // symbol runs get the guidance stage, not a billable rewrite of junk.
        val text = if (SelectionFilter.isMeaningful(raw)) raw else ""
        val readonly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        viewModel.onIntent(PopupIntent.Begin(text, canReplace = !readonly))

        setContent {
            val state by viewModel.state.collectAsState()

            LaunchedEffect(Unit) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is PopupEffect.FinishWithReplacement -> {
                            setResult(
                                RESULT_OK,
                                Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, effect.text)
                            )
                            finish()
                        }
                        PopupEffect.Finish -> finish()
                    }
                }
            }

            RewordMeTheme {
                PopupScreen(state, viewModel::onIntent)
            }
        }
    }
}

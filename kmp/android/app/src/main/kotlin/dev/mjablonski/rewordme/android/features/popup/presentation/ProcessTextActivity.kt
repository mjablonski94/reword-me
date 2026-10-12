package dev.mjablonski.rewordme.android.features.popup.presentation

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.mjablonski.rewordme.android.R
import dev.mjablonski.rewordme.android.common.RewordMeTheme
import dev.mjablonski.rewordme.android.features.popup.ui.PopupScreen
import dev.mjablonski.rewordme.android.features.settings.presentation.SettingsActivity
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

        // Shared text arrives through the share sheet, where hosts that hide
        // "Reword me" still offer Share; it has no way back but the clipboard.
        val shared = intent.action == Intent.ACTION_SEND
        val raw = intent.getCharSequenceExtra(
            if (shared) Intent.EXTRA_TEXT else Intent.EXTRA_PROCESS_TEXT
        )?.toString().orEmpty()
        viewModel.onIntent(
            beginFor(
                raw,
                shared = shared,
                readonlyHost = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false),
                callerIsBrowser = !shared && isBrowser(callingPackage)
            )
        )

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
                            if (effect.alsoCopied) toast(R.string.android_replaceAlsoCopied)
                            finish()
                        }
                        // Android 13+ confirms clipboard writes itself.
                        PopupEffect.Copied ->
                            if (Build.VERSION.SDK_INT < 33) toast(R.string.android_copied)
                        PopupEffect.OpenSettings -> {
                            // Its own task: the popup lives in the host app's task,
                            // and Settings must not end up stacked on someone's chat.
                            startActivity(
                                Intent(this@ProcessTextActivity, SettingsActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

    private fun toast(message: Int) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Chrome and its relatives return "replaced" while leaving web fields
     * unchanged. A caller that opens plain https links is a browser; the
     * caller of a startActivityForResult is always visible to us.
     */
    private fun isBrowser(caller: String?): Boolean {
        if (caller == null || caller == packageName) return false
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(caller)
        return runCatching { packageManager.queryIntentActivities(probe, 0).isNotEmpty() }
            .getOrDefault(false)
    }
}

/**
 * How the popup starts for one incoming selection. Pure, so the rules for
 * shared text, read-only hosts and browsers are unit-tested instead of
 * living only inside the activity.
 */
internal fun beginFor(
    raw: String,
    shared: Boolean,
    readonlyHost: Boolean,
    callerIsBrowser: Boolean
): PopupIntent.Begin {
    // Same gate as the desktop entry point: whitespace, bare numbers and
    // symbol runs get the guidance stage, not a billable rewrite of junk.
    val text = if (SelectionFilter.isMeaningful(raw)) raw else ""
    return PopupIntent.Begin(
        text = text,
        // A share has no result channel, so there is nothing to replace into.
        canReplace = !shared && !readonlyHost,
        tooShort = text.isEmpty() && raw.isNotBlank(),
        fromBrowser = !shared && callerIsBrowser
    )
}

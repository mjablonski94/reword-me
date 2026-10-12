package dev.mjablonski.rewordme.android.features.popup.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.mjablonski.rewordme.android.R
import dev.mjablonski.rewordme.android.common.RewordMeTheme
import dev.mjablonski.rewordme.android.features.floating.presentation.FloatingHandoff
import dev.mjablonski.rewordme.android.features.popup.ui.PopupScreen
import dev.mjablonski.rewordme.android.features.settings.presentation.SettingsActivity
import dev.mjablonski.rewordme.android.platform.Clipboard
import dev.mjablonski.rewordme.domain.SelectionFilter
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Answers ACTION_PROCESS_TEXT from any app's text-selection menu. Returning
 * RESULT_OK with EXTRA_PROCESS_TEXT makes the host EditText replace the
 * selection itself - the same "put it back where it came from" contract the
 * desktop app implements with synthetic paste, provided by the OS here.
 */
class ProcessTextActivity : ComponentActivity() {

    private val viewModel: PopupViewModel by viewModel()
    private val handoff: FloatingHandoff by inject()
    private val clipboard: Clipboard by inject()

    /**
     * Set when the floating icon opened this popup. There is no activity
     * result to the host then; the rewrite goes back through the service.
     */
    private var floatingToken: String? = null
    private var floatingResult: String? = null

    /**
     * Fades the popup in once its window is on screen and out before the
     * activity finishes. Started in onCreate, the fade ran while Android was
     * still bringing the window up - from the floating icon, a whole new
     * task - and was over before anyone could see it.
     */
    private val visibility = MutableTransitionState(false)
    private var closing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Real insets on every API level (imePadding, systemBars), not only
        // where Android 15 forces edge-to-edge.
        enableEdgeToEdge()
        // Compose owns the show/hide fade; a window animation on top of it
        // would play the same transition twice.
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        }

        // Shared text arrives through the share sheet, where hosts that hide
        // "Reword me" still offer Share; it has no way back but the clipboard.
        val shared = intent.action == Intent.ACTION_SEND
        if (intent.action == ACTION_FLOATING) floatingToken = intent.getStringExtra(EXTRA_FLOATING_TOKEN)
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
                            if (floatingToken != null) {
                                floatingResult = effect.text
                            } else {
                                setResult(
                                    RESULT_OK,
                                    Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, effect.text)
                                )
                            }
                            if (effect.alsoCopied) toast(R.string.android_replaceAlsoCopied)
                            closeAnimated()
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
                        PopupEffect.Finish -> closeAnimated()
                    }
                }
            }

            // Finish only once the exit fade has played out. With animations
            // turned off system-wide it settles at once and closes at once.
            LaunchedEffect(visibility.isIdle, visibility.currentState) {
                if (closing && visibility.isIdle && !visibility.currentState) finishQuietly()
            }

            RewordMeTheme {
                PopupScreen(state, visibility = visibility, onIntent = viewModel::onIntent)
            }
        }
        // onEnterAnimationComplete normally reveals it; this covers a window
        // that never reports one (recreated, or shown without any transition).
        window.decorView.postDelayed(::reveal, if (savedInstanceState == null) REVEAL_FALLBACK_MS else 0L)
    }

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        reveal()
    }

    private fun reveal() {
        if (!closing) visibility.targetState = true
    }

    private fun closeAnimated() {
        if (closing) return
        closing = true
        visibility.targetState = false
    }

    private fun finishQuietly() {
        finish()
        if (Build.VERSION.SDK_INT < 34) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
        reportToFloatingIcon()
    }

    override fun onStop() {
        super.onStop()
        // Opened from the icon, the popup is its own task, hidden from
        // Recents: once the user leaves it there is no way back, and the
        // icon waits for its outcome - so leaving means closing.
        if (floatingToken != null && !isChangingConfigurations && !isFinishing) finish()
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) reportToFloatingIcon()
        super.onDestroy()
    }

    /** Hands the outcome to the icon's service, once; Copy if it is gone. */
    private fun reportToFloatingIcon() {
        val token = floatingToken ?: return
        floatingToken = null
        val text = floatingResult
        if (!handoff.finish(token, text) && text != null && clipboard.copy(text)) {
            toast(R.string.floating_replaceFailed)
        }
    }

    private fun toast(message: Int) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        /** Not in any intent filter: only the floating icon starts it, explicitly. */
        const val ACTION_FLOATING = "dev.mjablonski.rewordme.action.FLOATING_REWORD"
        const val EXTRA_FLOATING_TOKEN = "dev.mjablonski.rewordme.extra.FLOATING_TOKEN"

        private const val REVEAL_FALLBACK_MS = 400L

        /**
         * The popup for a floating-icon tap. A new task of its own, because
         * a service has no task to join - and without MULTIPLE_TASK Android
         * would bring RewordMe's home screen up behind the popup instead of
         * leaving the app the text came from there.
         */
        fun floatingIntent(context: Context, text: String, editable: Boolean, token: String): Intent =
            Intent(context, ProcessTextActivity::class.java)
                .setAction(ACTION_FLOATING)
                .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, !editable)
                .putExtra(EXTRA_FLOATING_TOKEN, token)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
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

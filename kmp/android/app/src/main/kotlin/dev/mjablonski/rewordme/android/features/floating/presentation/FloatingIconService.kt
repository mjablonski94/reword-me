package dev.mjablonski.rewordme.android.features.floating.presentation

import android.accessibilityservice.AccessibilityService
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import dev.mjablonski.rewordme.android.R
import dev.mjablonski.rewordme.android.features.floating.data.FloatingIconSettings
import dev.mjablonski.rewordme.android.features.floating.ui.FloatingIconOverlay
import dev.mjablonski.rewordme.android.features.popup.presentation.ProcessTextActivity
import dev.mjablonski.rewordme.android.platform.Clipboard
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * The floating icon: whenever text is selected in any app, a small RewordMe
 * icon appears next to the selection; tapping it opens the usual popup, and
 * Replace writes the rewrite back into that field.
 *
 * This reaches the apps the selection menu cannot - since Android 11 Gmail,
 * Messages and others never list "Reword me" - at the price of an
 * accessibility service the user turns on themselves. It reads the selected
 * text only to place the icon and, after a tap, to hand it to the popup.
 */
class FloatingIconService : AccessibilityService(), FloatingHandoff.Receiver {

    private val handoff: FloatingHandoff by inject()
    private val settings: FloatingIconSettings by inject()
    private val writer: FieldWriter by inject()
    private val clipboard: Clipboard by inject()

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlay: FloatingIconOverlay? = null

    /** The field whose selection the icon currently belongs to. */
    private var tracked: AccessibilityNodeInfo? = null

    /** When the current selection was first seen; 0 while there is none. */
    private var selectedAt = 0L

    /** The tap in flight: the popup is open and owes us its outcome. */
    private var pending: Pending? = null

    private class Pending(
        val token: String,
        val node: AccessibilityNodeInfo,
        val snapshot: SelectionSnapshot
    )

    private val recheck = Runnable { recheck() }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = forget()
    }

    override fun onServiceConnected() {
        settings.serviceConnected()
        overlay = FloatingIconOverlay(this, ::onIconTapped)
        handoff.attach(this)
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (overlay == null || pending != null) return
        if (!settings.iconEnabled) {
            forget()
            return
        }
        when (event.eventType) {
            // Shown once the selection holds still for a moment, so a dragged
            // handle does not make it flicker.
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                event.source?.let { tracked = it }
                schedule(SETTLE_MS)
            }
            // Scrolling moves the text under a fixed icon: hide, then put it
            // back next to the selection once the list settles.
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> if (tracked?.windowId == event.windowId) {
                overlay?.hide()
                schedule(SCROLL_SETTLE_MS)
            }
            // A tap elsewhere, a focus move, another window or the keyboard
            // coming up can start, end or move a selection without any
            // selection event; the recheck sorts out which.
            else -> schedule(if (tracked != null) RECHECK_MS else SETTLE_MS)
        }
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Rotation: the selection is somewhere else now.
        if (tracked != null) schedule(ROTATION_SETTLE_MS)
    }

    override fun onDestroy() {
        handoff.detach(this)
        runCatching { unregisterReceiver(screenOff) }
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        overlay?.hide(animated = false)
        overlay = null
        super.onDestroy()
    }

    private fun track(node: AccessibilityNodeInfo) {
        val snapshot = snapshotOf(node)
        if (!FloatingRules.shouldShow(snapshot, packageName)) {
            forget()
            return
        }
        tracked = node
        val icon = overlay ?: return
        // Android shows the selection toolbar only once the finger lifts.
        // Appearing before it, the icon had to move out of its way a moment
        // later; so the first appearance waits for the toolbar - or, in apps
        // without one, not much longer than a long-press lasts.
        if (!icon.isShown) {
            if (selectedAt == 0L) selectedAt = SystemClock.uptimeMillis()
            val waited = SystemClock.uptimeMillis() - selectedAt
            val screen = screenBox()
            if (popups(node, screen).isEmpty() && waited < TOOLBAR_WAIT_MS) {
                schedule(TOOLBAR_WAIT_MS - waited)
                return
            }
        }
        icon.showAt(placeFor(node, snapshot))
        // Keep verifying while the icon is up: some apps never report that a
        // selection went away, and the icon must not outlive it.
        schedule(WATCH_MS)
    }

    private fun recheck() {
        if (pending != null) return
        if (!settings.iconEnabled) {
            forget()
            return
        }
        val reported = tracked?.takeIf { it.refresh() && isOnScreen(it) }
            ?.takeIf { FloatingRules.shouldShow(snapshotOf(it), packageName) }
        val node = reported ?: focusedSelection()
        if (node == null) {
            forget()
            return
        }
        track(node)
    }

    /**
     * The focused field, when it holds a selection. A long-press into a field
     * that is not focused yet can report its selection from a view the app
     * then swaps out (Google Messages does), and nothing more after it; the
     * field that really holds the selection is the one with input focus.
     */
    private fun focusedSelection(): AccessibilityNodeInfo? =
        runCatching { findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
            ?.takeIf { FloatingRules.shouldShow(snapshotOf(it), packageName) }

    private fun forget() {
        handler.removeCallbacks(recheck)
        tracked = null
        selectedAt = 0L
        overlay?.hide()
    }

    private fun schedule(delayMillis: Long) {
        handler.removeCallbacks(recheck)
        handler.postDelayed(recheck, delayMillis)
    }

    private fun onIconTapped() {
        val node = tracked ?: return
        // The selection may have grown since the icon appeared.
        val snapshot = if (node.refresh()) snapshotOf(node) else return forget()
        if (!FloatingRules.shouldShow(snapshot, packageName)) return forget()

        val token = UUID.randomUUID().toString()
        pending = Pending(token, node, snapshot)
        handler.removeCallbacks(recheck)
        overlay?.hide()
        runCatching {
            // No system task-open animation: the popup's own fade is the
            // transition, the same as from the selection menu.
            startActivity(
                ProcessTextActivity.floatingIntent(this, snapshot.selected, snapshot.editable, token),
                ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()
            )
        }.onFailure {
            pending = null
            forget()
        }
    }

    override fun finished(token: String, replacement: String?): Boolean {
        val request = pending?.takeIf { it.token == token } ?: return false
        if (replacement == null) {
            // Closed without a rewrite: the text may still be selected,
            // so the icon comes back for another go.
            pending = null
            tracked = request.node
            schedule(AFTER_POPUP_MS)
            return true
        }
        tracked = null
        scope.launch {
            // Still pending while writing: until the rewrite lands, the old
            // selection is there and would bring the icon back for a moment.
            val outcome = try {
                // Let the host window take focus back before editing it.
                delay(AFTER_POPUP_MS)
                writer.write(NodeField(request.node), request.snapshot, replacement)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                // A host view gone stale mid-write: the rewrite must not be lost.
                clipboard.copy(replacement)
                WriteOutcome.COPIED
            } finally {
                pending = null
            }
            if (outcome == WriteOutcome.COPIED) {
                Toast.makeText(applicationContext, R.string.floating_replaceFailed, Toast.LENGTH_LONG).show()
            }
        }
        return true
    }

    private fun snapshotOf(node: AccessibilityNodeInfo) = SelectionSnapshot(
        packageName = node.packageName?.toString().orEmpty(),
        text = node.text?.toString().orEmpty(),
        start = node.textSelectionStart,
        end = node.textSelectionEnd,
        editable = node.isEditable,
        password = node.isPassword,
        focused = node.isFocused,
        viewId = node.viewIdResourceName
    )

    /** Windows we may not see (no interactive-windows flag) count as on screen. */
    private fun isOnScreen(node: AccessibilityNodeInfo): Boolean {
        val visible = runCatching { windows }.getOrNull() ?: return true
        return visible.isEmpty() || visible.any { it.id == node.windowId }
    }

    private fun placeFor(node: AccessibilityNodeInfo, snapshot: SelectionSnapshot): Spot {
        val icon = overlay ?: return Spot(0, 0)
        val trimmed = snapshot.selected.trimEnd()
        val lead = trimmed.length - trimmed.trimStart().length
        val field = Rect().also(node::getBoundsInScreen).toBox()
        // The visible first and last characters; a field that cannot say
        // where its characters are falls back to its own bounds.
        val last = characterBox(node, snapshot.from + trimmed.length - 1) ?: field
        val first = characterBox(node, snapshot.from + lead) ?: last
        val screen = screenBox()
        return FloatingRules.place(
            first = first,
            last = last,
            screen = screen,
            obstacles = obstacles(node, screen),
            size = icon.sizePx,
            handle = icon.dp(HANDLE_DP),
            gap = icon.dp(GAP_DP)
        )
    }

    /**
     * The keyboard, the system bars, and the host's small windows over its
     * screen: the selection toolbar, menus, autocomplete lists.
     */
    private fun obstacles(node: AccessibilityNodeInfo, screen: Box): List<Box> =
        popups(node, screen) + windowBoxes().mapNotNull { (window, box) ->
            // Status and navigation bars: full-width strips.
            val bar = window.type == AccessibilityWindowInfo.TYPE_SYSTEM &&
                (box.right - box.left) * 10 >= (screen.right - screen.left) * 9 &&
                (box.bottom - box.top) * 7 < screen.bottom - screen.top
            box.takeIf { window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD || bar }
        }

    /** The host's small windows over its screen, the selection toolbar among them. */
    private fun popups(node: AccessibilityNodeInfo, screen: Box): List<Box> {
        val area = (screen.right - screen.left).toLong() * (screen.bottom - screen.top)
        return windowBoxes().mapNotNull { (window, box) ->
            box.takeIf {
                window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    window.id != node.windowId &&
                    (box.right - box.left).toLong() * (box.bottom - box.top) < area / 4
            }
        }
    }

    private fun windowBoxes(): List<Pair<AccessibilityWindowInfo, Box>> =
        runCatching { windows }.getOrNull().orEmpty()
            .map { it to Rect().also(it::getBoundsInScreen).toBox() }

    /** Screen bounds of one character, when the host reports them and it is visible. */
    private fun characterBox(node: AccessibilityNodeInfo, index: Int): Box? {
        val key = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
        if (index < 0 || key !in node.availableExtraData) return null
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, index)
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, 1)
        }
        if (!node.refreshWithExtraData(key, args)) return null
        @Suppress("DEPRECATION")
        val rect = node.extras.getParcelableArray(key)?.firstOrNull() as? RectF ?: return null
        return Box(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
    }

    private fun screenBox(): Box =
        getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.toBox()

    private fun Rect.toBox() = Box(left, top, right, bottom)

    /** [EditableField] over the host's accessibility node. */
    private class NodeField(private val node: AccessibilityNodeInfo) : EditableField {
        override fun refresh(): Boolean = node.refresh()
        override val text: String? get() = node.text?.toString()
        override val editable: Boolean get() = node.isEditable

        override fun setText(text: String): Boolean = node.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
        )

        override fun select(start: Int, end: Int): Boolean = node.performAction(
            AccessibilityNodeInfo.ACTION_SET_SELECTION,
            Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end)
            }
        )

        override fun paste(): Boolean = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    private companion object {
        const val SETTLE_MS = 200L
        const val TOOLBAR_WAIT_MS = 900L
        const val RECHECK_MS = 120L
        const val SCROLL_SETTLE_MS = 300L
        const val ROTATION_SETTLE_MS = 400L
        const val WATCH_MS = 700L
        const val AFTER_POPUP_MS = 250L

        /** Room for the selection handle hanging below the line's end. */
        const val HANDLE_DP = 26
        const val GAP_DP = 6
    }
}

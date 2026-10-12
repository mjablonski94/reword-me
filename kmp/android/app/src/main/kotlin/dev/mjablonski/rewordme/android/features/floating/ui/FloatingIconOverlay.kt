package dev.mjablonski.rewordme.android.features.floating.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import dev.mjablonski.rewordme.android.R
import dev.mjablonski.rewordme.android.features.floating.presentation.Spot
import kotlin.math.roundToInt

/**
 * The icon itself: a tiny window of TYPE_ACCESSIBILITY_OVERLAY, which an
 * accessibility service may add without the "display over other apps"
 * permission. It never takes focus, so a tap leaves the selection - and the
 * keyboard - of the app underneath exactly as they were.
 */
internal class FloatingIconOverlay(
    private val context: Context,
    private val onTap: () -> Unit
) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density

    /** 40dp: a comfortable tap, small enough to sit beside a line of text. */
    val sizePx: Int = dp(40)

    private var view: View? = null
    private var removing = false

    /** Where the icon is headed; a glide may still be on its way there. */
    private var target: Spot? = null
    private var glide: ValueAnimator? = null

    private val params = WindowManager.LayoutParams(
        sizePx,
        sizePx,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        // Screen coordinates, same as AccessibilityNodeInfo bounds: no
        // status-bar or cutout offset may shift the icon off the selection.
        gravity = Gravity.TOP or Gravity.START
        windowAnimations = 0
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        title = "RewordMe floating icon"
    }

    val isShown: Boolean get() = view != null && !removing

    fun dp(value: Int): Int = (value * density).toInt()

    /** Fades in at [spot], or moves there if already up. */
    fun showAt(spot: Spot) {
        val current = view
        // The watch loop re-places the icon several times a second; only a
        // real move is worth a window relayout.
        if (current != null && !removing && spot == target) return
        target = spot
        if (current != null) {
            if (removing) {
                // Selected again mid fade-out: turn the fade around.
                removing = false
                current.animate().cancel()
                current.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(FADE_IN_MS).start()
            }
            glideTo(current, spot)
            return
        }
        params.x = spot.x
        params.y = spot.y
        val icon = createView()
        if (runCatching { windowManager.addView(icon, params) }.isFailure) return
        view = icon
        icon.alpha = 0f
        icon.scaleX = 0.6f
        icon.scaleY = 0.6f
        icon.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(FADE_IN_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun hide(animated: Boolean = true) {
        val current = view ?: return
        if (removing) return
        removing = true
        current.animate().cancel()
        if (!animated) {
            remove(current)
            return
        }
        // withEndAction skips cancelled runs, so a reversed fade keeps the view.
        current.animate()
            .alpha(0f).scaleX(0.6f).scaleY(0.6f)
            .setDuration(FADE_OUT_MS)
            .withEndAction { if (removing) remove(current) }
            .start()
    }

    /** Moves follow the selection (the keyboard opening, a dragged handle) without jumping. */
    private fun glideTo(icon: View, spot: Spot) {
        glide?.cancel()
        val fromX = params.x
        val fromY = params.y
        glide = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = GLIDE_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val f = animator.animatedFraction
                params.x = (fromX + (spot.x - fromX) * f).roundToInt()
                params.y = (fromY + (spot.y - fromY) * f).roundToInt()
                runCatching { windowManager.updateViewLayout(icon, params) }
            }
            start()
        }
    }

    private fun remove(icon: View) {
        glide?.cancel()
        glide = null
        runCatching { windowManager.removeViewImmediate(icon) }
        if (view === icon) {
            view = null
            target = null
        }
        removing = false
    }

    private fun createView(): View = ImageView(context).apply {
        setImageResource(R.mipmap.ic_launcher)
        // The same label as the selection-menu entry, so screen readers and
        // UI tests find one name for one action.
        contentDescription = context.getString(R.string.android_processText)
        scaleType = ImageView.ScaleType.FIT_CENTER
        val inset = dp(2)
        setPadding(inset, inset, inset, inset)
        setOnClickListener { onTap() }
    }

    private companion object {
        const val FADE_IN_MS = 180L
        const val FADE_OUT_MS = 140L
        const val GLIDE_MS = 180L
    }
}

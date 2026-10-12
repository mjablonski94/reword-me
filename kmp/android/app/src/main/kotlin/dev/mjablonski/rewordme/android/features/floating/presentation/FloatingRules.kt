package dev.mjablonski.rewordme.android.features.floating.presentation

/**
 * One selection as the accessibility service sees it, reduced to plain
 * values so the show/hide, placement and replace rules are unit-tested
 * off-device instead of living only inside the service.
 */
data class SelectionSnapshot(
    val packageName: String,
    val text: String,
    val start: Int,
    val end: Int,
    val editable: Boolean,
    val password: Boolean = false,
    /** Editable fields keep their selection after losing focus, just unshown. */
    val focused: Boolean = true,
    /** "package:id/name" when the host reports view IDs, else null. */
    val viewId: String? = null
) {
    val from: Int get() = minOf(start, end)
    val to: Int get() = maxOf(start, end)

    val selected: String
        get() = if (from >= 0 && to <= text.length) text.substring(from, to) else ""
}

/** A rectangle in screen pixels, decoupled from android.graphics.Rect. */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** Where the icon goes, top-left corner in screen pixels. */
data class Spot(val x: Int, val y: Int)

/** The new field text after a replacement, and where the cursor lands. */
data class Splice(val text: String, val start: Int, val end: Int, val cursor: Int)

object FloatingRules {

    /** The tutorial editor: the one field in RewordMe itself that shows the icon. */
    const val TUTORIAL_EDITOR_ID = "home_try_it_editor"

    /** Not a writing surface: the shade, quick settings, the lock screen. */
    private val ignoredPackages = setOf("com.android.systemui")

    /**
     * Whether a selection gets the icon. Every app's selected text does,
     * except password fields, the system UI and RewordMe's own screens -
     * those already list "Reword me" in their menu, and the popup's own
     * field must not offer to reword itself. The tutorial editor is the
     * exception, so people can try the icon right where they learn the app.
     */
    fun shouldShow(snapshot: SelectionSnapshot?, ownPackage: String): Boolean {
        if (snapshot == null || snapshot.password) return false
        if (snapshot.packageName in ignoredPackages) return false
        if (snapshot.packageName == ownPackage &&
            snapshot.viewId?.substringAfter(":id/", "") != TUTORIAL_EDITOR_ID
        ) {
            return false
        }
        // An unfocused field still reports its old selection; nobody sees it.
        if (snapshot.editable && !snapshot.focused) return false
        return snapshot.selected.isNotBlank()
    }

    /**
     * Places the icon for a selection whose first character sits in [first]
     * and last in [last]. It goes just above the selection, near its end -
     * and when the selection toolbar is there, above the toolbar, never on
     * its buttons. With no room at the top it goes below the selection
     * (past the end handle, and past the toolbar when Android put it there);
     * failing both, beside the end of the selection, covering only text.
     * [obstacles] are the keyboard and the host's popups.
     */
    fun place(
        first: Box,
        last: Box,
        screen: Box,
        obstacles: List<Box>,
        size: Int,
        handle: Int,
        gap: Int
    ): Spot {
        fun Spot.box() = Box(x, y, x + size, y + size)
        fun fits(spot: Spot): Boolean {
            val box = spot.box()
            return box.left >= screen.left + gap && box.right <= screen.right - gap &&
                box.top >= screen.top + gap && box.bottom <= screen.bottom - gap &&
                obstacles.none { it.overlaps(box) }
        }
        // Moves past whatever obstacle is in the way until the spot is free,
        // or gives up once it leaves the screen.
        fun slide(start: Spot, up: Boolean): Spot? {
            var spot = start
            repeat(obstacles.size + 1) {
                if (fits(spot)) return spot
                val box = spot.box()
                val hit = obstacles.firstOrNull { it.overlaps(box) } ?: return null
                spot = Spot(spot.x, if (up) hit.top - gap - size else hit.bottom + gap)
            }
            return null
        }

        val maxX = maxOf(screen.left + gap, screen.right - gap - size)
        val aboveX = (last.right - size / 2).coerceIn(screen.left + gap, maxX)
        // Below the line the end handle hangs right of the last character.
        val belowX = if (last.right + handle + size <= screen.right - gap) {
            last.right + handle
        } else {
            last.right - handle / 2 - gap - size
        }
        val beside = Spot(last.right + gap, (last.top + last.bottom) / 2 - size / 2)

        val chosen = slide(Spot(aboveX, first.top - gap - size), up = true)
            ?: slide(Spot(belowX, last.bottom + gap), up = false)
            ?: beside.takeIf(::fits)
            ?: beside
        return Spot(
            chosen.x.coerceIn(screen.left + gap, maxX),
            chosen.y.coerceIn(screen.top + gap, maxOf(screen.top + gap, screen.bottom - gap - size))
        )
    }

    private fun Box.overlaps(other: Box): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    /**
     * Swaps [selected] for [replacement] in the field's [current] text. The
     * selection is expected at [start]; if the field shifted meanwhile it is
     * looked up again, but only a single unambiguous match is trusted - a
     * wrong guess would rewrite the wrong sentence of someone's message.
     */
    fun splice(current: String, start: Int, selected: String, replacement: String): Splice? {
        if (selected.isEmpty()) return null
        val at = when {
            start >= 0 && current.regionMatches(start, selected, 0, selected.length) -> start
            else -> current.indexOf(selected).takeIf { it >= 0 && it == current.lastIndexOf(selected) }
        } ?: return null
        val text = current.substring(0, at) + replacement + current.substring(at + selected.length)
        return Splice(text, at, at + selected.length, cursor = at + replacement.length)
    }

    /**
     * Whether Android's enabled-services setting (a ':'-separated list of
     * flattened component names, full or short form) lists our service.
     */
    fun isServiceEnabled(setting: String?, packageName: String, serviceClass: String): Boolean =
        setting.orEmpty().split(':').any { entry ->
            val pkg = entry.substringBefore('/', "")
            val cls = entry.substringAfter('/', "")
            pkg == packageName && (cls == serviceClass || (cls.startsWith('.') && pkg + cls == serviceClass))
        }
}

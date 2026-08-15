package dev.mjablonski.rewordme.android.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** Thin seam over the system clipboard so view models stay testable. */
interface Clipboard {
    fun copy(text: String): Boolean
}

class ClipboardWriter(context: Context) : Clipboard {
    private val manager =
        context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    override fun copy(text: String): Boolean = runCatching {
        manager.setPrimaryClip(ClipData.newPlainText("RewordMe", text))
        true
    }.getOrDefault(false)
}

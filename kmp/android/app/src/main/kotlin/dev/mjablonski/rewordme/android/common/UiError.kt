package dev.mjablonski.rewordme.android.common

import android.content.Context
import dev.mjablonski.rewordme.models.RewordError

/**
 * A failure carried in MVI state. Typed RewordErrors keep their identity so
 * the view can localize them; anything else degrades to its raw message.
 */
sealed interface UiError {
    val isRetryable: Boolean

    data class Known(val error: RewordError) : UiError {
        override val isRetryable: Boolean get() = error.isRetryable
    }

    data class Raw(val message: String?) : UiError {
        override val isRetryable: Boolean get() = true
    }

    companion object {
        fun of(failure: Throwable): UiError =
            if (failure is RewordError) Known(failure) else Raw(failure.message)
    }
}

fun UiError.localized(context: Context, fallback: String): String = when (this) {
    is UiError.Known -> error.localized(context)
    is UiError.Raw -> message ?: fallback
}

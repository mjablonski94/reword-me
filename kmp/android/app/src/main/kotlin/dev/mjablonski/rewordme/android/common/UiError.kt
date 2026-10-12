package dev.mjablonski.rewordme.android.common

import android.content.Context
import dev.mjablonski.rewordme.android.R
import dev.mjablonski.rewordme.models.RewordError
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * A failure carried in MVI state. Typed RewordErrors keep their identity so
 * the view can localize them; transport failures collapse into [Network] so
 * nobody reads an OkHttp or Ktor exception message; anything else degrades
 * to its raw message.
 */
sealed interface UiError {
    val isRetryable: Boolean

    /** True when the fix lives in Settings: a missing or rejected key, no model, a wrong server. */
    val needsSettings: Boolean

    data class Known(val error: RewordError) : UiError {
        override val isRetryable: Boolean get() = error.isRetryable
        override val needsSettings: Boolean
            get() = error == RewordError.MissingApiKey ||
                error == RewordError.InvalidApiKey ||
                error == RewordError.NoModelAvailable
    }

    /** Unreachable host, refused connection, dropped socket or a timeout. */
    data class Network(val timedOut: Boolean) : UiError {
        override val isRetryable: Boolean get() = true
        override val needsSettings: Boolean get() = !timedOut
    }

    data class Raw(val message: String?) : UiError {
        override val isRetryable: Boolean get() = true
        override val needsSettings: Boolean get() = false
    }

    companion object {
        // Type checks, not class names: R8 renames classes in release builds.
        fun of(failure: Throwable): UiError {
            if (failure is RewordError) return Known(failure)
            val chain = generateSequence(failure) { it.cause }.take(8).toList()
            return when {
                // A connect timeout means an unreachable host - the address
                // or the network is wrong, not a slow provider.
                chain.any { it is ConnectTimeoutException } -> Network(timedOut = false)
                chain.any { it is HttpRequestTimeoutException || it is SocketTimeoutException } ->
                    Network(timedOut = true)
                chain.any { it is IOException } -> Network(timedOut = false)
                else -> Raw(failure.message)
            }
        }
    }
}

fun UiError.localized(context: Context, fallback: String): String = when (this) {
    is UiError.Known -> error.localized(context)
    is UiError.Network -> context.getString(
        if (timedOut) R.string.android_errorTimeout else R.string.android_errorNetwork
    )
    is UiError.Raw -> message ?: fallback
}

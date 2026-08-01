package dev.mjablonski.rewordme.app

import dev.mjablonski.rewordme.models.RewordError
import java.util.Locale
import java.util.ResourceBundle

/**
 * UI strings for the 11 languages the macOS app ships, resolved against the
 * system locale. Unknown locales fall back to the English bundle.
 */
object Strings {
    private val bundle: ResourceBundle =
        ResourceBundle.getBundle("Strings", Locale.getDefault())

    operator fun get(key: String): String =
        runCatching { bundle.getString(key) }.getOrDefault(key)

    fun format(key: String, vararg args: Any): String =
        runCatching { String.format(get(key), *args) }.getOrDefault(get(key))
}

/**
 * Error text lives in the UI layer: core:models carries the error case,
 * the popup decides how to phrase it in the user's language.
 */
fun RewordError.localized(): String = when (this) {
    RewordError.MissingApiKey -> Strings["error.missingKey"]
    RewordError.InvalidApiKey -> Strings["error.invalidKey"]
    is RewordError.RateLimited -> retryAfterSeconds
        ?.let { Strings.format("error.rateLimitedRetry", it) }
        ?: Strings["error.rateLimited"]
    is RewordError.Refused -> explanation ?: Strings["error.refused"]
    is RewordError.Api -> Strings.format("error.api", status, detail)
    RewordError.EmptyResponse -> Strings["error.empty"]
    RewordError.InvalidResponse -> Strings["error.invalidResponse"]
    RewordError.NoModelAvailable -> Strings["error.noModel"]
}

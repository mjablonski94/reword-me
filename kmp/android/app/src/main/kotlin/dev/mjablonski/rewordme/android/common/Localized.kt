package dev.mjablonski.rewordme.android.common

import android.content.Context
import dev.mjablonski.rewordme.android.R
import dev.mjablonski.rewordme.models.RewordError

/**
 * Error text lives in the UI layer, same as on desktop: core:models carries
 * the error case, the screen decides how to phrase it in the user's language.
 * Cases Android cannot reach (account CLIs, the managed local runtime) keep
 * mappings anyway so the when stays exhaustive against shared models.
 */
fun RewordError.localized(context: Context): String = when (this) {
    RewordError.MissingApiKey -> context.getString(R.string.error_missingKey)
    RewordError.InvalidApiKey -> context.getString(R.string.error_invalidKey)
    is RewordError.RateLimited -> retryAfterSeconds
        ?.let { context.getString(R.string.error_rateLimitedRetry, it) }
        ?: context.getString(R.string.error_rateLimited)
    RewordError.UsageLimitReached -> context.getString(R.string.error_usageLimit)
    is RewordError.Refused -> explanation ?: context.getString(R.string.error_refused)
    is RewordError.Api -> context.getString(R.string.error_api, status, readableDetail(detail))
    RewordError.EmptyResponse -> context.getString(R.string.error_empty)
    RewordError.InvalidResponse -> context.getString(R.string.error_invalidResponse)
    RewordError.NoModelAvailable -> context.getString(R.string.error_noModel)
    is RewordError.ProviderNotInstalled ->
        context.getString(R.string.error_providerNotInstalled, providerName)
    is RewordError.AccountNotSignedIn ->
        context.getString(R.string.error_accountNotSignedIn, providerName)
    is RewordError.AccountUsesApiKey ->
        context.getString(R.string.error_accountUsesApiKey, providerName)
    is RewordError.AccountCommandFailed -> detail
    RewordError.LocalModelNotDownloaded -> context.getString(R.string.error_localNotDownloaded)
    RewordError.LocalRuntimeUnavailable -> context.getString(R.string.error_localRuntime)
    is RewordError.LocalModelDownloadFailed ->
        context.getString(R.string.error_localDownload, detail)
}

/**
 * Proxies and gateways answer errors with HTML pages; the error detail is the
 * raw body, so strip the markup rather than show "<html><body>..." to people.
 */
internal fun readableDetail(detail: String): String {
    if ('<' !in detail || '>' !in detail) return detail
    return detail.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim().take(160)
}

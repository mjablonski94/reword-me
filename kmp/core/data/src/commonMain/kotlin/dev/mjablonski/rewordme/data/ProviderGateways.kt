package dev.mjablonski.rewordme.data

import dev.mjablonski.rewordme.models.LocalModelCatalog
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordError

/**
 * The two provider families that cannot be plain HTTP calls: account CLIs
 * (Codex, Claude) and the managed llama.cpp server. Both need to spawn OS
 * processes, so only the desktop supplies real implementations; other
 * platforms keep the defaults, which refuse with the matching RewordError.
 * The providers are also hidden from those platforms' settings, so the
 * defaults are a safety net, not a user-visible path.
 */

/** Rewords through a locally installed, already signed-in vendor app. */
interface AccountRewording {
    suspend fun reword(
        provider: ProviderKind,
        model: String,
        systemPrompt: String,
        text: String
    ): String
}

/** Where a running local model server listens. */
data class LocalServerConnection(
    val endpoint: String,
    val apiKey: String,
    val modelId: String = LocalModelCatalog.DEFAULT.id
)

/** Downloads (if needed) and starts the managed local model, then connects. */
interface LocalModelConnecting {
    suspend fun connection(modelId: String = LocalModelCatalog.DEFAULT.id): LocalServerConnection
}

object UnsupportedAccountRewording : AccountRewording {
    override suspend fun reword(
        provider: ProviderKind,
        model: String,
        systemPrompt: String,
        text: String
    ): String = throw RewordError.ProviderNotInstalled(provider.displayName)
}

object UnsupportedLocalModel : LocalModelConnecting {
    override suspend fun connection(modelId: String): LocalServerConnection =
        throw RewordError.LocalRuntimeUnavailable
}

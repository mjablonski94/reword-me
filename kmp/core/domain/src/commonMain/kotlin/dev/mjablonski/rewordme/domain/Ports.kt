package dev.mjablonski.rewordme.domain

import dev.mjablonski.rewordme.models.ModelInfo
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordConfig

/**
 * Ports the outer layers implement (core:data provides the real ones,
 * tests provide stubs). The domain only ever sees these interfaces.
 */

/** Anything that can list a provider's models. */
interface ModelListing {
    suspend fun listModels(provider: ProviderKind, apiKey: String, endpoint: String?): List<ModelInfo>
}

/** Full provider access: model listing plus the actual rewrite call. */
interface Rewording : ModelListing {
    suspend fun reword(
        provider: ProviderKind,
        apiKey: String,
        model: String,
        systemPrompt: String,
        text: String,
        endpoint: String?
    ): String
}

/** Where API keys live. */
interface ApiKeyStore {
    fun apiKey(provider: ProviderKind): String?

    /** True only when the requested value was persisted (or removed). */
    fun setApiKey(provider: ProviderKind, key: String?): Boolean
}

/** Where the non-secret configuration lives. */
interface ConfigStore {
    fun load(): RewordConfig
    fun save(config: RewordConfig)
}

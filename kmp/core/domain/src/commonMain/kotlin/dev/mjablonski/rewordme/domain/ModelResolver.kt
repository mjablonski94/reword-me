package dev.mjablonski.rewordme.domain

import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordConfig
import dev.mjablonski.rewordme.models.RewordError
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Resolves the model to use for a request. An explicit model in the
 * config wins; otherwise the provider's model list is fetched once and
 * the least costly model is cached for the rest of the session.
 */
class ModelResolver {
    private val mutex = Mutex()
    private val cache = mutableMapOf<CacheKey, String>()

    suspend fun model(config: RewordConfig, apiKey: String, service: ModelListing): String {
        config.selectedModel?.takeIf(String::isNotEmpty)?.let { return it }
        val key = CacheKey(config.provider, apiKey, config.endpointOverride)
        mutex.withLock { cache[key] }?.let { return it }

        val models = service.listModels(config.provider, apiKey, config.endpointOverride)
        val pick = ModelSelection.defaultModel(config.provider, models)
            ?: throw RewordError.NoModelAvailable
        mutex.withLock { cache[key] = pick.id }
        return pick.id
    }

    suspend fun invalidate() {
        mutex.withLock { cache.clear() }
    }

    /** Credentials and local endpoints can expose different model catalogs. */
    private data class CacheKey(
        val provider: ProviderKind,
        val apiKey: String,
        val endpoint: String?
    )
}

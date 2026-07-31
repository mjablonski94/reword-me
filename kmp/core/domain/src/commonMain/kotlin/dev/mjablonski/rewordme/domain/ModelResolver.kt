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
    private val cache = mutableMapOf<ProviderKind, String>()

    suspend fun model(config: RewordConfig, apiKey: String, service: ModelListing): String {
        config.model?.takeIf(String::isNotEmpty)?.let { return it }
        mutex.withLock { cache[config.provider] }?.let { return it }

        val models = service.listModels(config.provider, apiKey, config.endpointOverride)
        val pick = ModelSelection.defaultModel(config.provider, models)
            ?: throw RewordError.NoModelAvailable
        mutex.withLock { cache[config.provider] = pick.id }
        return pick.id
    }

    suspend fun invalidate() {
        mutex.withLock { cache.clear() }
    }
}

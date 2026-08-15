package dev.mjablonski.rewordme.domain.usecase

import dev.mjablonski.rewordme.domain.ConfigStore
import dev.mjablonski.rewordme.domain.ModelResolver
import dev.mjablonski.rewordme.domain.UseCase
import dev.mjablonski.rewordme.models.RewordConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Loads the persisted configuration. */
class LoadConfigUseCase(
    private val configStore: ConfigStore,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : UseCase<RewordConfig, Unit>(dispatcher) {
    override suspend fun action(params: Unit): RewordConfig = configStore.load()
}

/**
 * Persists a transformation of the current configuration. Callers pass a
 * function over the freshly loaded config rather than a snapshot, so two
 * screens editing different fields cannot overwrite each other.
 */
class UpdateConfigUseCase(
    private val configStore: ConfigStore,
    private val resolver: ModelResolver,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : UseCase<RewordConfig, (RewordConfig) -> RewordConfig>(dispatcher) {

    override suspend fun action(params: (RewordConfig) -> RewordConfig): RewordConfig {
        val updated = params(configStore.load())
        configStore.save(updated)
        // A provider/model/endpoint change can invalidate the cached automatic
        // pick; clearing unconditionally is cheap and always correct.
        resolver.invalidate()
        return updated
    }
}

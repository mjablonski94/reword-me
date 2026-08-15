package dev.mjablonski.rewordme.domain.usecase

import dev.mjablonski.rewordme.domain.ConfigStore
import dev.mjablonski.rewordme.domain.ModelResolver
import dev.mjablonski.rewordme.domain.UseCase
import dev.mjablonski.rewordme.models.RewordConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Loads the persisted configuration. */
class LoadConfigUseCase(
    private val configStore: ConfigStore,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : UseCase<RewordConfig, Unit>(dispatcher) {
    override suspend fun action(params: Unit): RewordConfig = configStore.load()
}

/**
 * Persists a transformation of the current configuration. Callers pass a
 * function over the freshly loaded config, and the whole load-transform-save
 * runs under one process-wide mutex - that pair is what actually stops two
 * concurrent edits from overwriting each other.
 */
class UpdateConfigUseCase(
    private val configStore: ConfigStore,
    private val resolver: ModelResolver,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : UseCase<RewordConfig, (RewordConfig) -> RewordConfig>(dispatcher) {

    override suspend fun action(params: (RewordConfig) -> RewordConfig): RewordConfig =
        mutex.withLock {
            val current = configStore.load()
            val updated = params(current)
            configStore.save(updated)
            // Only edits that can change which model serves the next request
            // pay the cache clear; a rule or base-prompt keystroke must not
            // force a fresh listModels round trip.
            if (affectsModelChoice(current, updated)) resolver.invalidate()
            updated
        }

    private fun affectsModelChoice(before: RewordConfig, after: RewordConfig): Boolean =
        before.provider != after.provider ||
            before.model != after.model ||
            before.modelsByProvider != after.modelsByProvider ||
            before.ollamaHost != after.ollamaHost

    private companion object {
        /** Shared across instances: Koin builds these as factories. */
        val mutex = Mutex()
    }
}

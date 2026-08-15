package dev.mjablonski.rewordme.domain.usecase

import dev.mjablonski.rewordme.domain.ApiKeyStore
import dev.mjablonski.rewordme.domain.ModelResolver
import dev.mjablonski.rewordme.domain.UseCase
import dev.mjablonski.rewordme.models.ProviderKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Whether a key is stored for the provider (the key itself stays in the vault). */
class HasApiKeyUseCase(
    private val keyStore: ApiKeyStore,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : UseCase<Boolean, ProviderKind>(dispatcher) {
    override suspend fun action(params: ProviderKind): Boolean =
        keyStore.apiKey(params)?.isNotEmpty() == true
}

data class SaveApiKeyParams(val provider: ProviderKind, val key: String?)

/** Stores or clears a provider's API key; result reports whether it persisted. */
class SaveApiKeyUseCase(
    private val keyStore: ApiKeyStore,
    private val resolver: ModelResolver,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : UseCase<Boolean, SaveApiKeyParams>(dispatcher) {

    override suspend fun action(params: SaveApiKeyParams): Boolean {
        val stored = keyStore.setApiKey(params.provider, params.key)
        // A new key can unlock a different model catalog.
        if (stored) resolver.invalidate()
        return stored
    }
}

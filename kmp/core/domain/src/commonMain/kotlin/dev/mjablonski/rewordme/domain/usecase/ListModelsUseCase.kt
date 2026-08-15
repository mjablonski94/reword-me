package dev.mjablonski.rewordme.domain.usecase

import dev.mjablonski.rewordme.domain.ApiKeyStore
import dev.mjablonski.rewordme.domain.ConfigStore
import dev.mjablonski.rewordme.domain.ModelListing
import dev.mjablonski.rewordme.domain.UseCase
import dev.mjablonski.rewordme.models.ModelInfo
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

data class ListModelsParams(
    val provider: ProviderKind,
    /**
     * A key typed into settings but not yet saved. The desktop lists models
     * with the draft key so the user can verify it before storing; blank
     * falls back to the vault.
     */
    val draftKey: String? = null
)

/** Model list for the settings screen's picker. */
class ListModelsUseCase(
    private val configStore: ConfigStore,
    private val keyStore: ApiKeyStore,
    private val listing: ModelListing,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : UseCase<List<ModelInfo>, ListModelsParams>(dispatcher) {

    override suspend fun action(params: ListModelsParams): List<ModelInfo> {
        val provider = params.provider
        val apiKey = if (provider.requiresApiKey) {
            params.draftKey?.trim()?.takeIf(String::isNotEmpty)
                ?: keyStore.apiKey(provider)
                ?: throw RewordError.MissingApiKey
        } else {
            ""
        }
        // The endpoint override only applies when the provider owning it is
        // selected; other providers list against their fixed base URL.
        val config = configStore.load()
        val endpoint = if (config.provider == provider) config.endpointOverride else null
        return listing.listModels(provider, apiKey, endpoint)
            .filter { provider.includesModel(it.id) }
    }
}

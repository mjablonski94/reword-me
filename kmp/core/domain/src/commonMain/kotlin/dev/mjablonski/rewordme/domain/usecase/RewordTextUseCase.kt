package dev.mjablonski.rewordme.domain.usecase

import dev.mjablonski.rewordme.domain.ApiKeyStore
import dev.mjablonski.rewordme.domain.ConfigStore
import dev.mjablonski.rewordme.domain.ModelResolver
import dev.mjablonski.rewordme.domain.PromptBuilder
import dev.mjablonski.rewordme.domain.Rewording
import dev.mjablonski.rewordme.domain.UseCase
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordConfig
import dev.mjablonski.rewordme.models.RewordError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

data class RewordParams(
    val text: String,
    /** One-shot steering typed in the popup, or a preset's instruction. */
    val instruction: String?
)

data class RewordOutcome(
    val text: String,
    val model: String,
    val provider: ProviderKind,
    /** The config the request ran under, for labelling the result. */
    val config: RewordConfig
)

/**
 * The whole popup path in one place: load config, fetch the key, resolve
 * the model, build the prompt, call the provider. Both front ends run this
 * so a fix lands on every platform at once.
 */
class RewordTextUseCase(
    private val configStore: ConfigStore,
    private val keyStore: ApiKeyStore,
    private val resolver: ModelResolver,
    private val service: Rewording,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) : UseCase<RewordOutcome, RewordParams>(dispatcher) {

    override suspend fun action(params: RewordParams): RewordOutcome {
        val config = configStore.load()
        val apiKey = if (config.provider.requiresApiKey) {
            keyStore.apiKey(config.provider) ?: throw RewordError.MissingApiKey
        } else {
            ""
        }
        val model = resolver.model(config, apiKey, service)
        val systemPrompt = PromptBuilder.systemPrompt(
            config.rules, config.basePrompt, params.instruction
        )
        val reworded = service.reword(
            config.provider, apiKey, model, systemPrompt, params.text, config.endpointOverride
        )
        return RewordOutcome(reworded, model, config.provider, config)
    }
}

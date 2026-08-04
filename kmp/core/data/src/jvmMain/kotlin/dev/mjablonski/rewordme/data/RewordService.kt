package dev.mjablonski.rewordme.data

import dev.mjablonski.rewordme.domain.Rewording
import dev.mjablonski.rewordme.models.ModelInfo
import dev.mjablonski.rewordme.models.LocalModelCatalog
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordError
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Performs the actual HTTP calls. Request building and parsing live in
 * the per-provider clients so they stay pure and testable.
 */
class RewordService(
    private val http: HttpClient = HttpClient(CIO),
    private val registry: ProviderClientRegistry = ProviderClientRegistry(),
    private val accountProviders: AccountProviderService = AccountProviderService(),
    private val localModel: LocalModelManager = LocalModelManager()
) : Rewording {
    override suspend fun listModels(
        provider: ProviderKind,
        apiKey: String,
        endpoint: String?
    ): List<ModelInfo> {
        when (provider) {
            ProviderKind.LOCAL -> return LocalModelCatalog.ALL.map { ModelInfo(it.id, it.displayName) }
            ProviderKind.CODEX -> return listOf(ModelInfo("automatic", "Automatic (Codex)"))
            ProviderKind.CLAUDE_ACCOUNT -> return listOf(
                ModelInfo("automatic", "Automatic (Claude)"),
                ModelInfo("sonnet", "Sonnet"),
                ModelInfo("opus", "Opus")
            )
            else -> Unit
        }
        val client = registry.client(provider)
        val body = perform(client.modelsRequest(validated(apiKey, provider), endpoint))
        return client.parseModels(body)
    }

    override suspend fun reword(
        provider: ProviderKind,
        apiKey: String,
        model: String,
        systemPrompt: String,
        text: String,
        endpoint: String?
    ): String {
        if (provider.isAccountProvider) {
            return accountProviders.reword(provider, model, systemPrompt, text)
        }
        if (provider == ProviderKind.LOCAL) {
            val manifest = LocalModelCatalog.model(model)
            val connection = localModel.connection(manifest.id)
            val client = registry.client(ProviderKind.LOCAL)
            val request = client.rewordRequest(
                connection.apiKey, manifest.id, systemPrompt, text, connection.endpoint
            )
            return client.parseReword(perform(request))
        }
        val client = registry.client(provider)
        val request = client.rewordRequest(validated(apiKey, provider), model, systemPrompt, text, endpoint)
        return client.parseReword(perform(request))
    }

    private fun validated(apiKey: String, provider: ProviderKind): String {
        // Managed/account paths are intercepted above; Ollama ignores auth.
        if (!provider.requiresApiKey) return if (provider == ProviderKind.OLLAMA) "ollama" else "local"
        return apiKey.trim().ifEmpty { throw RewordError.MissingApiKey }
    }

    private suspend fun perform(request: ProviderRequest): String {
        val response = http.request(request.url) {
            method = HttpMethod.parse(request.method)
            request.headers.forEach { (name, value) -> header(name, value) }
            request.jsonBody?.let {
                contentType(ContentType.Application.Json)
                setBody(it)
            }
        }
        val body = response.bodyAsText()
        return when (response.status.value) {
            in 200..299 -> body
            401, 403 -> throw RewordError.InvalidApiKey
            429 -> {
                if (isUsageLimitError(body)) throw RewordError.UsageLimitReached
                throw RewordError.RateLimited(response.headers["retry-after"]?.toIntOrNull())
            }
            else -> throw RewordError.Api(response.status.value, errorDetail(body))
        }
    }

    companion object {
        /** Best-effort extraction of a message from any error envelope. */
        internal fun errorDetail(body: String): String {
            val root = runCatching { lenientJson.parseToJsonElement(body).jsonObject }
                .getOrNull() ?: return body.take(200)
            val error = (root["error"] as? JsonObject)
            val message = error?.get("message") ?: root["message"]
            return runCatching { message?.jsonPrimitive?.content }.getOrNull()
                ?: body.take(200)
        }

        /** Distinguishes user-action billing/quota 429s from temporary throttling. */
        internal fun isUsageLimitError(body: String): Boolean {
            val root = runCatching { lenientJson.parseToJsonElement(body).jsonObject }
                .getOrNull() ?: return false
            val error = root["error"] as? JsonObject
            val fields = listOf(
                error?.get("code"),
                error?.get("type"),
                root["code"],
                root["type"]
            ).mapNotNull { value ->
                runCatching { value?.jsonPrimitive?.content?.lowercase() }.getOrNull()
            }
            val actionRequiredCodes = setOf(
                "credit_balance_exhausted",
                "organization_spend_limit_exceeded",
                "project_spend_limit_exceeded",
                "organization_usage_limit_exceeded",
                "insufficient_quota",
                "billing_hard_limit_reached",
                "billing_not_active"
            )
            if (fields.any(actionRequiredCodes::contains)) return true

            val message = errorDetail(body).lowercase()
            val transientMarkers = listOf(
                "rate_limit_error", "rate_limit_exceeded", "resource_exhausted",
                "per minute", "per second", "requests per", "tokens per",
                "rpm", "tpm", "retry after", "retry shortly", "quota metric", "rate limit"
            )
            if (transientMarkers.any(message::contains)) return false

            val actionRequiredPhrases = listOf(
                "credit balance", "spend limit", "insufficient quota",
                "billing not active", "billing hard limit", "payment required",
                "credits exhausted", "no credits"
            )
            if (actionRequiredPhrases.any(message::contains)) return true
            return "current quota" in message &&
                listOf("billing", "plan", "credit").any(message::contains)
        }
    }
}

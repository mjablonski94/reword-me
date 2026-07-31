package dev.mjablonski.rewordme.data

import dev.mjablonski.rewordme.domain.Rewording
import dev.mjablonski.rewordme.models.ModelInfo
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
    private val registry: ProviderClientRegistry = ProviderClientRegistry()
) : Rewording {
    override suspend fun listModels(
        provider: ProviderKind,
        apiKey: String,
        endpoint: String?
    ): List<ModelInfo> {
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
        val client = registry.client(provider)
        val request = client.rewordRequest(validated(apiKey, provider), model, systemPrompt, text, endpoint)
        return client.parseReword(perform(request))
    }

    private fun validated(apiKey: String, provider: ProviderKind): String {
        // Ollama ignores auth; a placeholder keeps the header well-formed.
        if (!provider.requiresApiKey) return "ollama"
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
            429 -> throw RewordError.RateLimited(
                response.headers["retry-after"]?.toIntOrNull()
            )
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
    }
}

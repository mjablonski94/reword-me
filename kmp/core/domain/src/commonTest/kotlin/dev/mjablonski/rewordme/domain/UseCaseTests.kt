package dev.mjablonski.rewordme.domain

import dev.mjablonski.rewordme.domain.usecase.ListModelsParams
import dev.mjablonski.rewordme.domain.usecase.ListModelsUseCase
import dev.mjablonski.rewordme.domain.usecase.RewordParams
import dev.mjablonski.rewordme.domain.usecase.RewordTextUseCase
import dev.mjablonski.rewordme.domain.usecase.SaveApiKeyParams
import dev.mjablonski.rewordme.domain.usecase.SaveApiKeyUseCase
import dev.mjablonski.rewordme.domain.usecase.UpdateConfigUseCase
import dev.mjablonski.rewordme.models.ModelInfo
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordConfig
import dev.mjablonski.rewordme.models.RewordError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

private class FakeConfigStore(var config: RewordConfig = RewordConfig()) : ConfigStore {
    var saves = 0
    override fun load(): RewordConfig = config
    override fun save(config: RewordConfig) {
        saves++
        this.config = config
    }
}

private class FakeKeyStore(
    private val keys: MutableMap<ProviderKind, String> = mutableMapOf(),
    var persists: Boolean = true
) : ApiKeyStore {
    override fun apiKey(provider: ProviderKind): String? = keys[provider]
    override fun setApiKey(provider: ProviderKind, key: String?): Boolean {
        if (!persists) return false
        if (key == null) keys.remove(provider) else keys[provider] = key
        return true
    }
}

private class FakeRewording(
    private val models: List<ModelInfo> = listOf(ModelInfo("model-a")),
    private val answer: String = "better text"
) : Rewording {
    var lastSystemPrompt: String? = null
    var lastModel: String? = null
    var lastEndpoint: String? = null
    var listCalls = 0

    override suspend fun listModels(
        provider: ProviderKind,
        apiKey: String,
        endpoint: String?
    ): List<ModelInfo> {
        listCalls++
        lastEndpoint = endpoint
        return models
    }

    override suspend fun reword(
        provider: ProviderKind,
        apiKey: String,
        model: String,
        systemPrompt: String,
        text: String,
        endpoint: String?
    ): String {
        lastSystemPrompt = systemPrompt
        lastModel = model
        lastEndpoint = endpoint
        return answer
    }
}

class RewordTextUseCaseTest {
    private fun useCase(
        configStore: FakeConfigStore,
        keyStore: FakeKeyStore,
        service: FakeRewording
    ) = RewordTextUseCase(configStore, keyStore, ModelResolver(), service)

    @Test
    fun rewordsWithSelectedModelAndSteering() = runBlocking {
        val config = RewordConfig(provider = ProviderKind.GEMINI)
            .selectingModel("gemini-2.0-flash")
        val service = FakeRewording()
        val keyStore = FakeKeyStore(mutableMapOf(ProviderKind.GEMINI to "key"))
        val outcome = useCase(FakeConfigStore(config), keyStore, service)
            .safeAction(RewordParams("hello", "more formal")).getOrThrow()

        assertEquals("better text", outcome.text)
        assertEquals("gemini-2.0-flash", outcome.model)
        assertEquals("gemini-2.0-flash", service.lastModel)
        assertTrue("more formal" in service.lastSystemPrompt.orEmpty())
    }

    @Test
    fun missingKeyFailsBeforeAnyNetworkCall() = runBlocking {
        val service = FakeRewording()
        val result = useCase(FakeConfigStore(), FakeKeyStore(), service)
            .safeAction(RewordParams("hello", null))

        assertIs<RewordError.MissingApiKey>(result.exceptionOrNull())
        assertEquals(0, service.listCalls)
    }

    @Test
    fun keylessProviderSkipsTheKeyStore() = runBlocking {
        val config = RewordConfig(provider = ProviderKind.OLLAMA).selectingModel("llama3.2")
        val outcome = useCase(FakeConfigStore(config), FakeKeyStore(), FakeRewording())
            .safeAction(RewordParams("hello", null))

        assertTrue(outcome.isSuccess)
    }
}

class ListModelsUseCaseTest {
    @Test
    fun filtersModelsTheProviderCannotChat() = runBlocking {
        val service = FakeRewording(
            models = listOf(ModelInfo("gpt-4o"), ModelInfo("text-embedding-3-small"))
        )
        val keyStore = FakeKeyStore(mutableMapOf(ProviderKind.OPENAI to "key"))
        val models = ListModelsUseCase(FakeConfigStore(), keyStore, service)
            .safeAction(ListModelsParams(ProviderKind.OPENAI)).getOrThrow()

        assertEquals(listOf("gpt-4o"), models.map(ModelInfo::id))
    }

    @Test
    fun draftKeyBeatsTheVaultAndMissingBothFails() = runBlocking<Unit> {
        val service = FakeRewording(models = listOf(ModelInfo("gpt-4o")))
        val useCase = ListModelsUseCase(FakeConfigStore(), FakeKeyStore(), service)

        val withDraft = useCase
            .safeAction(ListModelsParams(ProviderKind.OPENAI, draftKey = "sk-typed"))
        assertTrue(withDraft.isSuccess)

        val without = useCase.safeAction(ListModelsParams(ProviderKind.OPENAI))
        assertIs<RewordError.MissingApiKey>(without.exceptionOrNull())
    }

    @Test
    fun endpointOverrideOnlyAppliesToTheSelectedProvider() = runBlocking {
        val config = RewordConfig(provider = ProviderKind.OLLAMA, ollamaHost = "http://box:9999")
        val service = FakeRewording(models = listOf(ModelInfo("llama3.2")))
        ListModelsUseCase(FakeConfigStore(config), FakeKeyStore(), service)
            .safeAction(ListModelsParams(ProviderKind.OLLAMA)).getOrThrow()
        assertEquals("http://box:9999/v1", service.lastEndpoint)

        val geminiKeyStore = FakeKeyStore(mutableMapOf(ProviderKind.GEMINI to "key"))
        ListModelsUseCase(FakeConfigStore(config), geminiKeyStore, service)
            .safeAction(ListModelsParams(ProviderKind.GEMINI)).getOrThrow()
        assertEquals(null, service.lastEndpoint)
    }
}

class UpdateConfigUseCaseTest {
    @Test
    fun transformsTheFreshlyLoadedConfigAndInvalidatesTheResolver() = runBlocking {
        val store = FakeConfigStore(RewordConfig(basePrompt = "keep me"))
        val resolver = ModelResolver()
        val updated = UpdateConfigUseCase(store, resolver)
            .safeAction { it.selectingProvider(ProviderKind.MISTRAL) }.getOrThrow()

        assertEquals(ProviderKind.MISTRAL, updated.provider)
        assertEquals("keep me", updated.basePrompt)
        assertEquals(1, store.saves)
    }
}

class UseCaseCancellationTest {
    @Test
    fun cancellationUnwindsInsteadOfBecomingAFailureResult() = runBlocking {
        val useCase = object : UseCase<String, Unit>() {
            override suspend fun action(params: Unit): String {
                delay(60_000)
                return "never"
            }
        }
        var resumedNormally = false
        val job = launch {
            useCase.safeAction(Unit)
            // A cancelled safeAction must throw, not hand back Result.failure.
            resumedNormally = true
        }
        yield()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(resumedNormally)
    }
}

class SaveApiKeyUseCaseTest {
    @Test
    fun reportsWhetherTheKeyPersisted() = runBlocking {
        val keyStore = FakeKeyStore()
        val useCase = SaveApiKeyUseCase(keyStore, ModelResolver())
        assertTrue(
            useCase.safeAction(SaveApiKeyParams(ProviderKind.GEMINI, "key")).getOrThrow()
        )
        assertEquals("key", keyStore.apiKey(ProviderKind.GEMINI))

        keyStore.persists = false
        assertFalse(
            useCase.safeAction(SaveApiKeyParams(ProviderKind.OPENAI, "key")).getOrThrow()
        )
    }
}

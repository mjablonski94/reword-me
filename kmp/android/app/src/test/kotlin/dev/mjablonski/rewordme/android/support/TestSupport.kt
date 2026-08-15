package dev.mjablonski.rewordme.android.support

import dev.mjablonski.rewordme.domain.ApiKeyStore
import dev.mjablonski.rewordme.domain.ConfigStore
import dev.mjablonski.rewordme.domain.Rewording
import dev.mjablonski.rewordme.models.ModelInfo
import dev.mjablonski.rewordme.models.ProviderKind
import dev.mjablonski.rewordme.models.RewordConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * viewModelScope launches on Dispatchers.Main; tests swap in a controllable
 * dispatcher. Main must be set before runTest starts and share its scheduler,
 * or advanceUntilIdle would advance a different clock than the view model's.
 */
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) {
    fun runVm(block: suspend TestScope.() -> Unit) {
        Dispatchers.setMain(dispatcher)
        try {
            runTest(dispatcher) { block() }
        } finally {
            Dispatchers.resetMain()
        }
    }
}

class FakeConfigStore(var config: RewordConfig = RewordConfig()) : ConfigStore {
    var saves = 0
    override fun load(): RewordConfig = config
    override fun save(config: RewordConfig) {
        saves++
        this.config = config
    }
}

class FakeKeyStore(
    private val keys: MutableMap<ProviderKind, String> = mutableMapOf(),
    var persists: Boolean = true
) : ApiKeyStore {
    override fun apiKey(provider: ProviderKind): String? = keys[provider]
    override fun setApiKey(provider: ProviderKind, key: String?): Boolean {
        if (!persists) return false
        if (key.isNullOrEmpty()) keys.remove(provider) else keys[provider] = key
        return true
    }
}

class FakeRewording(
    var models: List<ModelInfo> = listOf(ModelInfo("model-a")),
    var answer: String = "better text",
    var failure: Throwable? = null
) : Rewording {
    var lastSystemPrompt: String? = null
    var listCalls = 0

    override suspend fun listModels(
        provider: ProviderKind,
        apiKey: String,
        endpoint: String?
    ): List<ModelInfo> {
        listCalls++
        failure?.let { throw it }
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
        failure?.let { throw it }
        return answer
    }
}

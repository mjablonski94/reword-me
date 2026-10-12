package dev.mjablonski.rewordme.android.features.home.presentation

import androidx.lifecycle.viewModelScope
import dev.mjablonski.rewordme.android.arch.MviViewModel
import dev.mjablonski.rewordme.android.features.floating.data.FloatingIconSettings
import dev.mjablonski.rewordme.domain.usecase.HasApiKeyUseCase
import dev.mjablonski.rewordme.domain.usecase.LoadConfigUseCase
import kotlinx.coroutines.launch

/**
 * The tutorial screen only needs one fact: can the popup work yet? Keyless
 * providers count as configured; key providers need a stored key.
 */
class HomeViewModel(
    private val loadConfig: LoadConfigUseCase,
    private val hasApiKey: HasApiKeyUseCase,
    private val floatingIcon: FloatingIconSettings
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    init {
        refresh()
    }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Refresh -> refresh()
            HomeIntent.OpenSettings -> sendEffect(HomeEffect.NavigateToSettings)
            HomeIntent.SetUpFloatingIcon -> sendEffect(HomeEffect.NavigateToFloatingIcon)
        }
    }

    private fun refresh() {
        setState {
            copy(floatingIconShown = floatingIcon.serviceEnabled() && floatingIcon.iconEnabled)
        }
        viewModelScope.launch {
            val config = loadConfig.safeAction(Unit).getOrNull() ?: return@launch
            val configured = !config.provider.requiresApiKey ||
                hasApiKey.safeAction(config.provider).getOrDefault(false)
            setState { copy(isConfigured = configured) }
        }
    }
}

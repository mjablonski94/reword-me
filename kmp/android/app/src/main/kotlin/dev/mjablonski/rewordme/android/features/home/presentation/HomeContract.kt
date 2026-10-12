package dev.mjablonski.rewordme.android.features.home.presentation

import dev.mjablonski.rewordme.android.arch.MviEffect
import dev.mjablonski.rewordme.android.arch.MviIntent
import dev.mjablonski.rewordme.android.arch.MviState

data class HomeState(
    /**
     * True once the selected provider can actually serve a request; null
     * until the config has loaded, so neither state flashes on launch.
     */
    val isConfigured: Boolean? = null
) : MviState

sealed interface HomeIntent : MviIntent {
    data object Refresh : HomeIntent
    data object OpenSettings : HomeIntent
}

sealed interface HomeEffect : MviEffect {
    data object NavigateToSettings : HomeEffect
}

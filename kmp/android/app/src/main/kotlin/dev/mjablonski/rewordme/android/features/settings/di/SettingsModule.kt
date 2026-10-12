package dev.mjablonski.rewordme.android.features.settings.di

import dev.mjablonski.rewordme.android.di.APP_SCOPE
import dev.mjablonski.rewordme.android.features.settings.presentation.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule = module {
    viewModel {
        SettingsViewModel(get(), get(), get(), get(), get(), get(), get(), get(APP_SCOPE))
    }
}

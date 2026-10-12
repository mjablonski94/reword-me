package dev.mjablonski.rewordme.android.features.popup.di

import dev.mjablonski.rewordme.android.features.popup.presentation.PopupViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val popupModule = module {
    viewModel { PopupViewModel(get(), get(), get(), get()) }
}

package dev.mjablonski.rewordme.android.features.home.di

import dev.mjablonski.rewordme.android.features.home.presentation.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val homeModule = module {
    viewModel { HomeViewModel(get(), get(), get()) }
}

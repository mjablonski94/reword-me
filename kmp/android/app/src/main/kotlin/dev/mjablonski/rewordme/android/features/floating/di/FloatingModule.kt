package dev.mjablonski.rewordme.android.features.floating.di

import dev.mjablonski.rewordme.android.features.floating.data.AndroidFloatingIconSettings
import dev.mjablonski.rewordme.android.features.floating.data.FloatingIconSettings
import dev.mjablonski.rewordme.android.features.floating.presentation.FieldWriter
import dev.mjablonski.rewordme.android.features.floating.presentation.FloatingHandoff
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val floatingModule = module {
    // One per process: the service and the popup must meet on the same instance.
    single { FloatingHandoff() }
    single<FloatingIconSettings> { AndroidFloatingIconSettings(androidContext()) }
    factory { FieldWriter(get()) }
}

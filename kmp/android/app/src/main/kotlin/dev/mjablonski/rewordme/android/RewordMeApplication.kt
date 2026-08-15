package dev.mjablonski.rewordme.android

import android.app.Application
import dev.mjablonski.rewordme.android.di.appModule
import dev.mjablonski.rewordme.android.features.home.di.homeModule
import dev.mjablonski.rewordme.android.features.popup.di.popupModule
import dev.mjablonski.rewordme.android.features.settings.di.settingsModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class RewordMeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@RewordMeApplication)
            modules(appModule, popupModule, settingsModule, homeModule)
        }
    }
}

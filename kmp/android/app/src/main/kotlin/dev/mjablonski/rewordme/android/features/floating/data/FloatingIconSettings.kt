package dev.mjablonski.rewordme.android.features.floating.data

import android.content.Context
import android.provider.Settings
import dev.mjablonski.rewordme.android.features.floating.presentation.FloatingIconService
import dev.mjablonski.rewordme.android.features.floating.presentation.FloatingRules

/**
 * The two halves of "show the floating icon": Android's accessibility switch,
 * which only the user can flip, and RewordMe's own switch, which hides the
 * icon without sending anyone back into Android settings.
 */
interface FloatingIconSettings {
    /** Android's accessibility setting lists RewordMe's service as on. */
    fun serviceEnabled(): Boolean

    /**
     * RewordMe's own switch. Off until someone asks for the icon - a fresh
     * install must not greet people with a "service is off" warning.
     */
    var iconEnabled: Boolean

    /**
     * The service just started. Turning it on straight in Android's settings
     * is asking for the icon too, unless RewordMe's switch was decided before.
     */
    fun serviceConnected()
}

class AndroidFloatingIconSettings(context: Context) : FloatingIconSettings {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun serviceEnabled(): Boolean = FloatingRules.isServiceEnabled(
        Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ),
        appContext.packageName,
        FloatingIconService::class.java.name
    )

    override var iconEnabled: Boolean
        get() = prefs.getBoolean(KEY_ICON, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ICON, value).apply()
        }

    override fun serviceConnected() {
        if (!prefs.contains(KEY_ICON)) iconEnabled = true
    }

    private companion object {
        const val PREFS = "floating_icon"
        const val KEY_ICON = "show_icon"
    }
}

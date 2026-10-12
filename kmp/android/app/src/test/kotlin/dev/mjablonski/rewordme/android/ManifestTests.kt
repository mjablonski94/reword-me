package dev.mjablonski.rewordme.android

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.WindowManager.LayoutParams
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mjablonski.rewordme.android.features.home.presentation.HomeActivity
import dev.mjablonski.rewordme.android.features.popup.presentation.ProcessTextActivity
import dev.mjablonski.rewordme.android.features.settings.presentation.SettingsActivity
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestTests {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun screensWithTextFieldsResizeForTheKeyboardButNeverOpenIt() {
        // Compose's imePadding() expects adjustResize; without stateHidden
        // those windows pop the keyboard on their own.
        val screens = listOf(
            HomeActivity::class.java,
            SettingsActivity::class.java,
            ProcessTextActivity::class.java
        )
        for (screen in screens) {
            val mode = context.packageManager
                .getActivityInfo(ComponentName(context, screen), 0).softInputMode
            assertEquals(
                LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
                mode and LayoutParams.SOFT_INPUT_MASK_ADJUST,
                screen.simpleName
            )
            assertEquals(
                LayoutParams.SOFT_INPUT_STATE_HIDDEN,
                mode and LayoutParams.SOFT_INPUT_MASK_STATE,
                screen.simpleName
            )
        }
    }

    @Test
    fun thePopupAnswersBothTheSelectionMenuAndTheShareSheet() {
        for (action in listOf(Intent.ACTION_PROCESS_TEXT, Intent.ACTION_SEND)) {
            val intent = Intent(action).setType("text/plain").setPackage(context.packageName)
            val handlers = context.packageManager.queryIntentActivities(intent, 0)
                .map { it.activityInfo.name }
            assertTrue(ProcessTextActivity::class.java.name in handlers, "$action -> $handlers")
        }
    }
}

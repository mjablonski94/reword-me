package dev.mjablonski.rewordme.android

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.WindowManager.LayoutParams
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mjablonski.rewordme.android.features.floating.presentation.FloatingIconService
import dev.mjablonski.rewordme.android.features.home.presentation.HomeActivity
import dev.mjablonski.rewordme.android.features.popup.presentation.ProcessTextActivity
import dev.mjablonski.rewordme.android.features.settings.presentation.SettingsActivity
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun theFloatingIconServiceIsOneOnlyTheSystemCanBind() {
        val component = ComponentName(context, FloatingIconService::class.java)
        val info = context.packageManager.getServiceInfo(component, PackageManager.GET_META_DATA)

        assertEquals(Manifest.permission.BIND_ACCESSIBILITY_SERVICE, info.permission)
        assertTrue(info.metaData.containsKey(AccessibilityService.SERVICE_META_DATA))
        val listed = context.packageManager.queryIntentServices(
            Intent(AccessibilityService.SERVICE_INTERFACE).setPackage(context.packageName),
            0
        ).map { it.serviceInfo.name }
        assertTrue(FloatingIconService::class.java.name in listed, "$listed")
    }

    @Test
    fun theFloatingPopupOpensOnlyExplicitlyInATaskOfItsOwn() {
        // No intent filter carries the floating action: nobody resolves it
        // implicitly, and the token check covers explicit callers.
        val implicit = Intent(ProcessTextActivity.ACTION_FLOATING).setPackage(context.packageName)
        assertTrue(context.packageManager.queryIntentActivities(implicit, 0).isEmpty())

        val intent = ProcessTextActivity.floatingIntent(context, "hej", editable = false, token = "t")
        assertEquals(ProcessTextActivity::class.java.name, intent.component?.className)
        assertEquals("hej", intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT))
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false))
        assertEquals("t", intent.getStringExtra(ProcessTextActivity.EXTRA_FLOATING_TOKEN))
        // Without MULTIPLE_TASK, Android raised RewordMe's home screen behind the popup.
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        assertEquals(flags, intent.flags and flags)
        assertFalse(
            ProcessTextActivity.floatingIntent(context, "hej", editable = true, token = "t")
                .getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        )
    }
}

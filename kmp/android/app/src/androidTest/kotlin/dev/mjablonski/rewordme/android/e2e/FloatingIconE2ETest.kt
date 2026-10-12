package dev.mjablonski.rewordme.android.e2e

import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.net.Uri
import android.graphics.Rect
import android.widget.EditText
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import dev.mjablonski.rewordme.android.R
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith

/**
 * The floating icon on a device: RewordMe's accessibility service on, text
 * selected in the tutorial editor - the one RewordMe field that shows the
 * icon - and the round trip through the popup back into the field.
 *
 * UiAutomator itself connects as an accessibility client, which by default
 * switches every other accessibility service off while it runs; the suite
 * asks it not to, or the service under test would never see a selection.
 * With a second service connected, UiAutomator no longer lists the tutorial's
 * EditText (a View inside Compose) - the service and the user still see it -
 * so the test presses it by position and reads it from the activity.
 */
@RunWith(AndroidJUnit4::class)
class FloatingIconE2ETest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device: UiDevice = run {
        Configurator.getInstance().uiAutomationFlags =
            UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        UiDevice.getInstance(instrumentation)
    }
    private val context: Context = instrumentation.targetContext
    private val pkg = context.packageName
    private val mockHost =
        InstrumentationRegistry.getArguments().getString("mockHost") ?: "10.0.2.2:8765"
    private val shots = File(context.getExternalFilesDir(null), "e2e").apply { mkdirs() }
    private val service = "$pkg/dev.mjablonski.rewordme.android.features.floating.presentation.FloatingIconService"
    private var servicesBefore = ""

    /** The icon's description is the selection-menu label, localized. */
    private val icon: BySelector
        get() = By.pkg(pkg).desc(context.getString(R.string.android_processText))

    @get:Rule
    val screenshotOnFailure = object : TestWatcher() {
        override fun failed(e: Throwable?, description: Description) {
            shot("FAILED_" + description.methodName)
            runCatching {
                device.dumpWindowHierarchy(File(shots, "FAILED_${description.methodName}.xml"))
            }
        }
    }

    @Before
    fun setUp() {
        assumeTrue("mock server at $mockHost is not reachable", reachable(mockHost))
        device.pressHome()
        File(context.filesDir, "config.json").writeText(
            """{"provider":"OLLAMA","model":null,"modelsByProvider":{},"rules":[],""" +
                """"basePrompt":"","ollamaHost":"$mockHost/ok","launchAtLogin":null}"""
        )
        iconSwitch(on = true)
        servicesBefore = shell("settings get secure enabled_accessibility_services").trim()
            .takeUnless { it == "null" || service in it }.orEmpty()
        // am instrument force-stops the app first, and Android drops a
        // force-stopped app's accessibility service; a fresh off/on binds it.
        shell("settings delete secure enabled_accessibility_services")
        SystemClock.sleep(500)
        shell("settings put secure enabled_accessibility_services $service")
        shell("settings put secure accessibility_enabled 1")
        waitUntil("the service never connected") {
            "label=RewordMe" in shell("dumpsys accessibility")
        }
    }

    @After
    fun tearDown() {
        if (servicesBefore.isEmpty()) {
            shell("settings delete secure enabled_accessibility_services")
        } else {
            shell("settings put secure enabled_accessibility_services $servicesBefore")
        }
        device.pressHome()
    }

    @Test
    fun aSelectionGetsTheIconAndReplaceLandsBackInTheField() {
        val word = openTutorial()
        longPress(word)
        val shown = waitFor(icon, 6_000)
        shot("floating_icon_shown")
        assertClearOfTheToolbar(shown.visibleBounds)

        shown.click()
        proofread()
        waitFor(text(R.string.popup_replace)).click()

        waitUntil("the rewrite never landed in the field: ${editorText()}") {
            editorText()?.contains("[mock rewrite]") == true
        }
        shot("floating_icon_replaced")
        // The selection became the rewrite: nothing left to offer the icon for.
        assertTrue("the icon outlived the selection", device.wait(Until.gone(icon), 4_000))
    }

    /**
     * The case the icon exists for: Google Messages cannot list "Reword me"
     * in its menu. Its draft field is not focused yet, so one long-press both
     * focuses and selects - and the selection Messages reports comes from a
     * view it swaps out right after, with nothing sent for the real field.
     */
    @Test
    fun inGoogleMessagesTheFirstLongPressGetsTheIconAndReplaceLands() {
        assumeTrue("Google Messages is not installed", installed(MESSAGES))
        // A new number each run: an existing thread would keep its old draft.
        val number = "+4860" + (System.currentTimeMillis() % 10_000_000).toString().padStart(7, '0')
        context.startActivity(
            Intent(Intent.ACTION_SENDTO, Uri.parse("sms:$number"))
                .setPackage(MESSAGES)
                .putExtra("sms_body", DRAFT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        assumeTrue("Messages showed no draft field (a first-run screen?)", settledDraft() != null)

        var shown: UiObject2? = null
        for (attempt in 1..2) {
            val draft = settledDraft() ?: break
            // A word on the first line ("sorry"): a one-letter word would only
            // get the popup's "too short" answer.
            longPress(Point(draft.left + draft.width() / 4, draft.top + dp(26)))
            shown = device.wait(Until.findObject(icon), 6_000)
            if (shown != null) break
            // Only a press that missed the draft (Messages still laying it
            // out) earns another go; a selection without the icon is the bug.
            assertFalse("the draft holds a selection, but no icon appeared", draftHasSelection())
            shot("messages_missed_press_$attempt")
            device.pressBack()
            SystemClock.sleep(800)
        }
        val found = shown ?: throw AssertionError("no icon after pressing the draft")
        shot("messages_icon_shown")
        assertClearOfTheToolbar(found.visibleBounds)

        found.click()
        proofread()
        waitFor(text(R.string.popup_replace)).click()

        // Read back the same way the service sees it: the focused field.
        waitUntil("the rewrite never landed in the Messages field") {
            focusedText()?.contains("[mock rewrite]") == true
        }
        shot("messages_replaced")
    }

    @Test
    fun deselectingHidesTheIcon() {
        val word = openTutorial()
        longPress(word)
        waitFor(icon, 6_000)

        // A tap on another word of the line turns the selection into a
        // cursor (below the word the handles hang, and take the tap).
        device.click(device.displayWidth * 4 / 5, word.y)

        assertTrue("the icon stayed after the selection ended", device.wait(Until.gone(icon), 4_000))
    }

    @Test
    fun rewordMesOwnSwitchHidesTheIconWithoutAndroidSettings() {
        iconSwitch(on = false)
        longPress(openTutorial())

        SystemClock.sleep(2_500)
        assertNull("the icon showed with RewordMe's switch off", device.findObject(icon))
    }

    @Test
    fun closingThePopupBringsTheIconBackForAnotherGo() {
        longPress(openTutorial())
        waitFor(icon, 6_000).click()
        waitFor(text(R.string.popup_proofread))

        device.pressBack()

        // The text is still selected; the icon comes back next to it.
        waitFor(icon, 6_000)
        assertFalse(
            "the popup never closed",
            device.hasObject(text(R.string.popup_proofread))
        )
    }

    /**
     * The icon is placed clear of the selection toolbar: above it, or below
     * the selection when there is no room at the top - never on its buttons.
     */
    private fun assertClearOfTheToolbar(iconBounds: Rect) {
        val toolbarItems = listOf(android.R.string.copy, android.R.string.selectAll, android.R.string.cut)
            .mapNotNull { device.findObject(By.text(label(context.getString(it)))) }
        for (item in toolbarItems) {
            assertFalse(
                "the icon $iconBounds covers \"${item.text}\" ${item.visibleBounds}",
                Rect.intersects(iconBounds, item.visibleBounds)
            )
        }
    }

    /**
     * Opens the tutorial and returns a point on a word of its sample text:
     * the sample sits right below the floating icon's line ("it is on", or
     * the set-up link while RewordMe's switch is off). Small phones need a
     * swipe first to bring it up.
     */
    private fun openTutorial(): Point {
        context.startActivity(
            Intent().setClassName(pkg, HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        val hint = By.text(Pattern.compile(
            Pattern.quote(context.getString(R.string.floating_homeOn)) + "|" +
                Pattern.quote(context.getString(R.string.floating_homeAction))
        ))
        waitFor(hint)
        repeat(4) {
            val bottom = waitFor(hint).visibleBounds.bottom
            if (bottom + dp(120) < device.displayHeight * 9 / 10) {
                // Let the screen settle: a long-press during layout reads as a tap.
                SystemClock.sleep(700)
                return Point(device.displayWidth / 3, bottom + dp(58))
            }
            val x = device.displayWidth / 2
            device.swipe(x, device.displayHeight * 3 / 4, x, device.displayHeight / 3, 30)
            SystemClock.sleep(500)
        }
        throw AssertionError("the tutorial text never came into view")
    }

    /** About 0.8 s in place: Android's long-press selects the word under it. */
    private fun longPress(point: Point) {
        device.swipe(point.x, point.y, point.x + 1, point.y + 1, 160)
    }

    private fun editorText(): String? {
        var text: String? = null
        instrumentation.runOnMainSync {
            text = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .firstNotNullOfOrNull { it.findViewById<EditText>(R.id.home_try_it_editor) }
                ?.text?.toString()
        }
        return text
    }

    /**
     * Where Messages' draft field is. UiAutomator misses classic views while
     * the service under test shares this process, so the raw accessibility
     * connection is asked by view ID first; the newer, Compose-based Messages
     * is also found next to the button left of the field.
     */
    private fun draftNode(): android.view.accessibility.AccessibilityNodeInfo? = runCatching {
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            .rootInActiveWindow
            ?.findAccessibilityNodeInfosByViewId("$MESSAGES:id/compose_message_text")
            ?.firstOrNull()
    }.getOrNull()

    private fun draftRect(): Rect? {
        draftNode()?.let { node -> return Rect().also(node::getBoundsInScreen) }
        return device.findObject(By.pkg(MESSAGES).desc(MESSAGES_MORE))?.visibleBounds?.let { row ->
            Rect(row.right, row.top - dp(12), device.displayWidth - dp(120), row.bottom)
        }
    }

    /** The draft field's bounds once Messages has stopped laying it out (it grows as the text loads). */
    private fun settledDraft(): Rect? {
        // Messages now and then opens the thread without the sms_body draft.
        // Setting the text through accessibility leaves the field unfocused,
        // which is the case under test.
        draftNode()?.takeIf { it.text?.toString()?.contains("sorry") != true }?.performAction(
            android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,
            android.os.Bundle().apply {
                putCharSequence(
                    android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    DRAFT
                )
            }
        )
        var last: Rect? = null
        repeat(30) {
            val now = draftRect()
            if (now != null && now == last) return now
            last = now
            SystemClock.sleep(400)
        }
        return null
    }

    private fun draftHasSelection(): Boolean {
        val node = draftNode() ?: return false
        return node.textSelectionStart >= 0 && node.textSelectionStart != node.textSelectionEnd
    }

    /** The text of the field with input focus, through the test's own accessibility connection. */
    private fun focusedText(): String? = runCatching {
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            .findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
            ?.text?.toString()
    }.getOrNull()

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    /** Proofread, with one Try again if the emulator's network drops a connection. */
    private fun proofread() {
        waitFor(text(R.string.popup_proofread)).click()
        device.wait(Until.hasObject(By.text(Pattern.compile(
            Pattern.quote(context.getString(R.string.popup_again)) + "|" +
                Pattern.quote(context.getString(R.string.android_errorNetwork))
        ))), 10_000)
        if (device.findObject(text(R.string.android_errorNetwork)) != null) {
            waitFor(text(R.string.popup_tryAgain)).click()
        }
        waitFor(text(R.string.popup_again))
    }

    // Through the shell: the app's own PackageManager cannot see other apps
    // (Android 11 package visibility), and the test runs in the app's process.
    private fun installed(packageName: String): Boolean =
        "package:" in shell("pm path $packageName")

    private fun iconSwitch(on: Boolean) {
        context.getSharedPreferences("floating_icon", Context.MODE_PRIVATE)
            .edit().putBoolean("show_icon", on).commit()
    }

    private fun text(id: Int): BySelector = By.text(context.getString(id))

    private fun label(text: String): Pattern = Pattern.compile("(?i)" + Pattern.quote(text))

    private fun waitFor(selector: BySelector, timeout: Long = 10_000): UiObject2 =
        device.wait(Until.findObject(selector), timeout)
            ?: throw AssertionError("not found within ${timeout}ms: $selector")

    private fun waitUntil(message: String, condition: () -> Boolean) {
        repeat(40) {
            if (condition()) return
            SystemClock.sleep(250)
        }
        throw AssertionError(message)
    }

    // The same flags as the device: a plain getUiAutomation() reconnects with
    // the defaults and switches the service under test off again.
    private fun shell(command: String): String =
        instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            .executeShellCommand(command).use { fd ->
            java.io.FileInputStream(fd.fileDescriptor).bufferedReader().readText()
        }

    private fun shot(name: String) {
        runCatching { device.takeScreenshot(File(shots, "$name.png")) }
    }

    private fun reachable(hostPort: String): Boolean = runCatching {
        val (host, port) = hostPort.substringBefore('/').split(':')
        Socket().use { it.connect(InetSocketAddress(host, port.toInt()), 2_000) }
        true
    }.getOrDefault(false)

    private companion object {
        const val HOME = "dev.mjablonski.rewordme.android.features.home.presentation.HomeActivity"
        const val MESSAGES = "com.google.android.apps.messaging"
        val MESSAGES_MORE: Pattern = Pattern.compile("Show more options|Expand attachment buttons")
        const val DRAFT = "hey sorry for the late reply, i cant make it tommorow but maybe friday works for you?"
    }
}

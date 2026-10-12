package dev.mjablonski.rewordme.android.e2e

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import dev.mjablonski.rewordme.android.R
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith

/**
 * End to end on a real device or emulator, the way a user gets there: the
 * system selection menu, the popup, a provider round trip and the text coming
 * back into the field. The provider is the OpenAI-compatible mock server used
 * by the E2E report, reached from an emulator as 10.0.2.2:8765 (override with
 * -Pandroid.testInstrumentationRunnerArguments.mockHost=host:port). Every
 * test is skipped when the mock server is not running.
 */
@RunWith(AndroidJUnit4::class)
class RewordMeE2ETest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context: Context = instrumentation.targetContext
    private val pkg = context.packageName
    private val mockHost =
        InstrumentationRegistry.getArguments().getString("mockHost") ?: "10.0.2.2:8765"
    private val shots = File(context.getExternalFilesDir(null), "e2e").apply { mkdirs() }

    @get:Rule
    val screenshotOnFailure = object : TestWatcher() {
        override fun failed(e: Throwable?, description: Description) {
            shot("FAILED_" + description.methodName)
        }
    }

    @Before
    fun setUp() {
        assumeTrue("mock server at $mockHost is not reachable", reachable(mockHost))
        shell("settings put system font_scale 1.0")
        device.pressHome()
        configure(host = "$mockHost/ok")
    }

    @After
    fun tearDown() {
        shell("settings put system font_scale 1.0")
        device.pressHome()
    }

    @Test
    fun tutorialSelectionIsRewordedAndReplacedInPlace() {
        assumeTrue(
            "UiAutomator cannot reach the floating selection toolbar before Android 9",
            Build.VERSION.SDK_INT >= 28
        )
        launch(HOME)
        // Slow emulators take a moment; a swipe before that lands on the launcher.
        waitFor(By.pkg(pkg).depth(0))
        // Small phones show the tutorial field only after a scroll.
        // A finger swipe: older Compose does not mark the column scrollable
        // for accessibility, so UiObject2.scroll has nothing to act on there.
        repeat(4) {
            val field = device.findObject(By.pkg(pkg).clazz(EDIT_TEXT))
            if (field != null && field.visibleBounds.height() > 100) return@repeat
            val x = device.displayWidth / 2
            device.swipe(x, device.displayHeight * 3 / 4, x, device.displayHeight / 4, 30)
            SystemClock.sleep(500)
        }
        val editor = waitFor(By.pkg(pkg).clazz(EDIT_TEXT))
        selectAllAndReword(editor)
        shot("tutorial_menu")

        proofread()
        waitFor(text(R.string.popup_replace)).click()

        val replaced = device.wait(
            Until.findObject(By.clazz(EDIT_TEXT).textStartsWith("[mock rewrite]")), 8_000
        )
        shot("tutorial_replaced")
        assertNotNull("the rewrite never landed in the field", replaced)
        assertFalse("the sample must not summon the keyboard", imeShown())
    }

    @Test
    fun missingKeyLeadsFromThePopupStraightToSettings() {
        configure(provider = "GEMINI", host = mockHost)
        context.getSharedPreferences("rewordme.keys", Context.MODE_PRIVATE).edit().clear().commit()
        startPopup(SAMPLE)

        waitFor(text(R.string.home_setupHint))
        shot("setup")
        waitFor(text(R.string.home_openSettings)).click()

        waitFor(text(R.string.settings_windowTitle))
        assertFalse("Settings opened the keyboard by itself", imeShownAfter(1_000))
    }

    @Test
    fun aResultSurvivesStrayTapsAndBackStepsBackFirst() {
        startPopup(SAMPLE)
        proofread()
        waitFor(text(R.string.popup_replace))
        assertFalse("the result opened the keyboard over its own buttons", imeShownAfter(800))

        device.click(device.displayWidth / 2, (device.displayHeight * 0.09).toInt())
        SystemClock.sleep(600)
        assertNotNull("a backdrop tap threw the result away", device.findObject(text(R.string.popup_replace)))

        device.pressBack()
        waitFor(text(R.string.popup_proofread))
        device.pressBack()
        assertTrue("Back from the menu did not close the popup", gone(text(R.string.popup_proofread)))
    }

    @Test
    fun failuresReadAsPlainLanguageWithAWayOut() {
        configure(host = "$mockHost/e401")
        startPopup(SAMPLE)
        waitFor(text(R.string.popup_proofread)).click()
        waitFor(text(R.string.error_invalidKey))
        waitFor(text(R.string.home_openSettings))
        assertNull(device.findObject(text(R.string.popup_tryAgain)))
        shot("error_invalid_key")

        configure(host = "$mockHost/html")
        device.pressBack()
        waitFor(text(R.string.popup_proofread)).click()
        waitFor(By.textContains("502 Bad Gateway nginx"))
        assertNull("HTML reached the screen", device.findObject(By.textContains("<html")))

        // Nothing listens on the device's own port 9: an immediate refusal.
        configure(host = "127.0.0.1:9")
        device.pressBack()
        waitFor(text(R.string.popup_proofread)).click()
        waitFor(text(R.string.android_errorNetwork))
        waitFor(text(R.string.popup_tryAgain))
        shot("error_network")
    }

    @Test
    fun readOnlyHostsGetCopyFirst() {
        startPopup(SAMPLE, readonly = true)
        proofread()

        waitFor(text(R.string.android_replaceUnavailable))
        assertNull(device.findObject(text(R.string.popup_replace)))
        waitFor(text(R.string.popup_copy)).click()
        assertTrue("Copy did not close the popup", gone(text(R.string.popup_copy)))
    }

    @Test
    fun theShareSheetEntryWorksWithoutAResultChannel() {
        val handlers = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_SEND).setType("text/plain"), 0
        )
        assertTrue("not offered in the share sheet", handlers.any { it.activityInfo.packageName == pkg })

        startPopup(SAMPLE, action = Intent.ACTION_SEND)
        proofread()
        waitFor(text(R.string.android_replaceUnavailable))
        assertNull(device.findObject(text(R.string.popup_replace)))
    }

    @Test
    fun tooShortSelectionsExplainThemselves() {
        startPopup("ok")
        waitFor(text(R.string.android_selectionTooShort))
    }

    @Test
    fun closingFadesOutAndFinishes() {
        startPopup(SAMPLE)
        waitFor(text(R.string.popup_proofread))
        waitFor(By.desc(context.getString(R.string.android_close))).click()

        assertTrue("the popup never finished", gone(text(R.string.popup_proofread)))
        assertFalse(
            "ProcessTextActivity is still resumed",
            shell("dumpsys activity activities").lines()
                .filter { "mResumedActivity" in it || "ResumedActivity" in it }
                .any { "ProcessTextActivity" in it }
        )
    }

    @Test
    fun settingsKeyboardLeavesNoStaleSpaceBehind() {
        configure(provider = "OLLAMA", host = "$mockHost/ok")
        launch(HOME)
        waitFor(text(R.string.home_openSettings)).click()
        waitFor(text(R.string.settings_windowTitle))
        assertFalse("Settings opened the keyboard by itself", imeShownAfter(1_000))
        val before = settingsViewport()

        waitFor(By.clazz(EDIT_TEXT)).click()
        assertTrue("tapping a field did not open the keyboard", imeShownAfter(1_500))
        device.pressBack()
        SystemClock.sleep(1_000)
        assertFalse(imeShown())

        shot("settings_after_keyboard")
        assertEquals("the keyboard left stale space behind", before, settingsViewport())
    }

    @Test
    fun largeFontsKeepEveryResultActionReachable() {
        shell("settings put system font_scale 1.3")
        configure(host = "$mockHost/long")
        startPopup(SAMPLE)
        proofread()
        waitFor(text(R.string.popup_again))

        // Scroll the card itself (the largest scrollable), not the result well.
        val replaceButton = By.clickable(true).hasDescendant(text(R.string.popup_replace))
        repeat(6) {
            if (device.findObject(replaceButton)?.visibleBounds?.let { it.height() > 0 } == true) return@repeat
            device.findObjects(By.scrollable(true)).maxByOrNull { it.visibleBounds.height() }
                ?.scroll(Direction.DOWN, 0.8f)
        }
        val replace = waitFor(replaceButton)
        shot("large_font_result")
        val bounds = replace.visibleBounds
        assertTrue("Replace is squeezed: $bounds", bounds.width() > device.displayWidth / 2)
        replace.click()
        assertTrue("Replace did not finish the popup", gone(text(R.string.popup_replace)))
    }

    // --- helpers ---------------------------------------------------------------

    /**
     * Proofread and wait for the result. Emulator networking drops the odd
     * connection when several instances share the host; the popup then shows
     * its network error, and the test does what a user would: Try again, once.
     */
    private fun proofread() {
        waitFor(text(R.string.popup_proofread)).click()
        val result = text(R.string.popup_again)
        val offline = text(R.string.android_errorNetwork)
        device.wait(Until.hasObject(By.text(Pattern.compile(
            Pattern.quote(context.getString(R.string.popup_again)) + "|" +
                Pattern.quote(context.getString(R.string.android_errorNetwork))
        ))), 10_000)
        if (device.findObject(offline) != null) {
            shot("network_retry")
            waitFor(text(R.string.popup_tryAgain)).click()
        }
        waitFor(result)
    }

    /**
     * What a stale keyboard inset would shrink: the scrolling viewport when the
     * screen scrolls, else the position of the bottom-most fixed control.
     */
    private fun settingsViewport(): String =
        device.findObject(By.scrollable(true))?.let { "scroll ${it.visibleBounds.height()}" }
            ?: "button ${waitFor(text(R.string.provider_loadModels)).visibleBounds}"

    private fun text(id: Int): BySelector = By.text(context.getString(id))

    private fun waitFor(selector: BySelector, timeout: Long = 10_000): UiObject2 =
        device.wait(Until.findObject(selector), timeout)
            ?: throw AssertionError("not found within ${timeout}ms: $selector")

    private fun gone(selector: BySelector): Boolean = device.wait(Until.gone(selector), 4_000)

    private fun launch(activity: String) {
        context.startActivity(
            Intent().setClassName(pkg, activity)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        device.waitForIdle()
    }

    private fun startPopup(
        text: String,
        readonly: Boolean = false,
        action: String = Intent.ACTION_PROCESS_TEXT
    ) {
        val intent = Intent(action).setClassName(pkg, POPUP).setType("text/plain")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (action == Intent.ACTION_SEND) {
            intent.putExtra(Intent.EXTRA_TEXT, text)
        } else {
            intent.putExtra(Intent.EXTRA_PROCESS_TEXT, text)
                .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, readonly)
        }
        context.startActivity(intent)
    }

    /**
     * Long-press, Select all, then "Reword me" from the toolbar or its
     * overflow. Android 8-9 list third-party actions only from the second
     * selection in a field on, so a miss is retried with a fresh selection.
     */
    private fun selectAllAndReword(editor: UiObject2) {
        repeat(3) {
            editor.longClick()
            SystemClock.sleep(800)
            clickMenuItem(label(context.getString(android.R.string.selectAll)))
            SystemClock.sleep(500)
            // Our own, localized label: "Przeredaguj" on a Polish device.
            if (clickMenuItem(label(context.getString(R.string.android_processText)))) return
            device.pressBack()
            SystemClock.sleep(500)
        }
        shot("reword_missing")
        fail("\"Reword me\" never appeared in the selection menu")
    }

    private fun label(text: String): Pattern =
        Pattern.compile("(?i)" + Pattern.quote(text))

    private fun clickMenuItem(label: Pattern): Boolean {
        device.wait(Until.findObject(By.text(label)), 1_500)?.let {
            it.click()
            return true
        }
        val more = device.findObject(By.desc(MORE_OPTIONS)) ?: return false
        more.click()
        val item = device.wait(Until.findObject(By.text(label)), 1_500) ?: return false
        item.click()
        return true
    }

    private fun configure(provider: String = "OLLAMA", host: String) {
        File(context.filesDir, "config.json").writeText(
            """{"provider":"$provider","model":null,"modelsByProvider":{},"rules":[],""" +
                """"basePrompt":"","ollamaHost":"$host","launchAtLogin":null}"""
        )
    }

    private fun imeShown(): Boolean = "mInputShown=true" in shell("dumpsys input_method")

    private fun imeShownAfter(millis: Long): Boolean {
        SystemClock.sleep(millis)
        return imeShown()
    }

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { fd ->
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
        const val POPUP =
            "dev.mjablonski.rewordme.android.features.popup.presentation.ProcessTextActivity"
        const val EDIT_TEXT = "android.widget.EditText"
        const val SAMPLE = "hey just wanted to check if you have time to look at the report"
        // The floating toolbar's overflow button; its description is a
        // private framework string, so the locales the suite runs in are listed.
        val MORE_OPTIONS: Pattern = Pattern.compile("(?i)more options|więcej opcji")
    }
}

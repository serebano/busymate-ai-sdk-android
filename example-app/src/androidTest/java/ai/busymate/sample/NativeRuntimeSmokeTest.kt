package ai.busymate.sample

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import java.io.File
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeRuntimeSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private fun screenshot(name: String) {
        val directory = File(context.getExternalFilesDir(null), "runtime").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(directory, "$name.png")))
    }
    // Native Button themes expose an all-caps label to accessibility. Match the
    // label without case while keeping its complete literal text (including /).
    private fun selector(text: String) = UiSelector().textMatches("(?i)" + Pattern.quote(text))
    private fun control(text: String) = device.findObject(selector(text))
    private fun scrollTo(text: String) {
        val scroll = UiScrollable(UiSelector().scrollable(true).instance(0))
        scroll.setMaxSearchSwipes(25)
        assertTrue("Settings control is reachable: $text", scroll.scrollIntoView(selector(text)))
    }
    @After fun retainRuntimeDiagnostics() {
        val directory = File(context.getExternalFilesDir(null), "runtime").apply { mkdirs() }
        device.dumpWindowHierarchy(File(directory, "final-window.xml"))
        screenshot("06-final-window")
    }
    private fun noMicrophonePermission() {
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        assertFalse(device.hasObject(By.pkg("com.android.permissioncontroller")))
        assertFalse(device.hasObject(By.pkg("com.google.android.permissioncontroller")))
    }
    @Test fun realDemoSettingsRecreateWithoutOpeningPermission() {
        context.startActivity(Intent(context, ChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 20_000))
        assertTrue(device.wait(Until.hasObject(By.clazz("android.webkit.WebView")), 20_000))
        noMicrophonePermission()
        screenshot("01-real-demo-start-no-permission")
        scrollTo("Native microphone adapter")
        assertTrue(control("Native microphone adapter").isChecked)
        control("Native microphone adapter").click()
        assertFalse(control("Native microphone adapter").isChecked)
        screenshot("02-native-microphone-disabled")
        scrollTo("Apply settings / recreate WebView safely")
        control("Apply settings / recreate WebView safely").click()
        assertTrue(device.wait(Until.hasObject(By.text("Assistant")), 15_000))
        scrollTo("Native microphone adapter")
        assertFalse("Applied configuration survived real Activity recreation", control("Native microphone adapter").isChecked)
        noMicrophonePermission()
        screenshot("03-disabled-after-activity-recreation")
        scrollTo("Reset demo (guest / defaults)")
        control("Reset demo (guest / defaults)").click()
        assertTrue(device.wait(Until.hasObject(By.text("Assistant")), 15_000))
        scrollTo("Native microphone adapter")
        assertTrue(control("Native microphone adapter").isChecked)
        noMicrophonePermission()
        screenshot("04-guest-reset-restores-adapter")
        device.pressHome()
        context.startActivity(Intent(context, ChatActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName).depth(0)), 15_000))
        noMicrophonePermission()
        screenshot("05-resume-no-permission")
    }
}

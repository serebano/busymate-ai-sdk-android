package ai.busymate.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.Condition
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.File
import java.util.regex.Pattern
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Four separate invocations, each with fresh owned-demo data/permissions. */
@RunWith(AndroidJUnit4::class)
@RequiresApi(34)
class FirstTapPermissionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val permissionPackage = Pattern.compile("com\\.(android|google\\.android)\\.permissioncontroller")
    private fun permissionWindow() = By.pkg(permissionPackage)
    private fun hostedControl(label: String, timeout: Long): UiObject2? =
        device.wait(object : Condition<UiDevice, UiObject2> {
            override fun apply(device: UiDevice): UiObject2? {
                // WebView hydration can replace the composer after its first
                // accessibility snapshot. Query fresh nodes on every poll;
                // never infer a tap target from screenshot coordinates.
                assertTrue("Accessibility cache must refresh", instrumentation.uiAutomation.clearCache())
                return device.findObject(By.text(label).pkg(context.packageName))
            }
        }, timeout)
    private fun evidence(name: String) {
        val directory = File(context.getExternalFilesDir(null), "permissions").apply { mkdirs() }
        assertTrue(device.takeScreenshot(File(directory, "$name.png")))
        device.dumpWindowHierarchy(File(directory, "$name.xml"))
    }
    private fun firstTap(source: String, grant: Boolean) {
        assumeTrue("Use gated test-permissions.sh after the native site is live",
            InstrumentationRegistry.getArguments().getString("permissionSuite") == "1")
        assertTrue("Permission harness requires API34+", Build.VERSION.SDK_INT >= 34)
        val idleTimeout = Configurator.getInstance().getWaitForIdleTimeout()
        // Poll explicit native/WebView conditions. Avoid a generic idle wait
        // after granting permission, so the real app can be closed promptly.
        Configurator.getInstance().setWaitForIdleTimeout(0)
        try {
            ActivityScenario.launch(ChatActivity::class.java).use {
                try {
                    assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
                    // Android WebView exposes these actual hosted aria-labels
                    // as Button text (verified in the smoke hierarchy).
                    val dictation = hostedControl("Start voice input", 90_000)
                    val voice = hostedControl("Enter voice mode", 15_000)
                    assertNotNull("Actual hosted dictation must render", dictation)
                    assertNotNull("Actual hosted voice mode must render", voice)
                    assertFalse("Opening chat must not request OS permission", device.hasObject(permissionWindow()))
                    evidence("01-$source-visible-hosted-chat-before-tap-no-prompt")
                    (if (source == "dictation") dictation else voice)!!.click()

                    assertTrue("First hosted tap must open the actual Android dialog",
                        device.wait(Until.hasObject(permissionWindow()), 15_000))
                    val message = device.wait(Until.findObject(By.res(Pattern.compile("com\\.(android|google\\.android)\\.permissioncontroller:id/permission_message"))), 5_000)
                    assertNotNull("Native permission request message must exist", message)
                    assertTrue("Dialog must request this app's microphone", message!!.text.contains("Busymate SDK example") &&
                        (message.text.contains("audio", ignoreCase = true) || message.text.contains("microphone", ignoreCase = true)))
                    evidence("02-$source-actual-android-microphone-dialog")
                    val id = if (grant) "permission_allow_foreground_only_button" else "permission_deny_button"
                    val button = device.wait(Until.findObject(By.res(Pattern.compile("com\\.(android|google\\.android)\\.permissioncontroller:id/$id"))), 5_000)
                    assertNotNull("Expected native permission choice must exist", button)
                    button!!.click()
                    assertTrue(device.wait(Until.gone(permissionWindow()), 5_000))
                    assertEquals(if (grant) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED,
                        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
                    evidence("03-$source-${if (grant) "grant" else "deny"}-chosen")
                } finally {
                    // Closing ActivityScenario destroys the real WebView. Do
                    // not tap dictation Stop, which would submit captured audio.
                    evidence("99-final-window")
                }
            }
        } finally {
            Configurator.getInstance().setWaitForIdleTimeout(idleTimeout)
        }
    }
    @Test fun dictationGrant() = firstTap("dictation", true)
    @Test fun dictationDeny() = firstTap("dictation", false)
    @Test fun voiceGrant() = firstTap("voice", true)
    @Test fun voiceDeny() = firstTap("voice", false)
}

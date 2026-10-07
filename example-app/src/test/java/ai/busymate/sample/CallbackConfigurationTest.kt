package ai.busymate.sample
import org.junit.Assert.*
import org.junit.Test
class CallbackConfigurationTest {
    @Test fun disabledCallbacksAreAbsentFromActualSdkConfig() {
        val config = DemoSettings(closeEnabled = false, actionsEnabled = false).identityConfig({ _, _ -> }, {}, {}, {})
        assertNull(config.onClose)
        assertNull(config.onAction)
    }
    @Test fun enabledCallbacksHandleOnlyTheSupportedActions() {
        var closed = false
        val config = DemoSettings().identityConfig({ _, _ -> }, {}, { closed = true }, {})
        assertTrue(config.onAction!!.invoke("ready", emptyMap()))
        assertFalse(config.onAction!!.invoke("arbitrary", emptyMap()))
        config.onClose!!.invoke()
        assertTrue(closed)
    }
}

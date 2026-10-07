package ai.busymate.sample
import org.junit.Assert.*
import org.junit.Test
class DemoSettingsTest {
    @Test fun hostedGuestDefaultsAreValid() { assertNull(DemoSettings().validationError()) }
    @Test fun rejectsUntrustedOrigins() {
        for (origin in listOf("http://example.com", "https://*.example.com", "https://user:pass@example.com", "https://example.com/path", "https://example.com?query=1")) {
            assertNotNull(origin, DemoSettings(extraOrigins = origin).validationError())
        }
    }
    @Test fun acceptsExactMappedOriginsAndPorts() { assertNull(DemoSettings(extraOrigins = "https://support.example.com,https://support.example.com:8443").validationError()) }
    @Test fun rejectsUnsafeBackendAndChatUrl() {
        assertNotNull(DemoSettings(backendUrl = "http://backend.example.com").validationError())
        assertNotNull(DemoSettings(chatUrl = "javascript:alert(1)").validationError())
    }
    @Test fun rejectsInvalidAssistant() { assertNotNull(DemoSettings(assistant = "tenant/other").validationError()) }
}

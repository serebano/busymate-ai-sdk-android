package ai.busymate.sample
import org.junit.Assert.*
import org.junit.Test
class BackendReplyTest {
    @Test fun mapsOnlyNonceBoundBackendReply() {
        val result = BackendMint.reply("fixture-token", "asked-nonce", "asked-nonce")
        assertEquals("fixture-token", result.token)
        assertEquals("asked-nonce", result.nonce)
    }
    @Test fun rejectsMismatchedNonceOrEmptyToken() {
        for ((token, nonce, expected) in listOf(Triple("fixture", "other", "asked"), Triple("", "asked", "asked"))) {
            try { BackendMint.reply(token, nonce, expected); fail("Unsafe backend reply accepted") }
            catch (_: IllegalStateException) { }
        }
    }
}

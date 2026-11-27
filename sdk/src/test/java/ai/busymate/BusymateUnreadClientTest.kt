package ai.busymate

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** How the SDK reads `POST /api/chat/unread`: 204 clears, a valid 200 sets, everything else changes nothing. */
class BusymateUnreadClientTest {
    private val unchanged = BusymateUnreadClient.Answer.Unchanged

    @Test fun requestBodyIsTheContractsTwoFields() {
        val body = JSONObject(BusymateUnreadClient.body("acme", "test-visitor-token-aaaa"))
        assertEquals(setOf("a", "v"), body.keys().asSequence().toSet())
        assertEquals("acme", body.getString("a"))
        assertEquals("test-visitor-token-aaaa", body.getString("v"))
    }

    @Test fun noContentMeansNothingUnread() {
        assertEquals(BusymateUnreadClient.Answer.Known(BusymateUnread.NONE), BusymateUnreadClient.interpret(204, null))
    }

    @Test fun okCarriesCountPreviewAndTime() {
        assertEquals(BusymateUnreadClient.Answer.Known(BusymateUnread(2, "Still there?", "2026-11-27T09:41:05Z")),
            BusymateUnreadClient.interpret(200, """{"n":2,"t":"Still there?","at":"2026-11-27T09:41:05Z"}"""))
    }

    @Test fun anythingElseLeavesTheLastKnownCount() {
        assertSame(unchanged, BusymateUnreadClient.interpret(200, null))
        assertSame(unchanged, BusymateUnreadClient.interpret(200, "<html>captive portal</html>"))
        assertSame(unchanged, BusymateUnreadClient.interpret(200, """{"n":"many"}"""))
        assertSame(unchanged, BusymateUnreadClient.interpret(200, "x".repeat(5000)))
        for (status in listOf(301, 302, 400, 401, 403, 404, 429, 500, 502, 503)) {
            assertSame(unchanged, BusymateUnreadClient.interpret(status, """{"n":9}"""))
        }
    }

    @Test fun aBadOriginIsNeverCalled() {
        assertSame(unchanged, BusymateUnreadClient.ask("http://busymate.ai", "acme", "test-visitor-token-aaaa", "0"))
        assertSame(unchanged, BusymateUnreadClient.ask("https://busymate.ai/evil", "acme", "test-visitor-token-aaaa", "0"))
    }
}

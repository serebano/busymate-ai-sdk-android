package ai.busymate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The follow-ups channel accepts exactly two message types from exactly the chat's origins. */
class BusymateMessagesTest {
    private val token = "eyJ0IjoiYWNtZSJ9.c2lnbmF0dXJl-_~+/=:0123"

    private fun unread(raw: String) = (BusymateMessages.parse(raw) as? BusymateMessage.Unread)?.value

    @Test fun unreadCarriesCountPreviewAndTime() {
        assertEquals(BusymateUnread(2, "Your order has shipped.", "2026-11-27T09:41:05.123Z"),
            unread("""{"type":"support.chat.v1.unread","n":2,"t":"Your order has shipped.","at":"2026-11-27T09:41:05.123Z"}"""))
    }

    @Test fun unreadAcceptsIntegerValuedFloatAndMissingOptionalFields() {
        assertEquals(BusymateUnread(3, null, null), unread("""{"type":"support.chat.v1.unread","n":3.0}"""))
        assertEquals(BusymateUnread(1, "Hi", null), unread("""{"type":"support.chat.v1.unread","n":1,"t":"Hi","at":null}"""))
        assertEquals("2026-11-27T11:41:05+02:00", unread("""{"type":"support.chat.v1.unread","n":1,"at":"2026-11-27T11:41:05+02:00"}""")?.at)
    }

    @Test fun zeroMeansNothingUnreadWhateverElseIsSent() {
        assertEquals(BusymateUnread.NONE, unread("""{"type":"support.chat.v1.unread","n":0,"t":"stale","at":"2026-11-27T09:41:05Z"}"""))
    }

    @Test fun unreadWithABrokenFieldIsDropped() {
        for (bad in listOf(
            """{"type":"support.chat.v1.unread"}""",
            """{"type":"support.chat.v1.unread","n":-1}""",
            """{"type":"support.chat.v1.unread","n":1.5}""",
            """{"type":"support.chat.v1.unread","n":"2"}""",
            """{"type":"support.chat.v1.unread","n":true}""",
            """{"type":"support.chat.v1.unread","n":1,"t":7}""",
            """{"type":"support.chat.v1.unread","n":1,"t":{"html":"x"}}""",
            """{"type":"support.chat.v1.unread","n":1,"at":"yesterday"}""",
            """{"type":"support.chat.v1.unread","n":1,"at":1764236465}""",
        )) assertNull(bad, BusymateMessages.parse(bad))
    }

    @Test fun countIsCappedAndBadgeTextStaysShort() {
        val huge = unread("""{"type":"support.chat.v1.unread","n":123456789}""")!!
        assertEquals(BusymateMessages.MAX_COUNT, huge.count)
        assertEquals("99+", huge.badgeText)
        assertEquals("99", BusymateUnread(99, null, null).badgeText)
        assertEquals("", BusymateUnread.NONE.badgeText)
    }

    @Test fun previewIsOneLineOfAtMostEightyCharacters() {
        val long = "word ".repeat(40)
        val cut = unread("""{"type":"support.chat.v1.unread","n":1,"t":"$long"}""")!!.preview!!
        assertTrue(cut.length <= 80)
        assertFalse(cut.endsWith(" "))
        assertEquals("Line one line two", BusymateMessages.preview("  Line one\n\tline\u0000 two  "))
        assertNull(BusymateMessages.preview(" \n "))
        // Whole code points only: 80 emoji stay 80 emoji, not half of one.
        val emoji = "😀".repeat(90)
        assertEquals(80, BusymateMessages.preview(emoji)!!.let { it.codePointCount(0, it.length) })
    }

    @Test fun identityNeedsAnOpaqueTokenOfTheAgreedShape() {
        assertEquals(token, (BusymateMessages.parse("""{"type":"support.chat.v1.identity","v":"$token"}""") as BusymateMessage.Identity).token)
        for (bad in listOf(
            """{"type":"support.chat.v1.identity"}""",
            """{"type":"support.chat.v1.identity","v":"short"}""",
            """{"type":"support.chat.v1.identity","v":12345678901234567890}""",
            """{"type":"support.chat.v1.identity","v":"has spaces in the token value"}""",
            """{"type":"support.chat.v1.identity","v":"<script>alert(1)</script>xx"}""",
            """{"type":"support.chat.v1.identity","v":"${"a".repeat(2049)}"}""",
            // The v1 identity ANSWER (app to frame) has no `v`: it is not this message.
            """{"type":"support.chat.v1.identity","token":"$token","nonce":"n"}""",
        )) assertNull(bad, BusymateMessages.parse(bad))
    }

    @Test fun theTokenNeverAppearsInAToString() {
        val message = BusymateMessages.parse("""{"type":"support.chat.v1.identity","v":"$token"}""")
        assertFalse(message.toString().contains(token))
    }

    @Test fun unknownTypesAndNonObjectsAreDropped() {
        for (bad in listOf(
            null, "", "null", "42", "[]", "\"support.chat.v1.unread\"", "not json", "{",
            """{"n":1}""",
            """{"type":"support.chat.v1.open"}""",
            """{"type":"support.chat.v1.identity_request"}""",
            """{"type":"busymate.ai.v1.open_url","url":"https://evil.example"}""",
            """{"type":"SUPPORT.CHAT.V1.UNREAD","n":1}""",
            """{"type":["support.chat.v1.unread"],"n":1}""",
        )) assertNull(bad, BusymateMessages.parse(bad))
    }

    @Test fun oversizedMessagesAreDroppedBeforeParsing() {
        val padding = "x".repeat(BusymateMessages.MAX_LENGTH)
        assertNull(BusymateMessages.parse("""{"type":"support.chat.v1.unread","n":1,"pad":"$padding"}"""))
    }

    @Test fun kitV2RequestsAreRecognisedSoTheyCanBeRefusedAtOnce() {
        assertEquals(BusymateMessage.V2Request("b1"), BusymateMessages.parse("""{"bm":2,"id":"b1","op":"hello"}"""))
        assertNull(BusymateMessages.parse("""{"bm":3,"id":"b1","op":"hello"}"""))
        assertNull(BusymateMessages.parse("""{"bm":2,"op":"hello"}"""))
        // A v2 envelope is never read as a follow-up, even if it also carries a type.
        assertNull(BusymateMessages.parse("""{"bm":1,"type":"support.chat.v1.unread","n":5}"""))
    }

    @Test fun onlyTheChatsOwnOriginsAreAllowed() {
        val origins = BusymateMessages.chatOrigins(BusymateConfig("acme", origins = listOf(
            "https://Support.Acme.com/", "https://chat.acme.com:8443", "http://insecure.acme.com", "https://*.acme.com",
            "https://acme.com/path", "null", "",
        )))
        assertEquals(setOf("https://busymate.ai", "https://acme.busymate.ai", "https://support.acme.com", "https://chat.acme.com:8443"), origins)
        assertFalse(origins.any { it.contains('*') })
    }

    @Test fun aMappedChatUrlAddsItsOwnOriginAndABadSlugAllowsNothing() {
        assertTrue("https://help.acme.com" in BusymateMessages.chatOrigins(BusymateConfig("acme", chatUrl = "https://help.acme.com/chat?locale=en")))
        assertEquals(emptySet<String>(), BusymateMessages.chatOrigins(BusymateConfig("Not A Slug")))
        assertEquals(emptySet<String>(), BusymateMessages.chatOrigins(BusymateConfig("*")))
    }

    @Test fun originsAreNormalisedAndEverythingElseIsRefused() {
        assertEquals("https://busymate.ai", BusymateMessages.origin("https://busymate.ai:443"))
        assertEquals("https://busymate.ai", BusymateMessages.originOfUrl("https://busymate.ai/chat/w/demos?channel=android#x"))
        assertEquals("https://busymate.ai", BusymateMessages.originOfUrl("HTTPS://BUSYMATE.AI"))
        assertNull(BusymateMessages.originOfUrl("https://user:pw@busymate.ai/chat"))
        assertNull(BusymateMessages.originOfUrl("http://busymate.ai/chat"))
        assertNull(BusymateMessages.originOfUrl("about:blank"))
        assertNull(BusymateMessages.origin("null"))
        assertNull(BusymateMessages.origin("https://busymate.ai.evil.example/path"))
    }

    @Test fun theOpenSignalIsTheAgreedObject() {
        assertEquals("""{"type":"support.chat.v1.open"}""", BusymateMessages.OPEN_MESSAGE)
    }
}

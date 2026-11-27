package ai.busymate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Badge and bubble rules: dismissing leaves the message unread; a newer message brings the bubble back. */
class BusymateUnreadStateTest {
    private val first = BusymateUnread(1, "Your order has shipped.", "2026-11-27T09:41:05Z")
    private val second = BusymateUnread(2, "Want the tracking link?", "2026-11-27T10:02:00Z")

    @Test fun aFollowUpShowsBadgeAndBubble() {
        val state = BusymateUnreadState()
        assertTrue(state.update(first))
        assertEquals(first, state.unread)
        assertEquals(first, state.teaser)
        assertFalse("the same value again is not a change", state.update(first))
    }

    @Test fun dismissHidesTheBubbleAndLeavesTheMessageUnread() {
        val state = BusymateUnreadState().apply { update(first) }
        assertTrue(state.dismiss())
        assertNull(state.teaser)
        assertEquals(1, state.unread.count)
        assertFalse(state.dismiss())
        // The server repeating the same message (app foreground) does not bring it back.
        state.update(first)
        assertNull(state.teaser)
    }

    @Test fun aNewerMessageBringsTheBubbleBack() {
        val state = BusymateUnreadState().apply { update(first); dismiss() }
        state.update(second)
        assertEquals(second, state.teaser)
    }

    @Test fun readingTheChatClearsBoth() {
        val state = BusymateUnreadState().apply { update(first); dismiss() }
        assertTrue(state.update(BusymateUnread.NONE))
        assertEquals(BusymateUnread.NONE, state.unread)
        assertNull(state.teaser)
        assertNull(state.dismissedKey)
        // The same message unread again later (a new visit) may tease again.
        state.update(first)
        assertEquals(first, state.teaser)
    }

    @Test fun aCountWithoutPreviewShowsOnlyTheBadge() {
        val state = BusymateUnreadState().apply { update(BusymateUnread(3, null, null)) }
        assertEquals(3, state.unread.count)
        assertNull(state.teaser)
    }

    @Test fun aDismissalSurvivesARestartAndStoresNoMessageText() {
        val state = BusymateUnreadState().apply { update(first); dismiss() }
        val key = state.dismissedKey!!
        assertFalse(key.contains("order"))
        assertEquals(16, key.length)
        val restarted = BusymateUnreadState(key).apply { update(first) }
        assertNull(restarted.teaser)
        assertEquals(1, restarted.unread.count)
    }
}

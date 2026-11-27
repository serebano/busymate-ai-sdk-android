package ai.busymate

import java.security.MessageDigest

/**
 * The unread count and the teaser, as one small state machine (no Android types).
 *
 * The badge follows the count. The teaser is the newest unread message's preview until the person
 * dismisses it: dismissing hides the bubble for THAT message only and leaves it unread, so the badge
 * stays; a newer message brings the bubble back; opening or reading the chat (count 0) clears both.
 */
internal class BusymateUnreadState(private var dismissed: String? = null) {
    var unread: BusymateUnread = BusymateUnread.NONE
        private set

    /** The message the bubble shows, or null. */
    val teaser: BusymateUnread?
        get() = unread.takeIf { it.count > 0 && !it.preview.isNullOrEmpty() && keyOf(it) != dismissed }

    /** The dismissed message's key (a digest, never its text), for the host's private settings. */
    val dismissedKey: String? get() = dismissed

    /** True when anything changed. */
    fun update(next: BusymateUnread): Boolean {
        val value = if (next.count <= 0) BusymateUnread.NONE else next
        val before = unread to dismissed
        unread = value
        if (value.count == 0) dismissed = null
        return before != (unread to dismissed)
    }

    /** The person closed the bubble (or opened the chat from it). True when a teaser was showing. */
    fun dismiss(): Boolean {
        val showing = teaser ?: return false
        dismissed = keyOf(showing)
        return true
    }

    companion object {
        /** Which message a teaser is: its time when the frame sent one, else its text. Stored as a digest. */
        fun keyOf(unread: BusymateUnread): String {
            val source = unread.at ?: unread.preview.orEmpty()
            val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
            return BusymateTokenStore.hex(digest.copyOf(8))
        }
    }
}

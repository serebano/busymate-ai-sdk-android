package ai.busymate

/**
 * What waits unseen for this person in the chat: how many messages from the mate (follow-ups
 * included), the first sentence of the newest one and when it was sent.
 *
 * @property count unread messages; 0 when nothing waits.
 * @property preview the first sentence of the newest message (at most 80 characters), or null.
 * @property at ISO-8601 time of the newest message, or null.
 */
data class BusymateUnread(val count: Int, val preview: String?, val at: String?) {
    /** True when a badge should show. */
    val hasUnread: Boolean get() = count > 0

    /** The badge text: the count, or "99+" above 99. Empty when nothing is unread. */
    val badgeText: String get() = when {
        count <= 0 -> ""
        count > 99 -> "99+"
        else -> count.toString()
    }

    companion object {
        /** Nothing unread. */
        @JvmField val NONE = BusymateUnread(0, null, null)
    }
}

/** For hosts that do not collect [Busymate.unread]: called on the main thread with every change. */
fun interface OnUnreadListener {
    fun onUnreadChanged(unread: BusymateUnread)
}

/** The teaser bubble's message, or null when it should hide. Called on the main thread. */
fun interface OnTeaserListener {
    fun onTeaserChanged(teaser: BusymateUnread?)
}

/**
 * @property assistant your assistant's slug on busymate.ai.
 * @property chatUrl the chat address your WebView loads. Its origin answers the unread question.
 * @property origins extra EXACT https origins that serve your chat (a mapped chat host). No wildcards.
 */
class BusymateConfig @JvmOverloads constructor(
    val assistant: String,
    val chatUrl: String = "https://busymate.ai/chat/w/$assistant",
    val origins: List<String> = emptyList(),
)

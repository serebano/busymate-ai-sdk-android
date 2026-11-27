package ai.busymate

import org.json.JSONObject

/** One validated message from the chat frame. Anything that does not parse to one of these is dropped. */
internal sealed class BusymateMessage {
    data class Unread(val value: BusymateUnread) : BusymateMessage()
    class Identity(val token: String) : BusymateMessage() {
        // The token never reaches a log line, a crash report or a debugger's toString.
        override fun toString() = "Identity(redacted)"
    }
    /** A kit v2 (`busymate.bridge/2`) request that reached this listener: answered `unsupported`. */
    data class V2Request(val id: Any) : BusymateMessage()
}

/** The wire schema of the follow-ups channel (contract "follow-ups on every surface", §2). Pure: no Android types. */
internal object BusymateMessages {
    const val TYPE_UNREAD = "support.chat.v1.unread"
    const val TYPE_IDENTITY = "support.chat.v1.identity"
    const val TYPE_OPEN = "support.chat.v1.open"
    const val MAX_LENGTH = 4096
    const val MAX_PREVIEW = 80
    const val MAX_COUNT = 9999

    private val TOKEN = Regex("^[A-Za-z0-9._~+/=:-]{16,2048}$")
    private val ISO_TIME = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(?::\\d{2}(?:\\.\\d{1,9})?)?(?:Z|[+-]\\d{2}:?\\d{2})$")
    private val ASSISTANT = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
    private val EXACT_ORIGIN = Regex("^https://[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?(?::[0-9]{1,5})?$")

    /** The text the app sends when the person opens the chat from the badge or the bubble. */
    val OPEN_MESSAGE: String = "{\"type\":\"$TYPE_OPEN\"}"

    fun parse(raw: String?): BusymateMessage? {
        if (raw == null || raw.isEmpty() || raw.length > MAX_LENGTH) return null
        val json = try { JSONObject(raw) } catch (_: Exception) { return null }
        if (json.has("bm")) {
            val id = json.opt("id")
            return if (json.opt("bm") == 2 && json.opt("op") is String && (id is String || id is Number)) BusymateMessage.V2Request(id) else null
        }
        return when (json.opt("type")) {
            TYPE_UNREAD -> unread(json)?.let { BusymateMessage.Unread(it) }
            TYPE_IDENTITY -> (json.opt("v") as? String)?.takeIf { TOKEN.matches(it) }?.let { BusymateMessage.Identity(it) }
            else -> null
        }
    }

    /** `{n, t?, at?}` from a frame message or from `POST /api/chat/unread`; null when it breaks the schema. */
    fun unread(json: JSONObject): BusymateUnread? {
        val count = when (val n = json.opt("n")) {
            is Int -> n.toLong()
            is Long -> n
            is Number -> n.toDouble().let { d -> if (d.isNaN() || d.isInfinite() || d != Math.floor(d) || d > 1e15) return null else d.toLong() }
            else -> return null
        }
        if (count < 0) return null
        val text = when (val t = json.opt("t")) {
            null, JSONObject.NULL -> null
            is String -> preview(t)
            else -> return null
        }
        val at = when (val value = json.opt("at")) {
            null, JSONObject.NULL -> null
            is String -> if (value.length <= 40 && ISO_TIME.matches(value)) value else return null
            else -> return null
        }
        if (count == 0L) return BusymateUnread.NONE
        return BusymateUnread(minOf(count, MAX_COUNT.toLong()).toInt(), text, at)
    }

    /** One line, no control characters, at most [MAX_PREVIEW] characters (whole code points); null when blank. */
    fun preview(text: String): String? {
        val line = buildString(minOf(text.length, 512)) {
            var space = false
            for (ch in text) {
                if (length >= 512) break
                if (ch.isWhitespace() || ch.isISOControl()) { space = isNotEmpty(); continue }
                if (space) append(' ')
                space = false
                append(ch)
            }
        }
        if (line.isEmpty()) return null
        if (line.codePointCount(0, line.length) <= MAX_PREVIEW) return line
        return line.substring(0, line.offsetByCodePoints(0, MAX_PREVIEW)).trimEnd()
    }

    fun isAssistant(slug: String): Boolean = ASSISTANT.matches(slug)

    /** "https://host[:port]", lower-case, default port dropped; null for anything else (wildcards, paths, http, "null"). */
    fun origin(value: String?): String? {
        val origin = value?.trim()?.lowercase()?.removeSuffix("/")?.removeSuffix(":443") ?: return null
        return if (EXACT_ORIGIN.matches(origin)) origin else null
    }

    /** The origin of an https URL, or null. */
    fun originOfUrl(url: String?): String? {
        val value = url?.trim() ?: return null
        if (!value.startsWith("https://", ignoreCase = true)) return null
        val next = value.indexOfAny(charArrayOf('/', '?', '#'), 8)
        val end = if (next < 0) value.length else next
        val authority = value.substring(8, end)
        if (authority.contains('@')) return null
        return origin("https://$authority")
    }

    /**
     * The ONLY origins the bridge listens to: busymate.ai (where `/chat/w/<assistant>` lives), the
     * assistant's own host, the origin of the configured chat URL and the host's exact extra origins.
     */
    fun chatOrigins(config: BusymateConfig): Set<String> {
        if (!isAssistant(config.assistant)) return emptySet()
        val out = linkedSetOf("https://busymate.ai", "https://${config.assistant}.busymate.ai")
        originOfUrl(config.chatUrl)?.let { out += it }
        config.origins.mapNotNullTo(out) { origin(it) }
        return out
    }
}

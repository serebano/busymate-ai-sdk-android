package ai.busymate

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** `POST <chat origin>/api/chat/unread` with `{a, v}`: the badge's one question, asked without a WebView. */
internal object BusymateUnreadClient {
    sealed class Answer {
        /** 200 with a valid body, or 204 (nothing unread). */
        data class Known(val unread: BusymateUnread) : Answer()
        /** Anything else: keep what is shown. */
        object Unchanged : Answer()
    }

    private const val MAX_BODY = 4096

    fun body(assistant: String, token: String): String = JSONObject().put("a", assistant).put("v", token).toString()

    /** Pure reading of the server's answer. */
    fun interpret(status: Int, body: String?): Answer = when (status) {
        204 -> Answer.Known(BusymateUnread.NONE)
        200 -> {
            val json = body?.takeIf { it.length <= MAX_BODY }?.let { try { JSONObject(it) } catch (_: Exception) { null } }
            json?.let { BusymateMessages.unread(it) }?.let { Answer.Known(it) } ?: Answer.Unchanged
        }
        else -> Answer.Unchanged
    }

    /** Blocking; call off the main thread. No cookies, no redirects, no cache, never throws. */
    fun ask(origin: String, assistant: String, token: String, sdkVersion: String): Answer {
        if (BusymateMessages.origin(origin) == null) return Answer.Unchanged
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("$origin/api/chat/unread").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = false
                useCaches = false
                doOutput = true
                setRequestProperty("Content-Type", "text/plain")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Busymate-SDK", "android/$sdkVersion")
            }
            connection.outputStream.use { it.write(body(assistant, token).toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val text = if (status == 200) connection.inputStream.use { stream ->
                val buffer = ByteArray(MAX_BODY + 1)
                var size = 0
                while (size < buffer.size) {
                    val read = stream.read(buffer, size, buffer.size - size)
                    if (read < 0) break
                    size += read
                }
                String(buffer, 0, size, Charsets.UTF_8)
            } else null
            interpret(status, text)
        } catch (_: Exception) {
            Answer.Unchanged
        } finally {
            connection?.disconnect()
        }
    }
}

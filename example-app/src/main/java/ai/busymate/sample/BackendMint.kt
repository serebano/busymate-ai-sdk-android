package ai.busymate.sample

import ai.busymate.bridge.BusymateBridge
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object BackendMint {
    internal fun connection(settings: DemoSettings): HttpURLConnection =
        (URL(settings.backendUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = "POST"; connectTimeout = 10000; readTimeout = 10000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (settings.backendBearer.isNotEmpty()) setRequestProperty("Authorization", "Bearer ${settings.backendBearer}")
        }

    internal fun reply(token: String, nonce: String, expectedNonce: Any?): BusymateBridge.MintResult.Token {
        check(token.isNotEmpty() && nonce == expectedNonce)
        return BusymateBridge.MintResult.Token(token, nonce)
    }
    fun mint(settings: DemoSettings, request: Map<String, Any?>, done: (BusymateBridge.MintResult) -> Unit) {
        if (!DemoSession.signedIn || settings.accountId.isEmpty()) { done(BusymateBridge.MintResult.NotSignedIn); return }
        if (settings.backendUrl.isEmpty()) { done(BusymateBridge.MintResult.Failed); return }
        Thread {
            var connection: HttpURLConnection? = null
            val result = runCatching {
                connection = connection(settings)
                val c = connection!!
                c.outputStream.use { it.write(JSONObject(request).toString().toByteArray(Charsets.UTF_8)) }
                if (c.responseCode == 401) return@runCatching BusymateBridge.MintResult.NotSignedIn
                check(c.responseCode in 200..299)
                val reply = c.inputStream.use { JSONObject(it.bufferedReader().readText()) }
                val token = reply.getString("token"); val nonce = reply.getString("nonce")
                reply(token, nonce, request["nonce"])
            }.getOrDefault(BusymateBridge.MintResult.Failed)
            connection?.disconnect()
            done(result)
        }.start()
    }
}

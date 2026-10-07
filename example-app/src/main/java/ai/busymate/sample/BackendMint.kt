package ai.busymate.sample

import ai.busymate.bridge.BusymateBridge
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object BackendMint {
    fun mint(settings: DemoSettings, request: Map<String, Any?>, done: (BusymateBridge.MintResult) -> Unit) {
        if (!DemoSession.signedIn || settings.accountId.isEmpty()) { done(BusymateBridge.MintResult.NotSignedIn); return }
        if (settings.backendUrl.isEmpty()) { done(BusymateBridge.MintResult.Failed); return }
        Thread {
            var connection: HttpURLConnection? = null
            val result = runCatching {
                connection = URL(settings.backendUrl).openConnection() as HttpURLConnection
                val c = connection!!
                c.requestMethod = "POST"; c.connectTimeout = 10000; c.readTimeout = 10000; c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                if (settings.backendBearer.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer ${settings.backendBearer}")
                c.outputStream.use { it.write(JSONObject(request).toString().toByteArray(Charsets.UTF_8)) }
                if (c.responseCode == 401) return@runCatching BusymateBridge.MintResult.NotSignedIn
                check(c.responseCode in 200..299)
                val reply = c.inputStream.use { JSONObject(it.bufferedReader().readText()) }
                val token = reply.getString("token"); val nonce = reply.getString("nonce")
                check(token.isNotEmpty() && nonce == request["nonce"])
                BusymateBridge.MintResult.Token(token, nonce)
            }.getOrDefault(BusymateBridge.MintResult.Failed)
            connection?.disconnect()
            done(result)
        }.start()
    }
}

// Busymate AI — kit v2 native bridge for Android (`busymate.bridge/2`, build 2.0.0).
//
// FROZEN. This file is a pipe, not a policy: it moves one JSON envelope between
// the Busymate chat (served from busymate.ai) and five fixed operations of YOUR
// app. Every message name, timeout, retry, fallback and decision lives in the
// chat, which Busymate updates on its side — so a fix never needs a new build
// of your app. Do not edit it; a new version is a new immutable URL
// (https://busymate.ai/sdk/v2/<version>/), announced in advance.
//
// Contract: https://busymate.ai/sdk/v2/2.0.0/CONTRACT.md
//
// Requires: androidx.webkit:webkit >= 1.8, androidx.lifecycle:lifecycle-process.
// Store policy: no new permissions, no downloaded native code, and the web page
// reaches only your own mint, an https open, and close — never a general native
// API (Google Play "Device and Network Abuse"; Apple 4.7.2 for the iOS twin).
//
// Who may ask: EXACTLY https://<assistant>.busymate.ai plus the exact https
// origins you list (your mapped support host). No wildcards: another tenant's
// chat, or any other busymate.ai page, is refused and never reaches your mint.
//
// Use (before the first loadUrl):
//
//   BusymateBridge.install(webView, BusymateBridge.Config(
//       assistant = "acme",
//       origins = listOf("https://support.acme.com"),       // your mapped support host, if any
//       account = { session.userIdOrNull },                  // null = signed out
//       mint = { request, done -> backend.mintBusymate(request["nonce"] as String, done) },
//   ))
//   // `request` also carries "assistant" and "origin", set by this file (never by
//   // the page), so your backend may bind the token to them.
//   // in your WebViewClient:
//   override fun onRenderProcessGone(v: WebView, d: RenderProcessGoneDetail) = BusymateBridge.onRenderProcessGone(v, d)
//   // optional, only makes sign-in / sign-out show up faster:
//   BusymateBridge.accountChanged()

package ai.busymate.bridge

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.security.MessageDigest

object BusymateBridge {
    const val NAME = "BusymateBridge"
    const val VERSION = 2
    const val BUILD = "2.0.0"
    private const val MAX_ENVELOPE = 64 * 1024
    private const val MINT_CEILING_MS = 15_000L
    private val ASSISTANT = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
    private val EXACT_ORIGIN = Regex("^https://[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?(?::[0-9]{1,5})?$")

    /** The exact origins allowed to ask: the assistant's own busymate.ai host + your exact https origins. */
    @JvmStatic
    fun allowedOrigins(config: Config): List<String> {
        val own = if (ASSISTANT.matches(config.assistant)) listOf("https://${config.assistant}.busymate.ai") else emptyList()
        val extra = config.origins.map { it.trim().lowercase().removeSuffix("/").removeSuffix(":443") }.filter { EXACT_ORIGIN.matches(it) }
        return (own + extra).distinct()
    }

    /** What your mint callback answers with. */
    sealed class MintResult {
        data class Token(val token: String, val nonce: String) : MintResult()
        object NotSignedIn : MintResult()
        object Failed : MintResult()
    }

    class Config(
        /** Your assistant's slug on busymate.ai; https://<assistant>.busymate.ai may ask. */
        val assistant: String,
        /** Extra EXACT https origins that load the chat (your mapped support host). Wildcards are ignored. */
        val origins: List<String> = emptyList(),
        /** The signed-in account id, or null when signed out. Never leaves the device raw. */
        val account: () -> String?,
        /** Ask YOUR backend for a Busymate launch token: the chat's request, plus "assistant" and "origin" set by this file. */
        val mint: (request: Map<String, Any?>, done: (MintResult) -> Unit) -> Unit,
        /** Optional: host actions the chat may ask for; return true when handled. */
        val onAction: ((name: String, params: Map<String, Any?>) -> Boolean)? = null,
        /** Optional: the chat asked to be closed. */
        val onClose: (() -> Unit)? = null,
        /** Optional: the SDK replaced a WebView whose renderer died; keep this new one. */
        val onReplaced: ((WebView) -> Unit)? = null,
    )

    private class Installed(val config: Config, val transport: String, val origins: List<String>) {
        var lastReply: JavaScriptReplyProxy? = null
        var pendingRestored = false
        var lastUrl: String? = null
    }

    private val main = Handler(Looper.getMainLooper())
    private val installs = mutableMapOf<Int, Pair<WeakReference<WebView>, Installed>>()
    private var lifecycleObserved = false

    /** Install on a WebView BEFORE its first load. Safe to call once per WebView. */
    @JvmStatic
    fun install(webView: WebView, config: Config) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        val origins = allowedOrigins(config)
        // Nothing valid to allow: stay inert (the chat runs as a guest) rather than guess.
        if (origins.isEmpty()) return
        val installed: Installed
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            installed = Installed(config, "message", origins)
            WebViewCompat.addWebMessageListener(webView, NAME, origins.toSet()) { view, message, sourceOrigin, _, reply ->
                receive(view, installed, message.data, sourceOrigin.toString(), reply)
            }
        } else {
            // WebView older than 82: no per-frame origin, so the TOP page's origin
            // is checked and every reply goes to the top page only. The chat is
            // told (`transport: "jsi"`) and may decline to mint on this class.
            installed = Installed(config, "jsi", origins)
            webView.addJavascriptInterface(JsiEntry(WeakReference(webView), installed), NAME)
        }
        installs[System.identityHashCode(webView)] = WeakReference(webView) to installed
        observeLifecycle()
    }

    /** Call from your WebViewClient's onRenderProcessGone. Replaces the dead WebView; never crashes the app. */
    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.O)
    fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        val entry = installs.remove(System.identityHashCode(view)) ?: return false
        val installed = entry.second
        val parent = view.parent as? ViewGroup ?: return true
        val index = parent.indexOfChild(view)
        val params = view.layoutParams
        val client = WebViewCompat.getWebViewClient(view)
        val chrome = WebViewCompat.getWebChromeClient(view)
        val url = view.url ?: installed.lastUrl
        parent.removeView(view)
        view.destroy()
        val fresh = WebView(parent.context)
        fresh.webViewClient = client
        if (chrome != null) fresh.webChromeClient = chrome
        parent.addView(fresh, index, params)
        install(fresh, installed.config)
        installs[System.identityHashCode(fresh)]?.second?.pendingRestored = true
        installed.config.onReplaced?.invoke(fresh)
        if (url != null) fresh.loadUrl(url)
        return true
    }

    /** Optional: tell the chat the signed-in account changed (sign-in, sign-out, switch). */
    @JvmStatic
    fun accountChanged() {
        main.post { forEachInstall { view, installed -> emit(view, installed, "state", stateOf(view, installed)) } }
    }

    // ── the pipe ────────────────────────────────────────────────────────────

    private class JsiEntry(val view: WeakReference<WebView>, val installed: Installed) {
        @JavascriptInterface
        fun postMessage(data: String) {
            main.post {
                val webView = view.get() ?: return@post
                receive(webView, installed, data, originOf(webView.url), null)
            }
        }
    }

    private fun receive(view: WebView, installed: Installed, data: String?, sourceOrigin: String?, reply: JavaScriptReplyProxy?) {
        if (data == null || data.length > MAX_ENVELOPE) return
        val envelope = try { JSONObject(data) } catch (_: Exception) { return }
        if (envelope.optInt("bm") != VERSION) return
        val id = envelope.opt("id") ?: return
        val respond: (JSONObject) -> Unit = { body ->
            body.put("bm", VERSION).put("id", id)
            send(view, reply, body.toString())
        }
        val origin = normalizedOrigin(sourceOrigin)
        if (origin == null || origin !in installed.origins) {
            respond(JSONObject().put("ok", false).put("e", "denied"))
            return
        }
        installed.lastUrl = view.url
        val op = envelope.optString("op")
        val p = envelope.optJSONObject("p") ?: JSONObject()
        when (op) {
            "hello" -> {
                installed.lastReply = reply
                respond(JSONObject().put("ok", true).put("r", helloOf(view, installed, origin)))
                if (installed.pendingRestored) {
                    installed.pendingRestored = false
                    emit(view, installed, "restored", JSONObject())
                }
            }
            "state" -> respond(JSONObject().put("ok", true).put("r", stateOf(view, installed)))
            "mint" -> mint(installed, p, origin, respond)
            "open" -> respond(open(view, p))
            "action" -> respond(action(installed, p))
            else -> respond(JSONObject().put("ok", false).put("e", "unsupported"))
        }
    }

    private fun mint(installed: Installed, p: JSONObject, origin: String, respond: (JSONObject) -> Unit) {
        if (installed.config.account() == null) {
            respond(JSONObject().put("ok", false).put("e", "not_signed_in"))
            return
        }
        var done = false
        val finish: (JSONObject) -> Unit = { body -> main.post { if (!done) { done = true; respond(body) } } }
        // The ceiling only reclaims the callback; the chat owns every real budget.
        main.postDelayed({ finish(JSONObject().put("ok", false).put("e", "timeout")) }, MINT_CEILING_MS)
        try {
            // Who asked is stated by this file, never by the page.
            val request = toMap(p) + mapOf("assistant" to installed.config.assistant, "origin" to origin)
            installed.config.mint(request) { result ->
                finish(when (result) {
                    is MintResult.Token -> JSONObject().put("ok", true)
                        .put("r", JSONObject().put("token", result.token).put("nonce", result.nonce))
                    is MintResult.NotSignedIn -> JSONObject().put("ok", false).put("e", "not_signed_in")
                    is MintResult.Failed -> JSONObject().put("ok", false).put("e", "mint_failed")
                })
            }
        } catch (_: Exception) {
            finish(JSONObject().put("ok", false).put("e", "mint_failed"))
        }
    }

    private fun open(view: WebView, p: JSONObject): JSONObject {
        val url = p.optString("url")
        val uri = try { Uri.parse(url) } catch (_: Exception) { null }
        if (uri == null || uri.scheme != "https" || uri.host.isNullOrEmpty()) {
            return JSONObject().put("ok", false).put("e", "bad_request")
        }
        return try {
            view.context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            JSONObject().put("ok", true).put("r", JSONObject().put("opened", true))
        } catch (_: ActivityNotFoundException) {
            JSONObject().put("ok", false).put("e", "unsupported")
        }
    }

    private fun action(installed: Installed, p: JSONObject): JSONObject {
        val name = p.optString("name")
        if (name.isEmpty() || name.length > 64) return JSONObject().put("ok", false).put("e", "bad_request")
        val params = toMap(p.optJSONObject("params") ?: JSONObject())
        val handled = try {
            installed.config.onAction?.invoke(name, params) == true ||
                (name == "close" && installed.config.onClose != null).also { if (it) installed.config.onClose?.invoke() }
        } catch (_: Exception) { false }
        return JSONObject().put("ok", true).put("r", JSONObject().put("handled", handled))
    }

    private fun helloOf(view: WebView, installed: Installed, origin: String): JSONObject {
        val context = view.context
        val info = try { context.packageManager.getPackageInfo(context.packageName, 0) } catch (_: Exception) { null }
        val webview = try { WebViewCompat.getCurrentWebViewPackage(context)?.versionName } catch (_: Exception) { null }
        @Suppress("DEPRECATION")
        val build = info?.let { if (Build.VERSION.SDK_INT >= 28) it.longVersionCode.toString() else it.versionCode.toString() }
        return JSONObject()
            .put("bridge", "busymate").put("v", VERSION).put("build", BUILD)
            .put("platform", "android").put("transport", installed.transport)
            .put("assistant", installed.config.assistant).put("origin", origin)
            .put("origins", JSONArray(installed.origins))
            .put("caps", JSONArray(listOf("hello", "state", "mint", "open", "action", "ev:resume", "ev:state", "ev:restored")))
            .put("webview", webview ?: JSONObject.NULL)
            .put("app", JSONObject().put("id", context.packageName).put("version", info?.versionName ?: JSONObject.NULL).put("build", build ?: JSONObject.NULL))
    }

    private fun stateOf(view: WebView, installed: Installed): JSONObject {
        val account = try { installed.config.account() } catch (_: Exception) { null }
        return JSONObject()
            .put("signedIn", account != null)
            .put("account", account?.let { accountKey(view.context.packageName, it) } ?: JSONObject.NULL)
    }

    /** SHA-256(appId + ":" + account), first 128 bits, hex. The raw id never crosses. */
    private fun accountKey(appId: String, account: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$appId:$account".toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    private fun emit(view: WebView, installed: Installed, ev: String, r: JSONObject) {
        val body = JSONObject().put("bm", VERSION).put("ev", ev).put("r", r).toString()
        send(view, installed.lastReply, body, installed.transport == "jsi")
    }

    private fun send(view: WebView, reply: JavaScriptReplyProxy?, body: String, forceWindow: Boolean = false) {
        main.post {
            if (reply != null && !forceWindow) {
                try { reply.postMessage(body); return@post } catch (_: Exception) { /* frame gone */ }
            }
            view.evaluateJavascript(
                "window.dispatchEvent(new CustomEvent('busymate-bridge',{detail:" + JSONObject.quote(body) + "}))",
                null,
            )
        }
    }

    private fun observeLifecycle() {
        if (lifecycleObserved) return
        lifecycleObserved = true
        main.post {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    forEachInstall { view, installed -> emit(view, installed, "resume", JSONObject()) }
                }
            })
        }
    }

    private fun forEachInstall(block: (WebView, Installed) -> Unit) {
        val dead = mutableListOf<Int>()
        for ((key, entry) in installs) {
            val view = entry.first.get()
            if (view == null) dead += key else block(view, entry.second)
        }
        dead.forEach { installs.remove(it) }
    }

    /** "https://host[:port]", lower-case, default port dropped; null for anything else (an opaque "null" origin included). */
    private fun normalizedOrigin(origin: String?): String? {
        val value = origin?.trim()?.lowercase()?.removeSuffix("/")?.removeSuffix(":443") ?: return null
        return if (EXACT_ORIGIN.matches(value)) value else null
    }

    private fun originOf(url: String?): String? = try {
        val uri = Uri.parse(url ?: return null)
        if (uri.scheme == null || uri.host == null) null
        else uri.scheme + "://" + uri.host + (if (uri.port != -1) ":" + uri.port else "")
    } catch (_: Exception) { null }

    private fun toMap(json: JSONObject): Map<String, Any?> {
        val out = mutableMapOf<String, Any?>()
        for (key in json.keys()) out[key] = json.opt(key).let { if (it == JSONObject.NULL) null else it }
        return out
    }
}

package ai.busymate.whitelabel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

data class BusymateAIIdentity(val token: String, val nonce: String)

fun interface BusymateAIIdentityProvider {
    /**
     * Mint through the customer's authenticated backend; never keep a signing key in the app.
     *
     * CALLED ONCE PER ASK. A launch assertion is SINGLE-USE, so this must
     * perform a real round trip every time and must NEVER return a cached pair:
     * a replayed token is refused by `support-launch`, and the visitor lands
     * anonymous with nothing in the app reporting a failure.
     */
    fun mint(callback: (Result<BusymateAIIdentity?>) -> Unit)
}

class BusymateAIWebViewBridge(
    private val context: Context,
    private val webView: WebView,
    private val identityProvider: BusymateAIIdentityProvider,
    // audit docs-and-developer-path-07 (2026-09-18): the frame's own ✕ posts
    // busymate.ai.v1.close — the web widget's own listener already reacts to
    // it (widget-page-api.md "Chat to page"); this bridge silently dropped it
    // (no matching `when` branch), so a host app hosting the full-page
    // experience (mobile-in-app-support.md) had no signal to dismiss itself.
    private val onClose: (() -> Unit)? = null,
    /**
     * #3540 — THE ORIGIN ALLOW-LIST. A `@JavascriptInterface` is reachable by
     * EVERY document the WebView loads, including one a redirect or an injected
     * frame navigated to. An empty set keeps the historical behaviour (answer
     * whatever is loaded) so nothing that already ships changes; a non-empty
     * set refuses to mint for any other origin. Pass your assistant's origin.
     */
    private val allowedOrigins: Set<String> = emptySet(),
) {
    /**
     * Every message from the page. Runs on a WebView JS thread, so the whole
     * body hops to the WebView's own thread before touching `webView.url` or
     * evaluating anything — reading either off-thread is undefined behaviour.
     */
    @JavascriptInterface
    fun postMessage(raw: String) {
        val payload = runCatching { JSONObject(raw) }.getOrNull() ?: return
        webView.post { handle(payload) }
    }

    private fun handle(payload: JSONObject) {
        when (val type = payload.optString("type")) {
            "busymate.ai.v1.identity_request", "support.chat.v1.identity_request" -> {
                val responseType = if (type == "busymate.ai.v1.identity_request") {
                    "busymate.ai.v1.identity"
                } else {
                    "support.chat.v1.identity"
                }
                if (!originAllowed()) {
                    // A refusal the frame can SEE. Silence is indistinguishable
                    // from an app with no bridge at all, which is exactly how
                    // the native identity hole stayed invisible for months.
                    postUnavailable("unsupported")
                    return
                }
                identityProvider.mint { result ->
                    val identity = result.getOrNull()
                    webView.post {
                        if (identity == null) {
                            postUnavailable(if (result.isFailure) "mint_failed" else "not_signed_in")
                            return@post
                        }
                        postToPage(
                            JSONObject()
                                .put("type", responseType)
                                .put("token", identity.token)
                                .put("nonce", identity.nonce),
                        )
                    }
                }
            }
            "busymate.ai.v1.open_url", "busymate.ai.v1.auth_request",
            "support.chat.v1.open_url", "support.chat.v1.auth_request" -> {
                val url = runCatching { Uri.parse(payload.optString("url")) }.getOrNull() ?: return
                if (url.scheme != "https" && url.scheme != "http") return
                context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            "busymate.ai.v1.close", "support.chat.v1.close" -> onClose?.invoke()
        }
    }

    /** The origin currently loaded, or null when the WebView has no document yet. */
    private fun currentOrigin(): String? {
        val url = runCatching { Uri.parse(webView.url ?: return null) }.getOrNull() ?: return null
        val scheme = url.scheme ?: return null
        val host = url.host ?: return null
        return if (url.port >= 0) "$scheme://$host:${url.port}" else "$scheme://$host"
    }

    private fun originAllowed(): Boolean =
        allowedOrigins.isEmpty() || allowedOrigins.contains(currentOrigin())

    /**
     * #3540 — POST AN OBJECT, NOT A JSON STRING.
     *
     * This used to evaluate `window.postMessage(<quoted json text>, '*')`, which
     * delivers a STRING to the page. Every frame-side reader looks at
     * `event.data.type`, and a string has no `.type`, so the app's perfectly
     * good answer was dropped on arrival and the customer launched anonymous —
     * silently, with the app believing it had answered. `JSON.parse` on the page
     * side restores the object the wire has always been specified to carry.
     */
    private fun postToPage(message: JSONObject) {
        webView.evaluateJavascript(
            "window.postMessage(JSON.parse(${JSONObject.quote(message.toString())}), '*')",
            null,
        )
    }

    private fun postUnavailable(reason: String) {
        postToPage(JSONObject().put("type", "busymate.identity.v1.unavailable").put("v", 1).put("reason", reason))
    }

    /**
     * Call this the moment your user signs in, signs out, or switches account.
     *
     * #3536: the frame itself now installs `window.BusymateAI`, so this reaches
     * a real implementation whether the WebView loads the hosted experience
     * DIRECTLY (the usual shape for an in-app assistant) or a page of yours
     * that embeds the widget through `embed/v1.js`. Nothing in this file
     * changed and nothing in your app has to: it used to be a silent no-op on
     * the direct-load shape, and it is not any more. The chat upgrades in
     * place — same conversation, no reload.
     */
    fun refreshIdentity() {
        webView.post {
            webView.evaluateJavascript("window.BusymateAI?.refreshIdentity?.()", null)
        }
    }

    /**
     * #3540 — the LOGIN / TOKEN-ROTATION / ACCOUNT-SWITCH signal, by the name
     * the contract uses. It deliberately does NOT push a token: it makes the
     * frame ASK, and the ask is answered by `mint` with a fresh pair. One
     * question, one mint — pushing an identity here as well would burn a
     * single-use assertion nobody spent.
     */
    fun identityChanged() = refreshIdentity()

    /**
     * #3540/#3538 — YOUR USER SIGNED OUT. Sends `busymate.identity.v1.revoked`,
     * which drops the verified identity, ends the session server-side and
     * clears the previous person's transcript before the next one can read it.
     * Without this call a signed-out customer keeps a verified chat until the
     * WebView is destroyed, which is a privacy defect and not a UX one.
     *
     * `reason` is one of `signed_out` (default), `switched`, `expired`.
     */
    @JvmOverloads
    fun signedOut(reason: String = "signed_out") {
        webView.post {
            signalPage("signedOut", JSONObject().put("type", "busymate.identity.v1.revoked").put("v", 1).put("reason", reason))
        }
    }

    /**
     * #3542 — A HOST-PAGE SIGNAL, NOT A BARE WINDOW POST.
     *
     * `signedOut()` used to only `window.postMessage` the revocation into the
     * loaded document. That reaches the frame when the WebView loads the
     * assistant DIRECTLY, and reaches NOBODY on the far more common tenant
     * shape — a page of yours that mounts the widget through `embed/v1.js` —
     * because there the listener lives inside the iframe and the loader
     * forwards nothing it did not send itself. Every one of our white-label
     * tenants hosts that shape, so a sign-out delivered this way was a
     * privacy defect wearing the costume of a working call.
     *
     * `window.BusymateAI.signedOut()` exists on BOTH shapes — the embed
     * loader installs it on the host page, and since #3536 the frame installs
     * the same façade on a direct load — so the API call is tried first and
     * the window post remains only as the fallback for a frame older than
     * either. `method` is a literal from this file, never page input.
     */
    private fun signalPage(method: String, message: JSONObject) {
        webView.evaluateJavascript(
            "(function(m){var a=window.BusymateAI||window.SupportChat;" +
                "if(a&&typeof a.$method==='function'){a.$method(m.reason);return}" +
                "window.postMessage(m,'*')})(JSON.parse(${JSONObject.quote(message.toString())}))",
            null,
        )
    }

    /**
     * #3540 — call from your Activity/Fragment `onResume()`. The frame already
     * re-asks on `visibilitychange`/`pageshow`, so this is belt-and-braces for
     * the shells where a WebView is restored without firing either; an
     * unchanged answer is a no-op at the frame's listener, so it is safe to
     * call on every resume.
     */
    fun onResume() = refreshIdentity()

    companion object {
        /** Primary handler for every Busymate AI use case. */
        const val HANDLER_NAME = "BusymateAINative"
        /** Compatibility handler for apps shipped before the generic API name. */
        const val SUPPORT_HANDLER_NAME = "SupportChatNative"

        /**
         * Register the bridge BEFORE `loadUrl`. A handler added afterwards can
         * miss the first ask; the frame does run a bounded late-bridge watch
         * (#3536), but a bridge that was there from the first byte never needs
         * it.
         *
         * WHAT THIS TOUCHES: `javaScriptEnabled` only. It deliberately sets NO
         * cookie policy — identity arrives from your app on every launch, so a
         * correct integration needs zero cookie settings and
         * `setAcceptThirdPartyCookies` in particular is neither required nor
         * recommended.
         */
        @JvmStatic
        @JvmOverloads
        fun install(
            context: Context,
            webView: WebView,
            identityProvider: BusymateAIIdentityProvider,
            onClose: (() -> Unit)? = null,
            allowedOrigins: Set<String> = emptySet(),
        ): BusymateAIWebViewBridge {
            val bridge = BusymateAIWebViewBridge(context, webView, identityProvider, onClose, allowedOrigins)
            webView.settings.javaScriptEnabled = true
            webView.addJavascriptInterface(bridge, HANDLER_NAME)
            webView.addJavascriptInterface(bridge, SUPPORT_HANDLER_NAME)
            return bridge
        }
    }
}

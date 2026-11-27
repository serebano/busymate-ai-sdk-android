package ai.busymate

import ai.busymate.whitelabel.BusymateLaunch
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.webkit.WebView
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Follow-ups in YOUR app: the channel between the Busymate chat WebView and the app around it.
 *
 * When the mate writes while the chat is closed (a follow-up, a late answer), the chat tells the app
 * and the app shows it on its own screens: a count badge on the chat button and a small message
 * bubble. It works with the WebView closed too: on start and every time the app comes to the
 * foreground the SDK asks the server what is unread.
 *
 *     // Application.onCreate (or before the first screen):
 *     Busymate.init(context, BusymateConfig(assistant = "acme"))
 *
 *     // Where you create the chat WebView, before loadUrl (after BusymateBridge.install if you use it):
 *     Busymate.attach(webView)
 *
 *     // On your main screen: BusymateLauncherButton + BusymateTeaserBubble (Compose or View),
 *     // or your own UI over Busymate.unread / OnUnreadListener. Call Busymate.openChat() when the
 *     // person opens the chat from your own badge.
 *
 * Security: messages are accepted only from the chat's own origins (`https://busymate.ai`,
 * `https://<assistant>.busymate.ai`, the origin of your chat URL and the exact origins you list),
 * through `WebViewCompat.addWebMessageListener` (no `addJavascriptInterface`), schema-checked, and
 * unknown types are dropped. The visitor token is encrypted with an Android Keystore key and never logged.
 */
object Busymate {
    /** The follow-ups channel's JS object, always registered on an attached WebView. */
    const val EVENTS_NAME = "BusymateEvents"

    /**
     * The same channel under the contract's name. Registered when the name is free; a WebView whose
     * host installed the kit v2 identity pipe already has a `BusymateBridge`, which keeps the name.
     */
    const val BRIDGE_NAME = "BusymateBridge"

    private const val OPEN_WINDOW_MS = 30_000L
    private const val MIN_REFRESH_GAP_MS = 1_500L
    private const val CLOSED_REFRESH_DELAY_MS = 800L
    private const val DISMISSED_KEY = "bm_teaser_dismissed:"

    private val main = Handler(Looper.getMainLooper())
    private val io: Executor by lazy {
        Executors.newSingleThreadExecutor { task -> Thread(task, "busymate-unread").apply { isDaemon = true } }
    }

    private val unreadFlow = MutableStateFlow(BusymateUnread.NONE)
    private val teaserFlow = MutableStateFlow<BusymateUnread?>(null)
    private val listeners = CopyOnWriteArraySet<OnUnreadListener>()
    private val teaserListeners = CopyOnWriteArraySet<OnTeaserListener>()

    private class Session(val context: Context, val config: BusymateConfig, val origins: Set<String>, val apiOrigin: String) {
        val tokens: BusymateTokenStore by lazy { BusymateTokenStore.create(context) }
        val state = BusymateUnreadState(
            try { context.getSharedPreferences(BusymateTokenStore.PREFS, Context.MODE_PRIVATE).getString(DISMISSED_KEY + config.assistant, null) } catch (_: Exception) { null },
        )
    }

    private class Attached(val ownsBridgeName: Boolean) : View.OnAttachStateChangeListener {
        var reply: JavaScriptReplyProxy? = null
        var openSent = 0
        override fun onViewAttachedToWindow(view: View) {
            // A preloaded chat that is shown only now: deliver the open it was waiting for.
            if (view is WebView && reply != null) sendOpen(view, this)
        }
        override fun onViewDetachedFromWindow(view: View) {
            // The chat screen closed: what it showed is now seen, so ask the server for the truth.
            main.postDelayed({ refreshUnread() }, CLOSED_REFRESH_DELAY_MS)
        }
    }

    @Volatile private var session: Session? = null
    private val attached = WeakHashMap<WebView, Attached>()
    private var lifecycleObserved = false
    private var refreshing = false
    private var refreshedAt = 0L
    private var openUntil = 0L
    private var openRequest = 0

    /** How many mate messages wait unseen, with the newest one's preview. Starts at [BusymateUnread.NONE]. */
    @JvmStatic
    val unread: StateFlow<BusymateUnread> = unreadFlow.asStateFlow()

    /**
     * The message the teaser bubble shows, or null: the newest unread preview until the person
     * dismisses it. Dismissing leaves the message unread, so [unread] keeps its count.
     */
    @JvmStatic
    val teaser: StateFlow<BusymateUnread?> = teaserFlow.asStateFlow()

    /** True once [init] ran with a valid assistant. */
    @JvmStatic
    val isReady: Boolean get() = session != null

    /**
     * Call once, early (Application.onCreate is best). Asks the server what is unread right away and
     * again each time the app comes to the foreground. Calling it again with another assistant
     * switches to it and forgets the first one's state (not its stored token).
     *
     * An invalid assistant slug leaves the SDK inert.
     */
    @JvmStatic
    fun init(context: Context, config: BusymateConfig) {
        val origins = BusymateMessages.chatOrigins(config)
        if (origins.isEmpty()) return
        val apiOrigin = BusymateMessages.originOfUrl(config.chatUrl) ?: "https://busymate.ai"
        val next = Session(context.applicationContext, config, origins, apiOrigin)
        onMain {
            val before = session
            if (before == null || before.config.assistant != config.assistant || before.origins != origins || before.apiOrigin != apiOrigin) {
                session = next
                publish(next)
            }
            if (!lifecycleObserved) {
                lifecycleObserved = true
                // Fires at once when the app is already in the foreground, then on every return to it.
                ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) = refreshUnread()
                })
            }
            refreshUnread()
        }
    }

    /**
     * Connect a chat WebView. Call on the main thread before `loadUrl`, and AFTER
     * `BusymateBridge.install` when your app uses the kit v2 identity pipe (both want the JS name
     * `BusymateBridge`; the identity pipe keeps it and this channel uses [EVENTS_NAME]).
     *
     * Returns false when [init] has not run or the device's WebView is too old for
     * `WEB_MESSAGE_LISTENER` (WebView 82-). There is deliberately no `addJavascriptInterface`
     * fallback: the badge then relies on [refreshUnread] alone.
     */
    @JvmStatic
    @MainThread
    @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
    fun attach(webView: WebView): Boolean {
        val current = session ?: return false
        if (attached.containsKey(webView)) return true
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) return false
        webView.settings.javaScriptEnabled = true
        var entry: Attached? = null
        try {
            WebViewCompat.addWebMessageListener(webView, EVENTS_NAME, current.origins) { view, message, sourceOrigin, _, reply ->
                entry?.let { receive(view, it, message.data, sourceOrigin, reply, answerV2 = false) }
            }
        } catch (_: IllegalArgumentException) {
            return false
        }
        val ownsBridgeName = try {
            WebViewCompat.addWebMessageListener(webView, BRIDGE_NAME, current.origins) { view, message, sourceOrigin, _, reply ->
                entry?.let { receive(view, it, message.data, sourceOrigin, reply, answerV2 = true) }
            }
            true
        } catch (_: IllegalArgumentException) {
            false // the kit v2 identity pipe owns the name on this WebView
        }
        entry = Attached(ownsBridgeName).also {
            attached[webView] = it
            webView.addOnAttachStateChangeListener(it)
        }
        return true
    }

    /** Disconnect a WebView you are about to destroy. Safe to call for one that was never attached. */
    @JvmStatic
    @MainThread
    @SuppressLint("RequiresFeature")
    fun detach(webView: WebView) {
        val entry = attached.remove(webView) ?: return
        webView.removeOnAttachStateChangeListener(entry)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            try {
                WebViewCompat.removeWebMessageListener(webView, EVENTS_NAME)
                if (entry.ownsBridgeName) WebViewCompat.removeWebMessageListener(webView, BRIDGE_NAME)
            } catch (_: Exception) { /* the WebView is already gone */ }
        }
        refreshUnread()
    }

    /**
     * Ask the server what is unread (`POST /api/chat/unread` with the stored visitor token). Runs by
     * itself on [init], on every app foreground and when the chat screen closes; call it after your
     * own push notification if you have one. Does nothing until the person has opened the chat once
     * on this device (there is no token to ask with). Never throws; a failed request changes nothing.
     */
    @JvmStatic
    fun refreshUnread() = onMain {
        val current = session ?: return@onMain
        val now = SystemClock.elapsedRealtime()
        if (refreshing || (refreshedAt != 0L && now - refreshedAt < MIN_REFRESH_GAP_MS)) return@onMain
        refreshing = true
        refreshedAt = now
        io.execute {
            val answer = try {
                current.tokens.load(current.config.assistant)?.let {
                    BusymateUnreadClient.ask(current.apiOrigin, current.config.assistant, it, BusymateLaunch.SDK_VERSION)
                }
            } catch (_: Exception) { null }
            main.post {
                refreshing = false
                if (session === current && answer is BusymateUnreadClient.Answer.Known) apply(current, answer.unread)
            }
        }
    }

    /**
     * The person opens the chat from a badge or a bubble. The built-in components call this for you;
     * call it from your own UI just before you show the chat screen. It hides the teaser and asks the
     * chat (now, or as soon as it loads) to show the conversation at the new message.
     */
    @JvmStatic
    fun openChat() = onMain {
        val current = session ?: return@onMain
        openUntil = SystemClock.elapsedRealtime() + OPEN_WINDOW_MS
        openRequest += 1
        if (current.state.dismiss()) { remember(current); publish(current) }
        for ((view, entry) in attached.entries.toList()) if (entry.reply != null) sendOpen(view, entry)
    }

    /** Hide the teaser bubble. The message stays unread: the badge keeps its count. */
    @JvmStatic
    fun dismissTeaser() = onMain {
        val current = session ?: return@onMain
        if (current.state.dismiss()) { remember(current); publish(current) }
    }

    /** Your user signed out: forget this device's visitor token and clear the badge. */
    @JvmStatic
    fun reset() = onMain {
        val current = session ?: return@onMain
        current.state.update(BusymateUnread.NONE)
        remember(current)
        publish(current)
        io.execute { try { current.tokens.clear(current.config.assistant) } catch (_: Exception) { } }
    }

    /** Called on the main thread with the current value at once, then on every change. */
    @JvmStatic
    fun addOnUnreadListener(listener: OnUnreadListener) {
        if (listeners.add(listener)) onMain { if (listener in listeners) listener.onUnreadChanged(unreadFlow.value) }
    }

    @JvmStatic
    fun removeOnUnreadListener(listener: OnUnreadListener) {
        listeners.remove(listener)
    }

    /** Called on the main thread with the current teaser at once, then each time it shows, changes or hides. */
    @JvmStatic
    fun addOnTeaserListener(listener: OnTeaserListener) {
        if (teaserListeners.add(listener)) onMain { if (listener in teaserListeners) listener.onTeaserChanged(teaserFlow.value) }
    }

    @JvmStatic
    fun removeOnTeaserListener(listener: OnTeaserListener) {
        teaserListeners.remove(listener)
    }

    // ── the channel ─────────────────────────────────────────────────────────

    /** Runs on the UI thread (WebMessageListener contract). */
    private fun receive(view: WebView, entry: Attached, data: String?, sourceOrigin: Uri, reply: JavaScriptReplyProxy, answerV2: Boolean) {
        val current = session ?: return
        // The WebView already filtered by origin rule; checked again so a rule mistake cannot widen it.
        if (BusymateMessages.origin(sourceOrigin.toString()) !in current.origins) return
        when (val message = BusymateMessages.parse(data)) {
            null -> return
            is BusymateMessage.V2Request -> {
                // Not the identity pipe: say so at once, so the chat does not wait for a `hello`.
                if (answerV2) try {
                    reply.postMessage(org.json.JSONObject().put("bm", 2).put("id", message.id).put("ok", false).put("e", "unsupported").toString())
                } catch (_: Exception) { }
                return
            }
            is BusymateMessage.Unread -> apply(current, message.value)
            is BusymateMessage.Identity -> {
                val token = message.token
                io.execute { try { current.tokens.save(current.config.assistant, token) } catch (_: Exception) { } }
            }
        }
        entry.reply = reply
        sendOpen(view, entry)
    }

    /** Once per [openChat] and WebView, while the request is fresh and the chat is on screen. */
    private fun sendOpen(view: WebView, entry: Attached) {
        val current = session ?: return
        if (entry.openSent == openRequest || SystemClock.elapsedRealtime() >= openUntil || !view.isAttachedToWindow) return
        entry.openSent = openRequest
        try { entry.reply?.postMessage(BusymateMessages.OPEN_MESSAGE) } catch (_: Exception) { /* that frame is gone */ }
        // The same signal the iOS SDK sends, for a frame that listens on its own window.
        if (BusymateMessages.originOfUrl(view.url) in current.origins) {
            view.evaluateJavascript("window.postMessage(${BusymateMessages.OPEN_MESSAGE},location.origin)", null)
        }
    }

    private fun apply(current: Session, next: BusymateUnread) {
        if (current.state.update(next)) { remember(current); publish(current) }
    }

    private fun publish(current: Session) {
        val value = current.state.unread
        val bubble = current.state.teaser
        if (teaserFlow.value != bubble) {
            teaserFlow.value = bubble
            for (listener in teaserListeners) listener.onTeaserChanged(bubble)
        }
        if (unreadFlow.value != value) {
            unreadFlow.value = value
            for (listener in listeners) listener.onUnreadChanged(value)
        }
    }

    /** Remembers WHICH message was dismissed (a digest of its time, never its text). */
    private fun remember(current: Session) {
        try {
            val prefs = current.context.getSharedPreferences(BusymateTokenStore.PREFS, Context.MODE_PRIVATE)
            val key = DISMISSED_KEY + current.config.assistant
            val value = current.state.dismissedKey
            if (prefs.getString(key, null) != value) prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
        } catch (_: Exception) { }
    }

    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }
}

package ai.busymate.sample

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.text.InputType
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.*
import ai.busymate.Busymate
import ai.busymate.bridge.BusymateBridge
import ai.busymate.whitelabel.BusymateFiles
import ai.busymate.whitelabel.BusymateLaunch
import ai.busymate.whitelabel.BusymateMicrophone
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

class ChatActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var microphone: BusymateMicrophone? = null
    private lateinit var events: TextView
    private val fields = mutableMapOf<String, EditText>()
    private val toggles = mutableMapOf<String, CheckBox>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val current = DemoSession.settings
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val settings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun field(name: String, value: String, secret: Boolean = false) {
            settings.addView(TextView(this).apply { text = name })
            fields[name] = EditText(this).apply {
                setText(value); setSingleLine()
                if (secret) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                settings.addView(this)
            }
        }
        field("Assistant", current.assistant); field("Chat URL", current.chatUrl)
        field("Exact origins (comma-separated)", current.extraOrigins)
        field("Authenticated backend mint URL", current.backendUrl)
        field("Account ID", current.accountId); field("Backend session bearer (optional)", current.backendBearer, true)
        fun toggle(name: String, value: Boolean) { toggles[name] = CheckBox(this).apply { text = name; isChecked = value; settings.addView(this) } }
        toggle("Native microphone adapter", current.microphoneEnabled)
        toggle("Handle close action", current.closeEnabled); toggle("Handle ready action", current.actionsEnabled)
        fun button(label: String, action: () -> Unit) { settings.addView(Button(this).apply { text = label; setOnClickListener { action() } }) }
        button("Apply settings / recreate WebView safely") {
            val updated = DemoSettings(fields.getValue("Assistant").text.toString(), fields.getValue("Chat URL").text.toString(),
                fields.getValue("Exact origins (comma-separated)").text.toString(), toggles.getValue("Native microphone adapter").isChecked,
                toggles.getValue("Handle close action").isChecked, toggles.getValue("Handle ready action").isChecked,
                fields.getValue("Authenticated backend mint URL").text.toString(), fields.getValue("Account ID").text.toString(),
                fields.getValue("Backend session bearer (optional)").text.toString())
            val error = updated.validationError()
            if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show() else { DemoSession.settings = updated; recreate() }
        }
        button("Sign in using configured app session") {
            if (current.backendUrl.isEmpty() || current.accountId.isEmpty()) log("Configure backend URL and account ID, then Apply first.")
            else { DemoSession.signedIn = true; BusymateBridge.accountChanged(); log("Account changed; chat will ask authenticated backend.") }
        }
        button("Sign out / clear verified identity") { DemoSession.signedIn = false; BusymateBridge.accountChanged(); log("Signed out: state event sent.") }
        button("Notify account changed / refresh state") { BusymateBridge.accountChanged(); log("Current account state emitted.") }
        // What the chat itself does when the mate writes while it is closed, sent from the loaded page through the
        // real channel (origin rule, schema check). For trying the badge and the bubble: go back to the main screen after.
        button("Send a test follow-up signal from the page") {
            webView.evaluateJavascript("(function(){var b=window.BusymateEvents||window.BusymateBridge;if(!b)return 'no bridge';" +
                "b.postMessage(JSON.stringify({type:'support.chat.v1.unread',n:1,t:'This is a test follow-up from the example app.',at:new Date().toISOString()}));return 'sent'})()") {
                log("Test follow-up signal: $it. Go back to see the badge and the bubble.")
            }
        }
        button("Reload chat") { webView.reload(); log("Chat reload requested.") }
        button("Open app microphone settings") { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
        button("Reset demo (guest / defaults)") { DemoSession.settings = DemoSettings(); DemoSession.signedIn = false; recreate() }
        events = TextView(this).apply { text = "Guest demo uses real hosted chat. Tokens/audio are never logged.\n" }
        settings.addView(events)
        column.addView(ScrollView(this).apply { addView(settings) }, LinearLayout.LayoutParams(-1, 420))
        webView = WebView(this)
        column.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(column)
        // The chat composer docks on the on-screen keyboard only if THIS view
        // shrinks when the keyboard opens (hosted chat lays out against its
        // viewport; it cannot see the keyboard otherwise). An edge-to-edge
        // window (Android 15+ for targetSdk 35, or any `enableEdgeToEdge()` /
        // `setDecorFitsSystemWindows(false)` host) no longer resizes for the
        // IME by itself, so the host applies the IME inset. See
        // docs/configuration.md → "Keyboard and window insets".
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(column) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        BusymateBridge.install(webView, current.identityConfig(
            mint = { request, done -> log("Backend mint requested (sensitive fields omitted)."); BackendMint.mint(current, request, done) },
            log = { log(it) }, close = { finish() },
            replaced = { replacement -> webView = replacement; log("Identity SDK replaced renderer WebView.") },
        ))
        // Follow-ups: the chat tells the app what is unread, so the main screen can show the badge and the bubble.
        // After BusymateBridge.install (both want the JS name "BusymateBridge"; the identity pipe keeps it).
        Busymate.init(this, current.busymateConfig())
        if (Busymate.attach(webView)) log("Follow-ups channel attached.") else log("Follow-ups channel unavailable on this WebView.")
        // The chat's attach (+) button: Android WebView shows no picker unless the
        // WebChromeClient answers onShowFileChooser. BusymateMicrophone installs this
        // too; calling it here keeps + working with the microphone adapter off.
        // It wraps the WebChromeClient set below at ON_START (docs/configuration.md → "Attachments").
        BusymateFiles.install(this, webView)
        if (current.microphoneEnabled) microphone = BusymateMicrophone(this, webView, BusymateBridge.allowedOrigins(
            BusymateBridge.Config(current.assistant, current.origins(), { null }, { _, done -> done(BusymateBridge.MintResult.NotSignedIn) })).toSet())
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                log("WebView requested media access.")
                microphone?.allowMedia(request) ?: request.deny()
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                log("Renderer terminated; reconstructing host safely.")
                if (current.microphoneEnabled) { recreate(); return true }
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) BusymateBridge.onRenderProcessGone(view, detail) else false
            }
        }
        webView.loadUrl(BusymateLaunch.chatUrl(current.chatUrl, this))
    }
    private fun log(message: String) { runOnUiThread { events.append(message + "\n"); if (events.text.length > 6000) events.text = events.text.takeLast(4000) } }
    override fun onResume() { super.onResume(); if (::events.isInitialized) log("Activity resumed; SDK observes foreground state.") }
    override fun onDestroy() {
        if (::webView.isInitialized) {
            Busymate.detach(webView)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.removeWebMessageListener(webView, "BusymateMicrophone")
                WebViewCompat.removeWebMessageListener(webView, BusymateFiles.NAME)
                WebViewCompat.removeWebMessageListener(webView, BusymateBridge.NAME)
            }
            webView.removeJavascriptInterface(BusymateBridge.NAME)
            webView.stopLoading(); webView.destroy()
        }
        super.onDestroy()
    }
}

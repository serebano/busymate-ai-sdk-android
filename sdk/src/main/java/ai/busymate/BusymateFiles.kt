package ai.busymate.whitelabel

import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.MimeTypeMap
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.WeakHashMap

/** Busymate AI file picking (files adapter 1.0.0): makes the hosted chat's attach (+) button work.
 *
 * Android WebView ignores `<input type="file">` unless the WebView's WebChromeClient answers
 * `onShowFileChooser`, so without this the chat's + button does nothing. Images and PDFs are
 * picked through the system document picker (Storage Access Framework): no storage or camera
 * permission is added to your app, and only the files the user picks are readable by the page.
 *
 * Zero config: [BusymateMicrophone] installs this for you. Without the microphone adapter call
 * `BusymateFiles.install(activity, webView)` in `onCreate`, before STARTED and before `loadUrl`.
 * At ON_START it wraps the WebChromeClient you set (every callback is forwarded to yours) unless
 * yours already overrides `onShowFileChooser`. If you set a new WebChromeClient after the
 * Activity started, forward from it instead:
 *
 *   override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>,
 *       params: FileChooserParams) = BusymateFiles.install(activity, view).show(callback, params)
 *
 * The chat checks `window.BusymateFiles` (an inert listener object exposed only to busymate.ai
 * and your listed origins; it carries no data) and hides + in an SDK WebView that cannot pick files.
 */
class BusymateFiles private constructor(activity: ComponentActivity, private val webView: WebView, origins: Set<String>) {
    private var pending: ValueCallback<Array<Uri>>? = null
    private val many = activity.registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { settle(it) }
    private val one = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { settle(listOfNotNull(it)) }

    init {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) runCatching {
            WebViewCompat.addWebMessageListener(webView, NAME, rules(origins)) { _, _, _, _, _ -> }
        }
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { owner.lifecycle.removeObserver(this); wrap() }
        })
    }

    /** Open the system picker for one `onShowFileChooser` call. Always returns true. */
    fun show(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams?): Boolean {
        pending?.onReceiveValue(null)
        pending = callback
        val types = mimeTypes(params?.acceptTypes) { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        val launched = runCatching {
            if (params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) many.launch(types) else one.launch(types)
        }
        if (launched.isFailure) settle(emptyList())
        return true
    }

    private fun settle(uris: List<Uri>) {
        val callback = pending ?: return
        pending = null
        callback.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
    }

    private fun wrap() {
        val current = runCatching { WebViewCompat.getWebChromeClient(webView) }.getOrNull()
        if (current is Chooser || (current != null && overridesChooser(current.javaClass))) return
        webView.webChromeClient = Chooser(current, this)
    }

    /** Forwards every callback to the host's client; answers only the file chooser. */
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private class Chooser(private val host: WebChromeClient?, private val files: BusymateFiles) : WebChromeClient() {
        private val h: WebChromeClient get() = host ?: Base
        override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams) = files.show(callback, params)
        override fun onProgressChanged(view: WebView, newProgress: Int) = h.onProgressChanged(view, newProgress)
        override fun onReceivedTitle(view: WebView, title: String?) = h.onReceivedTitle(view, title)
        override fun onReceivedIcon(view: WebView, icon: Bitmap?) = h.onReceivedIcon(view, icon)
        override fun onReceivedTouchIconUrl(view: WebView, url: String?, precomposed: Boolean) = h.onReceivedTouchIconUrl(view, url, precomposed)
        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) = h.onShowCustomView(view, callback)
        override fun onShowCustomView(view: View?, orientation: Int, callback: CustomViewCallback?) = h.onShowCustomView(view, orientation, callback)
        override fun onHideCustomView() = h.onHideCustomView()
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?) = h.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
        override fun onRequestFocus(view: WebView) = h.onRequestFocus(view)
        override fun onCloseWindow(window: WebView) = h.onCloseWindow(window)
        override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult) = h.onJsAlert(view, url, message, result)
        override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult) = h.onJsConfirm(view, url, message, result)
        override fun onJsPrompt(view: WebView, url: String?, message: String?, defaultValue: String?, result: JsPromptResult) = h.onJsPrompt(view, url, message, defaultValue, result)
        override fun onJsBeforeUnload(view: WebView, url: String?, message: String?, result: JsResult) = h.onJsBeforeUnload(view, url, message, result)
        override fun onExceededDatabaseQuota(url: String?, databaseIdentifier: String?, quota: Long, estimatedDatabaseSize: Long, totalQuota: Long, quotaUpdater: WebStorage.QuotaUpdater?) =
            h.onExceededDatabaseQuota(url, databaseIdentifier, quota, estimatedDatabaseSize, totalQuota, quotaUpdater)
        override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) = h.onGeolocationPermissionsShowPrompt(origin, callback)
        override fun onGeolocationPermissionsHidePrompt() = h.onGeolocationPermissionsHidePrompt()
        override fun onPermissionRequest(request: PermissionRequest) = h.onPermissionRequest(request)
        override fun onPermissionRequestCanceled(request: PermissionRequest) = h.onPermissionRequestCanceled(request)
        override fun onJsTimeout() = h.onJsTimeout()
        override fun onConsoleMessage(message: String?, lineNumber: Int, sourceID: String?) = h.onConsoleMessage(message, lineNumber, sourceID)
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?) = h.onConsoleMessage(consoleMessage)
        override fun getDefaultVideoPoster(): Bitmap? = h.defaultVideoPoster
        override fun getVideoLoadingProgressView(): View? = h.videoLoadingProgressView
        override fun getVisitedHistory(callback: ValueCallback<Array<String>>?) = h.getVisitedHistory(callback)
        private object Base : WebChromeClient()
    }

    companion object {
        const val NAME = "BusymateFiles"
        const val VERSION = "1.0.0"
        private val installed = WeakHashMap<WebView, BusymateFiles>()

        /** Install once per WebView (later calls return the same instance). Call during onCreate. */
        @JvmStatic
        @JvmOverloads
        fun install(activity: ComponentActivity, webView: WebView, origins: Set<String> = emptySet()): BusymateFiles =
            installed.getOrPut(webView) { BusymateFiles(activity, webView, origins) }

        /** busymate.ai, its assistant hosts and your exact https origins. */
        internal fun rules(origins: Set<String>): Set<String> =
            setOf("https://busymate.ai", "https://*.busymate.ai") + origins.filter { it.startsWith("https://") && !it.contains('*') }

        /** True when the host client answers the file chooser itself. */
        internal fun overridesChooser(type: Class<*>): Boolean = runCatching {
            type.getMethod("onShowFileChooser", WebView::class.java, ValueCallback::class.java,
                WebChromeClient.FileChooserParams::class.java).declaringClass != WebChromeClient::class.java
        }.getOrDefault(true)

        /** `accept` tokens (MIME wildcards such as image/any, `.pdf`, comma lists) as picker MIME types; anything else is any file. */
        internal fun mimeTypes(accept: Array<String>?, byExtension: (String) -> String?): Array<String> {
            val types = accept.orEmpty().flatMap { it.split(',') }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                .mapNotNull { if (it.startsWith('.')) byExtension(it.drop(1)) else it.takeIf { t -> '/' in t } }.distinct()
            return if (types.isEmpty()) arrayOf("*/*") else types.toTypedArray()
        }
    }
}

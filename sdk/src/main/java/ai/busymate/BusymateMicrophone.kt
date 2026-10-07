package ai.busymate.whitelabel

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/** Optional add-on: construct during Activity.onCreate, before STARTED/loadUrl.
 * Keep it alive and route WebChromeClient.onPermissionRequest to allowMedia.
 * No JavascriptInterface fallback: only a verified sending frame may ask.
 */
class BusymateMicrophone(
    private val activity: ComponentActivity,
    webView: WebView,
    origins: Set<String>,
) {
    private val allowed = origins.filter { value ->
        val url = Uri.parse(value)
        url.scheme == "https" && url.host != null && !value.contains("*") &&
            url.path.isNullOrEmpty() && url.query == null && url.fragment == null && url.userInfo == null
    }.map { normalized(Uri.parse(it)) }.toSet()
    private val waiting = mutableListOf<(Boolean) -> Unit>()
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val callbacks = waiting.toList()
        waiting.clear()
        callbacks.forEach { it(granted) }
    }
    init {
        if (allowed.isNotEmpty() && WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, "BusymateMicrophone", allowed) { _, message, sourceOrigin, _, reply ->
                val raw = message.data ?: ""
                if (raw.length > 4096) return@addWebMessageListener
                val request = runCatching { JSONObject(raw) }.getOrNull()
                if (normalized(sourceOrigin) !in allowed || request == null ||
                    request.optString("type") != "busymate.microphone.v1.request" ||
                    request.optString("id").isEmpty()) return@addWebMessageListener
                val id = request.getString("id")
                activity.runOnUiThread {
                    ask { granted -> reply.postMessage(JSONObject().put("id", id).put("granted", granted).toString()) }
                }
            }
        }
    }
    private fun normalized(origin: Uri): String {
        val port = if (origin.port == -1 || origin.port == 443) "" else ":${origin.port}"
        return "${origin.scheme}://${origin.host?.lowercase()}$port"
    }
    private fun granted(): Boolean = ContextCompat.checkSelfPermission(
        activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun ask(done: (Boolean) -> Unit) {
        if (granted()) { done(true); return }
        waiting.add(done)
        if (waiting.size == 1) launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
    /** Grant only audio, only the configured requesting origin, only after OS grant.
     * Call this on the UI thread from WebChromeClient.onPermissionRequest.
     */
    fun allowMedia(request: PermissionRequest) {
        if (normalized(request.origin) in allowed && granted() &&
            PermissionRequest.RESOURCE_AUDIO_CAPTURE in request.resources) {
            request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
        } else request.deny()
    }
}

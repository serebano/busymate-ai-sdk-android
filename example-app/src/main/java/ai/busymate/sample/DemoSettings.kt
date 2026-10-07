package ai.busymate.sample

import java.net.URI
import android.webkit.WebView
import ai.busymate.bridge.BusymateBridge

data class DemoSettings(
    val assistant: String = "busyproxy",
    val chatUrl: String = "https://busymate.ai/support/busyproxy?channel=android&locale=en",
    val extraOrigins: String = "https://busymate.ai",
    val microphoneEnabled: Boolean = true,
    val closeEnabled: Boolean = true,
    val actionsEnabled: Boolean = true,
    val backendUrl: String = "",
    val accountId: String = "",
    val backendBearer: String = "",
) {
    fun identityConfig(
        mint: (Map<String, Any?>, (BusymateBridge.MintResult) -> Unit) -> Unit,
        log: (String) -> Unit,
        close: () -> Unit,
        replaced: (WebView) -> Unit,
    ) = BusymateBridge.Config(
        assistant = assistant, origins = origins(),
        account = { if (DemoSession.signedIn) accountId.takeIf { it.isNotEmpty() } else null },
        mint = mint,
        onAction = if (actionsEnabled) ({ name, _ -> log("Native action: $name"); name == "ready" }) else null,
        onClose = if (closeEnabled) ({ log("Chat requested host close."); close() }) else null,
        onReplaced = replaced,
    )
    fun origins(): List<String> = extraOrigins.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    fun validationError(): String? {
        if (!Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$").matches(assistant)) return "Use a valid assistant slug."
        val url = runCatching { URI(chatUrl) }.getOrNull()
        if (url?.scheme != "https" || url.host.isNullOrEmpty() || url.userInfo != null) return "Chat URL must use HTTPS without credentials."
        if (origins().any { !validOrigin(it) }) return "Origins must be exact HTTPS scheme + host + optional port."
        if (backendUrl.isNotEmpty()) {
            val backend = runCatching { URI(backendUrl) }.getOrNull()
            if (backend?.scheme != "https" || backend.host.isNullOrEmpty() || backend.userInfo != null) return "Backend URL must use HTTPS."
        }
        return null
    }
    companion object {
        fun validOrigin(value: String): Boolean {
            val url = runCatching { URI(value) }.getOrNull() ?: return false
            return url.scheme == "https" && !url.host.isNullOrEmpty() && url.userInfo == null &&
                url.rawPath.isNullOrEmpty() && url.query == null && url.fragment == null && !value.contains('*')
        }
    }
}
// In-memory demo configuration only: bearer/account values are never persisted or logged.
object DemoSession { var settings = DemoSettings(); var signedIn = false }

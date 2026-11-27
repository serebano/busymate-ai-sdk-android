package ai.busymate.whitelabel

import android.content.Context

/** Busymate AI launch parameters (launch 1.0.0).
 *
 * Tells the hosted chat which SDK release, in which app, is showing it, so the workspace's Insights
 * can answer "which SDK version, in which app". Load the chat through it:
 *
 *     webView.loadUrl(BusymateLaunch.chatUrl(chatUrl, context))
 *
 * It appends `sdkVersion=<this SDK's version>` and, when it is a plain identifier,
 * `hostAppId=<your applicationId>`. Nothing else: no request, no permission, no stored id (a version
 * and an app id are not personal data). Your own query items (for example `channel=android`) are kept.
 */
object BusymateLaunch {
    /** The SDK release (`release-manifest.json` `version`; `scripts/verify-release.py` refuses a mismatch). */
    const val SDK_VERSION = "1.1.0"

    private val versionPattern = Regex("^[0-9A-Za-z.+-]{1,32}$")
    private val appIdPattern = Regex("^[A-Za-z][A-Za-z0-9._-]{1,127}$")

    /** Digits, letters, `.`, `+`, `-`, at most 32 characters. */
    fun isValidSdkVersion(value: String): Boolean = versionPattern.matches(value)

    /** `^[A-Za-z][A-Za-z0-9._-]{1,127}$`: a package name; anything else is left out. */
    fun isValidHostAppId(value: String): Boolean = appIdPattern.matches(value)

    /** [base] with the launch parameters appended; the host app id is `context.packageName`. */
    fun chatUrl(base: String, context: Context): String = chatUrl(base, context.packageName)

    /** [base] with the launch parameters appended (an earlier `sdkVersion` / `hostAppId` is replaced). */
    fun chatUrl(base: String, hostAppId: String?): String = launchUrl(base, SDK_VERSION, hostAppId)

    internal fun launchUrl(base: String, sdkVersion: String, hostAppId: String?): String {
        val hash = base.indexOf('#')
        val fragment = if (hash >= 0) base.substring(hash) else ""
        val withoutFragment = if (hash >= 0) base.substring(0, hash) else base
        val q = withoutFragment.indexOf('?')
        val path = if (q >= 0) withoutFragment.substring(0, q) else withoutFragment
        val kept = if (q >= 0) withoutFragment.substring(q + 1).split('&').filter {
            it.isNotEmpty() && it.substringBefore('=') != "sdkVersion" && it.substringBefore('=') != "hostAppId"
        } else emptyList()
        val items = kept.toMutableList()
        if (isValidSdkVersion(sdkVersion)) items += "sdkVersion=" + sdkVersion.replace("+", "%2B")
        if (hostAppId != null && isValidHostAppId(hostAppId)) items += "hostAppId=" + hostAppId.replace("+", "%2B")
        return path + (if (items.isEmpty()) "" else "?" + items.joinToString("&")) + fragment
    }
}

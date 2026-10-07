# Configuration reference

| Setting | v2 behavior |
|---|---|
| `assistant` | Required assistant slug; adds exactly `https://<assistant>.busymate.ai` to allowed origins. Obtain the actual assistant URL from Busymate; a marketing page is not a chat URL. |
| `origins` | Extra exact HTTPS origins for mapped support hosts/frames. No wildcards. Supply the actual chat frame origin independently to the microphone adapter. |
| `account` | Closure returning current account ID or nil/null, read when state/mint is requested. |
| `mint` | Required authenticated-backend callback, fresh token/nonce for every request; never a cached assertion. |
| `onAction` | Optional fixed app-action handler; return true only when handled. |
| `onClose` | Optional host dismissal callback; fallback for the `close` action. |

The microphone adapter accepts only `webView` and exact `origins` (plus Android host activity). It is installed once before load, not on each tap. Chat triggers dictation/voice requests and controls a 60-second wait. There is no SDK setting for automatic OS prompts on page load, camera access, opening Settings, timeout override, audio-session category, provider credentials or token caching.

Hosted chat appearance, theme, locale, voice availability and tenant behavior are configured through the hosted chat/embed surface and Busymate Console, not by arbitrary native SDK flags. Preserve the tenant-generated chat URL, including its supported query parameters. The SDK does not own safe-area/layout/navigation styling or audio routing; your host does.

V1 configuration: iOS initializer takes `webView`, `identityProvider`, optional `openExternal`, `onClose`, `allowedOrigins`; Android `install` takes `context`, `webView`, `identityProvider`, optional `onClose`, `allowedOrigins`. Android enables JavaScript but does not alter cookie policy. Third-party cookies are not required for native identity. Legacy compatibility handler names remain present; do not register another handler using those names.

Microphone registration: iOS `BusymateMicrophone`; Android `BusymateMicrophone`. Request type `busymate.microphone.v1.request`, source `dictation` or `voice`, correlated `id`; reply has the same `id` and a boolean `granted`. See [microphone integration](microphone.md) for denial, retry, Settings and cancellation behavior.

## Android-specific APIs

`onReplaced(WebView)`: callback receiving the replacement WebView after a renderer crash. The frozen SDK copies settings, reinstalls the identity bridge and reloads the URL; it cannot recreate your microphone Activity Result registration. If using renderer recovery with microphone integration, recreate the Activity so the new microphone adapter registers during `onCreate` before `STARTED`, and restore your delegates/layout as needed. Do not install a fresh microphone adapter late inside `onReplaced`.

`BusymateBridge.install(webView, config)` enables JavaScript and DOM storage and registers before load. `allowedOrigins(config)` returns the normalized origin list. `accountChanged()` emits account state. `onRenderProcessGone(webView, detail)` supports identity-only renderer replacement; forward it from `WebViewClient` only with appropriate host lifecycle coordination. Foreground resume is observed with ProcessLifecycleOwner. The identity v2 bridge uses verified frame message transport where supported, with its frozen compatibility fallback for old WebViews; microphone deliberately has no JavascriptInterface fallback.

For Fragment/Compose hosts, register the microphone Activity Result launcher before the owning Activity starts and retain the WebView/adapter with the actual owner. Dispose listeners and destroy the WebView only when no view still uses it. Activity recreation constructs new bridges early; never add duplicate handlers on every recomposition or tap. Preserve all existing WebChromeClient/WebViewClient callbacks. V2 has no public uninstall function in frozen build 2.0.0; remove registered native handlers as part of your host disposal.

V1 Android methods: `refreshIdentity`, `identityChanged`, `signedOut(reason)` and `onResume`; its provider returns a callback `Result<BusymateAIIdentity?>`. Handler names are `BusymateAINative` and compatibility `SupportChatNative`. JavaScript interfaces must be removed when their host is disposed.

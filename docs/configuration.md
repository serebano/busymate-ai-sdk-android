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

## Keyboard and window insets

The hosted chat docks its message composer on the on-screen keyboard by laying out against the WebView's own viewport (`interactive-widget=resizes-content` on the page's viewport meta, `dvh` heights). It never measures the keyboard itself: inside a WebView, `visualViewport` and `innerHeight` only change when the **view** changes size. So the composer is above the keyboard exactly when your host shrinks the WebView by the keyboard's height, and hidden under it when the keyboard paints over a full-height WebView (busymate-ai-src#2097, the "Chat with support" screen with the keyboard open and no input in sight).

| Host window | When the keyboard opens | Composer |
|---|---|---|
| Decor fits system windows (the classic default) with `android:windowSoftInputMode="adjustResize"` — or the default `adjustUnspecified`, which Android resolves to resize for a WebView | The framework resizes the window and the WebView with it. Measured on a Pixel 4a (Android 13, Chromium 117 WebView): the page viewport went from 698 to 319 CSS px, `innerHeight == visualViewport.height == 319`. | docked on the keyboard |
| Edge-to-edge — `enableEdgeToEdge()`, `WindowCompat.setDecorFitsSystemWindows(window, false)`, or Android 15+ with `targetSdk 35`, which enforces edge-to-edge — **without** applying the `ime()` inset | The window is **not** resized for the IME on Android 11+. The WebView keeps its full height, the keyboard paints over its bottom, and the page sees no change at all. | hidden under the keyboard |
| Edge-to-edge **with** `ViewCompat.setOnApplyWindowInsetsListener` applying `WindowInsetsCompat.Type.ime()` to the WebView's container | The container shrinks by the keyboard; same geometry as the first row. | docked on the keyboard |

Declare `adjustResize` on the chat Activity and, because every host sooner or later runs edge-to-edge, apply the insets yourself on the view that holds the WebView (the example app does both — [`ChatActivity`](../example-app/src/main/java/ai/busymate/sample/ChatActivity.kt), [manifest](../example-app/src/main/AndroidManifest.xml)):

```kotlin
WindowCompat.setDecorFitsSystemWindows(window, false)
ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
    val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
    val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
    view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
    WindowInsetsCompat.CONSUMED
}
```

Padding goes on the **container**, not on the `WebView` (a WebView draws its content under its own padding on several engine versions). With `adjustPan` the window pans instead of resizing and the page cannot dock anything: do not use it for the chat screen. Verify on a physical device with the keyboard open: the input, its send button and the first line of the reply must all be visible; `adb shell dumpsys activity top` shows the `WebView{…}` bounds, which must end at the keyboard's top edge.

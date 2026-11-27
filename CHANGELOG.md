# Changelog

## 1.1.0

Follow-ups on your app's own screens: a message channel between the chat WebView and the app, an unread badge and a message bubble (owner ask 2026-11-27, "follow-ups on every surface").

- **Channel.** `Busymate.attach(webView)` registers a `WebViewCompat.addWebMessageListener` named `BusymateEvents`, and `BusymateBridge` when that name is free (the frozen kit v2 identity pipe keeps the name when installed, so call `attach` after `BusymateBridge.install`). Allowed origins are only the chat's own: `https://busymate.ai`, `https://<assistant>.busymate.ai`, the origin of your chat URL and the exact https origins you list. No `addJavascriptInterface`. Messages are schema-checked (at most 4 KB) and unknown types are dropped. Handled: `support.chat.v1.unread` `{n, t, at}` and `support.chat.v1.identity` `{v}`; sent: `support.chat.v1.open` when the person opens the chat from the badge or bubble.
- **API.** `Busymate.init(context, BusymateConfig(assistant))`, `Busymate.unread` (`StateFlow<BusymateUnread(count, preview, at)>`), `Busymate.teaser`, `OnUnreadListener` / `OnTeaserListener` for Java and callbacks, `refreshUnread()`, `openChat()`, `dismissTeaser()`, `reset()`.
- **With the WebView closed.** `refreshUnread()` sends `POST <chat origin>/api/chat/unread` with `{a, v}`; it runs by itself on init, on every app foreground (`ProcessLifecycleOwner` `ON_START`) and when the chat screen closes. One small request, no polling, no background work, no permission.
- **Token.** The visitor token is encrypted with a non-exportable Android Keystore key (AES-256-GCM, bound to the assistant), kept in private preferences and never logged. A value that does not open is deleted (fail closed).
- **Components (Material 3, light and dark, font scaling, TalkBack).** `BusymateLauncherButton` (count badge) and `BusymateTeaserBubble` (preview, a dismiss control that leaves the message unread, tap opens the chat at the conversation). Views in `sdk` (`ai.busymate.view`, no Material library needed); Jetpack Compose in the new optional module `sdk-compose` (`ai.busymate.compose`), so apps without Compose take on no Compose dependency.
- **Dependencies.** `sdk` adds kotlinx-coroutines-core 1.10.2 (for `StateFlow`). `sdk-compose` uses the Compose BOM 2025.07.00.
- **Example app.** A main screen (`HomeActivity`) with the button and the bubble in both flavours; the chat moved behind it (`ChatActivity`). Guide: `docs/follow-ups.md`.
- 35 new JVM tests (message schema and origins, token store with real AES-GCM, badge/bubble rules, server answers). Identity v1/v2, microphone adapter 1.0.2 and files adapter 1.0.0 bytes are unchanged; `BusymateLaunch.kt` changes only its version constant. Version 1.0.5 stays immutable.

## 1.0.5

Launch parameters. New `BusymateLaunch.kt` (launch 1.0.0): `BusymateLaunch.chatUrl(chatUrl, context)` appends `sdkVersion=<this SDK's version>` and `hostAppId=<your applicationId>` (only when it matches `^[A-Za-z][A-Za-z0-9._-]{1,127}$`) to the chat address you load, so the workspace's Insights can show which SDK release, in which app, a chat ran in. Your own query items (`channel=android`) are kept. No new request, permission or stored id. Wrap the URL you load: `webView.loadUrl(BusymateLaunch.chatUrl(chatUrl, context))`; the example app does. Identity v1/v2 and microphone/files sources are unchanged. Version 1.0.4 stays immutable.

## 1.0.4

Chat address: the hosted chat's frame address is now `https://busymate.ai/chat/w/<assistant>` (busymate-ai-src#6079: no `/support` URLs). The example app defaults to `https://busymate.ai/chat/w/demos?channel=android&locale=en` and the docs name the new address. `/support/<assistant>` keeps working forever as a silent alias, so apps built on 1.0.3 and older need no change. No SDK source bytes changed (identity v1/v2, microphone adapter 1.0.2 and files adapter 1.0.0 are identical to 1.0.3).

## 1.0.3

Attachments: the hosted chat's attach (+) button now opens the system document picker. Android WebView ignores `<input type="file">` unless the host's `WebChromeClient` answers `onShowFileChooser`, which no SDK piece did, so on 1.0.2 and older the + button did nothing (reported 2026-11-19 after visitor attachments went live in every workspace chat). New `BusymateFiles.kt` (files adapter 1.0.0): registers `OpenDocument`/`OpenMultipleDocuments` launchers, wraps the host `WebChromeClient` at `ON_START` (forwarding every other callback; a host that already overrides `onShowFileChooser` keeps its own) and exposes an inert `window.BusymateFiles` capability object to busymate.ai and the listed origins. No new permission. Microphone adapter 1.0.2 (wire 1 unchanged) installs it, so hosts using the microphone adapter only update the SDK. Identity v1/v2 bytes unchanged. Example app installs it explicitly. Verified on a farm Pixel 4a (Android 13): + opens the picker, an image and a PDF attach and the mate reads both.

## 1.0.2

Release packaging: the microphone adapter source (`BusymateMicrophone.kt`, adapter 1.0.1, wire 1, sha256 `7b49ef8232dd8b834b54927b58d5f980fce3c17914380523fde1124696a49632`) is attached to every GitHub release and listed in `SHA256SUMS`; docs link the release, not busymate.ai. No SDK code change (refs serebano/busymate-ai-src#2156). CI also uploads the example debug APK as a build artifact.

## 1.0.1

Microphone adapter 1.0.1 (microphone wire 1 unchanged): `allowMedia` now asks Android for `RECORD_AUDIO` when an allowed chat origin requests audio capture before the app holds the permission, then grants or denies that WebView request with the user's answer. 1.0.0 denied such requests silently, so a chat capture path that did not send the `BusymateMicrophone` event first (for example the chat's built-in speech dictation) never showed the OS prompt (#7). Untrusted origins and non-audio requests are still denied without a prompt. Identity v1/v2 source bytes are unchanged. The example app now defaults to the public `demos` workspace. Upgrading needs your app's own release.

Keyboard and window insets (documentation and example app only; no SDK source bytes changed for this item): the example `ChatActivity` declares `adjustResize` and applies the `ime()` + system-bar insets on an edge-to-edge window so the hosted chat's composer docks on the keyboard; `docs/configuration.md` → "Keyboard and window insets" explains the three host-window configurations and which one hides the composer (busymate-ai-src#2097).

## 1.0.0

First independent OS distribution: existing identity v1 bridge, frozen identity v2 bridge build 2.0.0, and microphone adapter 1.0.0. Identity source bytes are unchanged. Hosted chat receives updates separately; your native app release is needed to install or upgrade native SDK code.

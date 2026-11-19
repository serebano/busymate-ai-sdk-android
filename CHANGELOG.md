# Changelog

## 1.0.3

Attachments: the hosted chat's attach (+) button now opens the system document picker. Android WebView ignores `<input type="file">` unless the host's `WebChromeClient` answers `onShowFileChooser`, which no SDK piece did, so on 1.0.2 and older the + button did nothing (reported 2026-11-19 after visitor attachments went live in every workspace chat). New `BusymateFiles.kt` (files adapter 1.0.0): registers `OpenDocument`/`OpenMultipleDocuments` launchers, wraps the host `WebChromeClient` at `ON_START` (forwarding every other callback; a host that already overrides `onShowFileChooser` keeps its own) and exposes an inert `window.BusymateFiles` capability object to busymate.ai and the listed origins. No new permission. Microphone adapter 1.0.2 (wire 1 unchanged) installs it, so hosts using the microphone adapter only update the SDK. Identity v1/v2 bytes unchanged. Example app installs it explicitly. Verified on a farm Pixel 4a (Android 13): + opens the picker, an image and a PDF attach and the mate reads both.

## 1.0.2

Release packaging: the microphone adapter source (`BusymateMicrophone.kt`, adapter 1.0.1, wire 1, sha256 `7b49ef8232dd8b834b54927b58d5f980fce3c17914380523fde1124696a49632`) is attached to every GitHub release and listed in `SHA256SUMS`; docs link the release, not busymate.ai. No SDK code change (refs serebano/busymate-ai-src#2156). CI also uploads the example debug APK as a build artifact.

## 1.0.1

Microphone adapter 1.0.1 (microphone wire 1 unchanged): `allowMedia` now asks Android for `RECORD_AUDIO` when an allowed chat origin requests audio capture before the app holds the permission, then grants or denies that WebView request with the user's answer. 1.0.0 denied such requests silently, so a chat capture path that did not send the `BusymateMicrophone` event first (for example the chat's built-in speech dictation) never showed the OS prompt (#7). Untrusted origins and non-audio requests are still denied without a prompt. Identity v1/v2 source bytes are unchanged. The example app now defaults to the public `demos` workspace. Upgrading needs your app's own release.

Keyboard and window insets (documentation and example app only; no SDK source bytes changed for this item): the example `ChatActivity` declares `adjustResize` and applies the `ime()` + system-bar insets on an edge-to-edge window so the hosted chat's composer docks on the keyboard; `docs/configuration.md` → "Keyboard and window insets" explains the three host-window configurations and which one hides the composer (busymate-ai-src#2097).

## 1.0.0

First independent OS distribution: existing identity v1 bridge, frozen identity v2 bridge build 2.0.0, and microphone adapter 1.0.0. Identity source bytes are unchanged. Hosted chat receives updates separately; your native app release is needed to install or upgrade native SDK code.

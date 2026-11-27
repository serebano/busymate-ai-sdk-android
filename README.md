# Busymate AI SDK for Android — 1.1.0

Official independent Android library source distribution for Busymate AI hosted chat. Use `BusymateBridge` (identity wire v2, immutable build 2.0.0) plus `BusymateMicrophone` (adapter 1.0.2), and `Busymate` for follow-ups: the unread badge and message bubble on your app's own screens. Existing v1 applications may retain `BusymateAIWebViewBridge`; install exactly one identity bridge per chat.

## Install a pinned source module

Clone this official repository at the **1.1.0** release tag into your application's vendor directory:

```sh
git clone --branch 1.1.0 --depth 1 https://github.com/serebano/busymate-ai-sdk-android.git vendor/busymate-ai-sdk-android
```

Add `include(":busymate-sdk")` and `project(":busymate-sdk").projectDir = file("vendor/busymate-ai-sdk-android/sdk")` to your app's `settings.gradle.kts`; add `implementation(project(":busymate-sdk"))` to the app dependencies. For the Jetpack Compose follow-up components also add `include(":busymate-sdk-compose")` with `project(":busymate-sdk-compose").projectDir = file("vendor/busymate-ai-sdk-android/sdk-compose")` and `implementation(project(":busymate-sdk-compose"))` (your root build then also resolves the Kotlin Compose compiler plugin). Apps without Compose skip it and take on no Compose dependency. Your root pluginManagement must resolve the Android library and Kotlin Android plugins. This release is a source-module installation, not a claimed Maven Central/JitPack coordinate. A versioned release AAR is usable only when published and accompanied by the declared AndroidX dependencies (an AAR alone carries no dependency metadata).

The reference build pins AGP 9.3.3, Gradle 9.6.0, Kotlin 2.4.21 and Java 17; compileSdk 35, minSdk 23. AndroidX dependencies are Activity 1.9.3, WebKit 1.12.1, Core 1.15.0 and Lifecycle Process 2.8.7, plus kotlinx-coroutines-core 1.10.2 (`Busymate.unread` is a `StateFlow`); `sdk-compose` adds the Compose BOM 2025.07.00 with Material 3. Compatible consuming projects can align their own versions. [AGP compatibility](https://developer.android.com/build/releases/agp-9-3-0-release-notes) and [WebKit releases](https://developer.android.com/jetpack/androidx/releases/webkit) explain these dependencies.

## Integrate

The [combined Activity example](example-app/src/main/java/ai/busymate/sample/ChatActivity.kt) and [manifest](example-app/src/main/AndroidManifest.xml) build as a consumer app. Replace its illustrative assistant/chat URL, account reader and mint callback with your tenant's actual authenticated integration. The sample intentionally returns guest identity; it does not simulate a successful backend identity flow.

1. Read [identity and authentication](docs/identity.md).
2. Configure the [complete SDK reference](docs/configuration.md).
3. Follow [microphone and voice integration](docs/microphone.md).
4. Validate on physical devices using the acceptance checklist.
5. Let visitors attach images and PDFs: the chat's + button needs `onShowFileChooser`, which `BusymateMicrophone` (or `BusymateFiles.install`) adds for you ([attachments](docs/configuration.md#attachments-the--button)).
6. Make the WebView shrink for the on-screen keyboard — `adjustResize` plus the `ime()` inset on an edge-to-edge window ([keyboard and window insets](docs/configuration.md#keyboard-and-window-insets)); a full-height WebView hides the composer under the keyboard.
7. Load the chat through `BusymateLaunch.chatUrl(chatUrl, context)` so the workspace sees your SDK version and app id ([launch parameters](docs/configuration.md#launch-parameters)).
8. Show follow-ups on your own screens: the unread badge and the message bubble ([follow-ups](docs/follow-ups.md)).

Install both bridges in `ComponentActivity.onCreate` before `STARTED` and before `loadUrl`. Forward `WebChromeClient.onPermissionRequest` to the microphone adapter. The microphone adapter also installs `BusymateFiles`, which answers `onShowFileChooser` with the system document picker so the chat's attach (+) button works (no new permission); without the microphone adapter call `BusymateFiles.install(activity, webView)` in `onCreate`. User tapping chat microphone/voice triggers OS permission (through the chat event, or through the chat's WebView audio request when the app has not been granted yet); opening chat does not. Denied/revoked/cancelled permission flows are explained in the guide. Supported `WEB_MESSAGE_LISTENER` is required for microphone events; no insecure fallback is provided.

## Follow-ups: unread badge and message bubble

When the mate writes while the chat is closed, your app shows it on its main screen: a count badge on the chat button and a bubble with the message. The chat WebView and your app talk over a message channel (`WebViewCompat.addWebMessageListener`, the chat's own origins only), and with the WebView closed the SDK asks the server on start and on every app foreground. Full guide: [docs/follow-ups.md](docs/follow-ups.md).

```kotlin
Busymate.init(this, BusymateConfig(assistant = "acme"))        // Application.onCreate
Busymate.attach(webView)                                        // before loadUrl, after BusymateBridge.install
BusymateTeaserBubble(onClick = { openChatScreen() })            // Compose (ai.busymate.compose), or the
BusymateLauncherButton(onClick = { openChatScreen() })          // View classes in ai.busymate.view
```

| API | |
|---|---|
| `Busymate.unread` | `StateFlow<BusymateUnread>` with `count`, `preview`, `at`; `OnUnreadListener` for Java and callbacks. |
| `Busymate.teaser` | `StateFlow<BusymateUnread?>`: the message the bubble shows; `OnTeaserListener`. |
| `Busymate.refreshUnread()` | `POST /api/chat/unread` with the stored visitor token. Automatic on init, on app foreground and when the chat closes. |
| `Busymate.openChat()` / `dismissTeaser()` / `reset()` | Open from a badge or bubble; hide the bubble and keep the message unread; forget the token on sign-out. |
| `BusymateLauncherButton`, `BusymateTeaserBubble` | Material 3 components, Compose and View, light and dark, font scaling, TalkBack. |

The visitor token is encrypted with an Android Keystore key and never logged. Messages are schema-checked and unknown types are dropped. No new permission.

## Download the adapter

`BusymateMicrophone.kt` (microphone adapter 1.0.2, wire 1) and `BusymateFiles.kt` (files adapter 1.0.0; the microphone adapter needs it) ship in this SDK and are attached to every GitHub release, with `SHA256SUMS`:

- Latest release: https://github.com/serebano/busymate-ai-sdk-android/releases/latest/download/BusymateMicrophone.kt
- Pinned, SDK 1.1.0: https://github.com/serebano/busymate-ai-sdk-android/releases/download/1.1.0/BusymateMicrophone.kt and https://github.com/serebano/busymate-ai-sdk-android/releases/download/1.1.0/BusymateFiles.kt
- Checksums: https://github.com/serebano/busymate-ai-sdk-android/releases/download/1.1.0/SHA256SUMS (`shasum -a 256 -c SHA256SUMS --ignore-missing`)

This repository and its releases are the only source of the adapter. busymate.ai no longer publishes adapter copies; its legacy `https://busymate.ai/sdk/microphone/1.0.0/` files stay frozen for existing integrations.

## Build and versions

Install Gradle 9.6.0, JDK 17 and Android SDK 35, then run `python3 scripts/verify-release.py` and `./gradlew :sdk:assembleRelease :sdk-compose:assembleRelease :sdk:testDebugUnitTest :example-app:assembleDebug :example-app:testDebugUnitTest :sdk:lintRelease :sdk-compose:lintRelease :example-app:lintDebug`. CI runs that real library/consumer build and publishes its AAR as a build artifact. Physical permission dialogs, tenant backend identity and app-store rollout need separate device validation.

Distribution 1.1.0 differs from identity wire 2/build 2.0.0, microphone wire 1/adapter 1.0.2 and files adapter 1.0.0. [Release manifest](release-manifest.json) pins the source hashes; identity bytes are unchanged since 1.0.0. This repo owns Android SDK releases; [busymate.ai official documentation](https://busymate.ai/docs/guides/mobile-in-app-support?utm_source=github&utm_medium=readme&utm_campaign=busymate-ai-sdk-android) and artifact mirrors pin these sources. See [changelog](CHANGELOG.md) and [v2 wire contract](CONTRACT-v2.md).

## Release process

Every release attaches each `adapterAssets` file from [release-manifest.json](release-manifest.json) under its basename, plus a `SHA256SUMS` covering every attached asset. `scripts/verify-release.py` checks the pinned adapter hashes.

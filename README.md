# Busymate AI SDK for Android — 1.0.2

Official independent Android library source distribution for Busymate AI hosted chat. Use `BusymateBridge` (identity wire v2, immutable build 2.0.0) plus `BusymateMicrophone` (adapter 1.0.1). Existing v1 applications may retain `BusymateAIWebViewBridge`; install exactly one identity bridge per chat.

## Install a pinned source module

Clone this official repository at the **1.0.2** release tag into your application's vendor directory:

```sh
git clone --branch 1.0.2 --depth 1 https://github.com/serebano/busymate-ai-sdk-android.git vendor/busymate-ai-sdk-android
```

Add `include(":busymate-sdk")` and `project(":busymate-sdk").projectDir = file("vendor/busymate-ai-sdk-android/sdk")` to your app's `settings.gradle.kts`; add `implementation(project(":busymate-sdk"))` to the app dependencies. Your root pluginManagement must resolve the Android library and Kotlin Android plugins. This release is a source-module installation, not a claimed Maven Central/JitPack coordinate. A versioned release AAR is usable only when published and accompanied by the declared AndroidX dependencies (an AAR alone carries no dependency metadata).

The reference build pins AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21 and Java 17; compileSdk 35, minSdk 23. AndroidX dependencies are Activity 1.9.3, WebKit 1.12.1, Core 1.15.0 and Lifecycle Process 2.8.7. Compatible consuming projects can align their own versions. [AGP compatibility](https://developer.android.com/build/releases/agp-8-7-0-release-notes) and [WebKit releases](https://developer.android.com/jetpack/androidx/releases/webkit) explain these dependencies.

## Integrate

The [combined Activity example](example-app/src/main/java/ai/busymate/sample/ChatActivity.kt) and [manifest](example-app/src/main/AndroidManifest.xml) build as a consumer app. Replace its illustrative assistant/chat URL, account reader and mint callback with your tenant's actual authenticated integration. The sample intentionally returns guest identity; it does not simulate a successful backend identity flow.

1. Read [identity and authentication](docs/identity.md).
2. Configure the [complete SDK reference](docs/configuration.md).
3. Follow [microphone and voice integration](docs/microphone.md).
4. Validate on physical devices using the acceptance checklist.
5. Make the WebView shrink for the on-screen keyboard — `adjustResize` plus the `ime()` inset on an edge-to-edge window ([keyboard and window insets](docs/configuration.md#keyboard-and-window-insets)); a full-height WebView hides the composer under the keyboard.

Install both bridges in `ComponentActivity.onCreate` before `STARTED` and before `loadUrl`. Forward `WebChromeClient.onPermissionRequest` to the microphone adapter. User tapping chat microphone/voice triggers OS permission (through the chat event, or through the chat's WebView audio request when the app has not been granted yet); opening chat does not. Denied/revoked/cancelled permission flows are explained in the guide. Supported `WEB_MESSAGE_LISTENER` is required for microphone events; no insecure fallback is provided.

## Download the adapter

`BusymateMicrophone.kt` (microphone adapter 1.0.1, wire 1) ships in this SDK and is attached to every GitHub release, with `SHA256SUMS`:

- Latest release: https://github.com/serebano/busymate-ai-sdk-android/releases/latest/download/BusymateMicrophone.kt
- Pinned, SDK 1.0.2: https://github.com/serebano/busymate-ai-sdk-android/releases/download/1.0.2/BusymateMicrophone.kt
- Checksums: https://github.com/serebano/busymate-ai-sdk-android/releases/download/1.0.2/SHA256SUMS (`shasum -a 256 -c SHA256SUMS --ignore-missing`)

This repository and its releases are the only source of the adapter. busymate.ai no longer publishes adapter copies; its legacy `https://busymate.ai/sdk/microphone/1.0.0/` files stay frozen for existing integrations.

## Build and versions

Install Gradle 8.9, JDK 17 and Android SDK 35, then run `python3 scripts/verify-release.py` and `./gradlew :sdk:assembleRelease :sdk:testDebugUnitTest :example-app:assembleDebug :example-app:testDebugUnitTest :sdk:lintRelease :example-app:lintDebug`. CI runs that real library/consumer build and publishes its AAR as a build artifact. Physical permission dialogs, tenant backend identity and app-store rollout need separate device validation.

Distribution 1.0.2 differs from identity wire 2/build 2.0.0 and microphone wire 1/adapter 1.0.1. [Release manifest](release-manifest.json) pins the source hashes; identity bytes are unchanged since 1.0.0. This repo owns Android SDK releases; [busymate.ai official documentation](https://busymate.ai/docs/guides/mobile-in-app-support) and artifact mirrors pin these sources. See [changelog](CHANGELOG.md) and [v2 wire contract](CONTRACT-v2.md).

## Release process

Every release attaches each `adapterAssets` file from [release-manifest.json](release-manifest.json) under its basename, plus a `SHA256SUMS` covering every attached asset. `scripts/verify-release.py` checks the pinned adapter hashes.

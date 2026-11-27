# Follow-ups in your app: unread badge and message bubble

When the mate writes while the chat is closed (a follow-up, a late answer), your customer should see it on your app's own screens, not only inside the chat WebView. From SDK 1.1.0 the chat and your app talk over a small message channel: the chat tells the app what is unread, and the app shows a count badge on its chat button and a message bubble next to it. It also works with the WebView closed: the SDK asks the server what is unread when it starts and every time your app comes to the foreground.

## Set up (three steps)

```kotlin
// 1. Once, early: Application.onCreate.
Busymate.init(this, BusymateConfig(assistant = "acme"))

// 2. Where you create the chat WebView, before loadUrl.
//    If you use the identity bridge, call this AFTER BusymateBridge.install.
Busymate.attach(webView)
webView.loadUrl(BusymateLaunch.chatUrl(chatUrl, this))
// ...and when you destroy that WebView:
Busymate.detach(webView)

// 3. On your main screen: the built-in button and bubble (below), or your own UI.
```

`BusymateConfig(assistant, chatUrl, origins)`: `assistant` is your slug. `chatUrl` defaults to `https://busymate.ai/chat/w/<assistant>`; pass your own address if the chat runs on your domain. `origins` lists extra exact `https` origins that serve your chat. No wildcards.

`Busymate.attach` returns `false` when `init` has not run or the device's WebView is too old for `WEB_MESSAGE_LISTENER`. The SDK has no `addJavascriptInterface` fallback on purpose; on such a device the badge still works through the server check.

## Built-in components (Material 3)

Both follow the SDK by themselves, in light and dark, scale with the system font size and are read by TalkBack ("Chat, 2 unread messages"; the bubble is announced when it appears).

**Jetpack Compose** (module `sdk-compose`, package `ai.busymate.compose`):

```kotlin
Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    BusymateTeaserBubble(onClick = { openChatScreen() })
    BusymateLauncherButton(onClick = { openChatScreen() })
}
```

Colours and shapes come from your `MaterialTheme`. Parameters: `containerColor`, `contentColor`, `shape`, `icon` (button); `containerColor`, `contentColor`, `shape`, `onDismiss` (bubble). Pass `unread` / `teaser` yourself for previews and tests.

**Views** (module `sdk`, package `ai.busymate.view`; no Compose and no Material library needed):

```xml
<ai.busymate.view.BusymateTeaserBubble
    android:id="@+id/bubble" android:layout_width="wrap_content" android:layout_height="wrap_content" />
<ai.busymate.view.BusymateLauncherButton
    android:id="@+id/chat" android:layout_width="wrap_content" android:layout_height="wrap_content" />
```

```kotlin
chat.setOnClickListener { openChatScreen() }
bubble.setOnClickListener { openChatScreen() }
```

Colours are read from your Material 3 theme (`colorPrimaryContainer`, `colorSurfaceContainerHigh`, …). Without one they use the device's dynamic colours (Android 12+) or the Material 3 baseline palette. Set `colors = BusymateColors(…)` on either view for your own. `setIcon(drawable)` replaces the button's icon. The bubble manages its own visibility.

What they do:

| Action | Result |
|---|---|
| The mate writes while the chat is closed | Badge with the count on the button; the bubble shows the first sentence (at most 80 characters). |
| Tap the button or the bubble | Your click listener runs (show your chat screen); the chat opens at the new message; badge and bubble clear once it is seen. |
| Tap the bubble's dismiss control | The bubble hides. The message stays unread, so the badge keeps its count. A newer message brings the bubble back. |

Wording: override `busymate_launcher_label`, `busymate_launcher_unread` (plurals), `busymate_teaser_title`, `busymate_teaser_open` and `busymate_teaser_dismiss` in your app's resources to translate or change the texts.

## Your own UI

```kotlin
// Kotlin: a StateFlow<BusymateUnread>(count, preview, at)
lifecycleScope.launch { Busymate.unread.collect { badge.text = it.badgeText } }
Busymate.teaser   // StateFlow<BusymateUnread?>: the message to tease, null when dismissed or read
```

```java
// Java
Busymate.addOnUnreadListener(unread -> badge.setText(unread.getBadgeText()));   // main thread, current value at once
Busymate.addOnTeaserListener(teaser -> bubble.setVisibility(teaser == null ? View.GONE : View.VISIBLE));
```

Call `Busymate.openChat()` just before you show the chat from your own badge or bubble (the built-in components do it for you), and `Busymate.dismissTeaser()` from your own dismiss control.

## API

| Member | What it does |
|---|---|
| `Busymate.init(context, config)` | Starts the SDK. Checks the server now and on every app foreground (`ProcessLifecycleOwner` `ON_START`). |
| `Busymate.attach(webView)` / `detach(webView)` | Connects or disconnects a chat WebView. Main thread, before `loadUrl`. |
| `Busymate.unread` | `StateFlow<BusymateUnread>`; `BusymateUnread(count, preview, at)`, plus `hasUnread` and `badgeText` ("99+" above 99). |
| `Busymate.teaser` | `StateFlow<BusymateUnread?>` for the bubble. |
| `OnUnreadListener`, `OnTeaserListener` | The same for Java and callback code: `addOnUnreadListener` / `removeOnUnreadListener`, `addOnTeaserListener` / `removeOnTeaserListener`. |
| `Busymate.refreshUnread()` | Asks the server now. Runs by itself on init, on app foreground and when the chat screen closes; call it after your own push notification. |
| `Busymate.openChat()` | The person opens the chat from a badge or bubble: hides the bubble and tells the chat to show the conversation at the new message. |
| `Busymate.dismissTeaser()` | Hides the bubble; the message stays unread. |
| `Busymate.reset()` | Your user signed out: deletes this device's visitor token and clears the badge. |

## How it works

- **Chat to app.** `Busymate.attach` registers a `WebViewCompat.addWebMessageListener` named `BusymateEvents`, and also `BusymateBridge` when that name is free (the kit v2 identity pipe uses the same name and keeps it; that is why `attach` goes after `BusymateBridge.install`). The chat posts two messages: `support.chat.v1.unread` `{n, t, at}` (count, first sentence, time) and `support.chat.v1.identity` `{v}` (an opaque visitor token for this workspace; no personal data).
- **App to chat.** `Busymate.openChat()` sends `support.chat.v1.open` to the chat, at once or as soon as the chat has loaded.
- **With the WebView closed.** `refreshUnread()` sends `POST <chat origin>/api/chat/unread` with `{"a": "<assistant>", "v": "<token>"}`. `204` means nothing unread, `200` carries `{n, t, at}`, anything else changes nothing. Until the person has opened the chat once on this device there is no token and no request is made.
- **Security.** The listener accepts messages only from `https://busymate.ai`, `https://<assistant>.busymate.ai`, the origin of your `chatUrl` and the exact origins you list. Every message is schema-checked (at most 4 KB, known type, typed fields); anything else is dropped. The token is encrypted with a non-exportable Android Keystore key (AES-256-GCM, bound to the assistant), stored in the app's private preferences and never logged. The SDK writes nothing to Logcat.
- **Cost.** No permission, no background work, no polling: one small request on start and on each return to the foreground. `sdk` adds no UI library; Compose is only in `sdk-compose`.

## Check it on a device

1. Open the chat once and send a message (this gives the device its visitor token), then close the chat.
2. Have the mate send a follow-up (your workspace's follow-up settings, or a test follow-up from the Console).
3. Bring your app to the foreground: the button shows the badge and the bubble shows the message.
4. Tap either: the chat opens at that message and the badge clears.

The example app's main screen has both components (Compose and View), a "Show a sample message" switch to see them without a real follow-up, and in the chat screen a "Send a test follow-up signal from the page" button that posts through the real channel.

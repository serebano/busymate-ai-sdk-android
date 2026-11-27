package ai.busymate.sample

import ai.busymate.Busymate
import ai.busymate.BusymateConfig
import ai.busymate.BusymateUnread
import ai.busymate.compose.BusymateLauncherButton
import ai.busymate.compose.BusymateTeaserBubble
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.selection.toggleable
import ai.busymate.view.BusymateLauncherButton as BusymateLauncherButtonView
import ai.busymate.view.BusymateTeaserBubble as BusymateTeaserBubbleView

/** Follow-ups need the SDK before any screen exists, so the badge is right on the first frame. */
class SampleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Busymate.init(this, DemoSession.settings.busymateConfig())
    }
}

fun DemoSettings.busymateConfig() = BusymateConfig(assistant = assistant, chatUrl = chatUrl, origins = origins())

/**
 * Stands for YOUR app's main screen: the chat WebView is closed here. The chat button carries the
 * unread badge and the follow-up bubble sits above it, both fed by the SDK.
 */
class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { ExampleTheme { Home(openChat = { startActivity(Intent(this, ChatActivity::class.java)) }) } }
    }
}

@Composable
private fun ExampleTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

private val sample = BusymateUnread(2, "Your order has shipped. Want the tracking link?", "2026-11-27T09:41:05Z")

@Composable
private fun Home(openChat: () -> Unit) {
    val unread by Busymate.unread.collectAsState()
    val teaser by Busymate.teaser.collectAsState()
    var views by rememberSaveable { mutableStateOf(false) }
    var showSample by rememberSaveable { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 200.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Busymate SDK example", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "This screen stands for your app's main screen. The chat is closed here. When the mate writes while " +
                        "you are away, the chat button below gets a badge and the message appears above it.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Follow-ups", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (unread.hasUnread) "Unread: ${unread.count}" + (unread.preview?.let { "\n“$it”" } ?: "") else "Nothing unread.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = openChat) { Text("Open chat") }
                            OutlinedButton(onClick = { Busymate.refreshUnread() }) { Text("Check now") }
                        }
                        Toggle("Use the View components", views) { views = it }
                        Toggle("Show a sample message", showSample) { showSample = it }
                    }
                }
            }
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (views) {
                    // The same two components for apps built with Views (XML layouts work the same way).
                    AndroidView(
                        factory = { context ->
                            BusymateTeaserBubbleView(context).apply {
                                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                                setOnClickListener { openChat() }
                            }
                        },
                        update = { it.sample = if (showSample) sample else null },
                    )
                    AndroidView(
                        factory = { context -> BusymateLauncherButtonView(context).apply { setOnClickListener { openChat() } } },
                        update = { it.sample = if (showSample) sample else null },
                    )
                } else if (showSample) {
                    var dismissed by rememberSaveable { mutableStateOf(false) }
                    BusymateTeaserBubble(onClick = openChat, teaser = sample.takeUnless { dismissed }, onDismiss = { dismissed = true })
                    BusymateLauncherButton(onClick = openChat, unread = sample)
                } else {
                    BusymateTeaserBubble(onClick = openChat, teaser = teaser)
                    BusymateLauncherButton(onClick = openChat, unread = unread)
                }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

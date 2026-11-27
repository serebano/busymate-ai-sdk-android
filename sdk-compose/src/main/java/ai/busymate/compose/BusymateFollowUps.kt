package ai.busymate.compose

import ai.busymate.Busymate
import ai.busymate.BusymateUnread
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.busymate.sdk.R as SdkR

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/**
 * The chat button for your main screen: a Material 3 floating action button with a count badge that
 * follows [Busymate.unread].
 *
 *     BusymateLauncherButton(onClick = { navController.navigate("chat") })
 *
 * A tap tells the chat to open at the new message ([Busymate.openChat]) and then calls [onClick],
 * where you show your chat screen. Colours and shapes come from your [MaterialTheme]; the badge
 * scales with the system font size; TalkBack reads "Chat, 2 unread messages".
 *
 * @param unread the value to show; by default collected from [Busymate.unread].
 * @param icon your own icon; the default is a chat bubble.
 */
@Composable
fun BusymateLauncherButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unread: BusymateUnread = Busymate.unread.collectAsState().value,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = contentColorFor(containerColor),
    shape: Shape = FloatingActionButtonDefaults.shape,
    icon: @Composable () -> Unit = { Icon(painterResource(SdkR.drawable.busymate_ic_chat), contentDescription = null) },
) {
    val label = if (unread.hasUnread) {
        pluralStringResource(SdkR.plurals.busymate_launcher_unread, unread.count, unread.count)
    } else stringResource(SdkR.string.busymate_launcher_label)
    BadgedBox(
        modifier = modifier,
        badge = {
            AnimatedVisibility(
                visible = unread.hasUnread,
                enter = scaleIn(tween(220)) + fadeIn(tween(120)),
                exit = scaleOut(tween(120)) + fadeOut(tween(120)),
            ) {
                // The count is part of the button's own description, so the badge itself is silent.
                Badge(modifier = Modifier.clearAndSetSemantics { }) { Text(remember(unread.count) { unread.badgeText }.ifEmpty { " " }) }
            }
        },
    ) {
        FloatingActionButton(
            onClick = { Busymate.openChat(); onClick() },
            modifier = Modifier.semantics { contentDescription = label },
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            content = icon,
        )
    }
}

/**
 * The follow-up message bubble for your main screen: the first sentence of the mate's new message
 * with a dismiss control. Place it next to [BusymateLauncherButton]; it takes no space when there is
 * nothing to show.
 *
 *     BusymateTeaserBubble(onClick = { navController.navigate("chat") })
 *
 * A tap tells the chat to open at that message ([Busymate.openChat]) and then calls [onClick].
 * Dismissing hides the bubble and leaves the message unread, so the badge keeps its count. TalkBack
 * announces the message when it appears.
 *
 * @param teaser the message to show, or null; by default collected from [Busymate.teaser].
 * @param onDismiss by default [Busymate.dismissTeaser].
 */
@Composable
fun BusymateTeaserBubble(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    teaser: BusymateUnread? = Busymate.teaser.collectAsState().value,
    onDismiss: () -> Unit = { Busymate.dismissTeaser() },
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    shape: Shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
) {
    // Keeps the last message on screen while the bubble animates away.
    var last by remember { mutableStateOf(teaser) }
    if (teaser != null) last = teaser
    AnimatedVisibility(
        visible = teaser != null,
        modifier = modifier,
        enter = fadeIn(tween(200)) + slideInVertically(tween(300, easing = EmphasizedDecelerate)) { it / 3 } +
            scaleIn(tween(300, easing = EmphasizedDecelerate), initialScale = 0.92f, transformOrigin = TransformOrigin(1f, 1f)),
        exit = fadeOut(tween(150)),
    ) {
        val message = last?.preview.orEmpty()
        val openLabel = stringResource(SdkR.string.busymate_teaser_open)
        Surface(
            onClick = { Busymate.openChat(); onClick() },
            modifier = Modifier
                .widthIn(max = 320.dp)
                .semantics { liveRegion = LiveRegionMode.Polite; onClick(label = openLabel, action = null) },
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            tonalElevation = 0.dp,
            shadowElevation = 3.dp,
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f, fill = false).padding(start = 16.dp, top = 12.dp, bottom = 12.dp)) {
                    Text(
                        text = stringResource(SdkR.string.busymate_teaser_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = message,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(SdkR.drawable.busymate_ic_close),
                        contentDescription = stringResource(SdkR.string.busymate_teaser_dismiss),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

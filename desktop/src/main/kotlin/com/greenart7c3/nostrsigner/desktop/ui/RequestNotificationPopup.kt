package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.greenart7c3.nostrsigner.desktop.core.AmberLogger
import com.greenart7c3.nostrsigner.desktop.core.BuildVariant
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.SignerType
import com.greenart7c3.nostrsigner.desktop.core.Strings
import com.sun.jna.Native
import java.awt.GraphicsEnvironment
import java.awt.geom.RoundRectangle2D
import javax.swing.JWindow
import kotlinx.coroutines.delay

private const val POPUP_TIMEOUT_MS = 60_000L

/** Transparent margin around the card so its drop shadow isn't clipped by the window. */
private val SHADOW_PADDING = 16.dp

/** Distance between the card and the screen edges, like the OS's own notifications. */
private val SCREEN_MARGIN = 4.dp

/** Room left for a Wayland top bar (waybar, quickshell, …) the popup cannot see. */
private val LINUX_TOP_BAR = 32.dp

private val isMac = System.getProperty("os.name").lowercase().contains("mac")
private val isWindows = System.getProperty("os.name").lowercase().contains("win")
private val isLinux = !isMac && !isWindows

/** One button on the popup. */
private class PopupAction(val label: String, val onClick: () -> Unit)

/**
 * Amber's own notification for an incoming request, with Approve / Reject
 * buttons, drawn to look like the OS's native notification: a Windows 11
 * toast (bottom-right) or a macOS alert-style notification (top-right). Used
 * on Windows and macOS, where the AWT tray notification cannot carry action
 * buttons, and on Linux daemons that never draw action buttons (toast layout,
 * top-right; daemons that do draw them get the native notification, see
 * [com.greenart7c3.nostrsigner.desktop.core.Notifier.linuxDaemonShowsButtons]).
 *
 * It follows the OS light/dark appearance, never takes focus, opens Amber when
 * its body is clicked, and closes itself after [POPUP_TIMEOUT_MS] unless the
 * pointer rests on it. Connect requests only get Reject: approving one needs
 * the permission choices on its card.
 */
@Composable
fun RequestNotificationPopup(
    request: PendingBunkerRequest,
    title: String,
    message: String,
    language: String,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cardSize = if (isMac) DpSize(344.dp, 86.dp) else DpSize(364.dp, 160.dp)
    val windowSize = DpSize(cardSize.width + SHADOW_PADDING * 2, cardSize.height + SHADOW_PADDING * 2)
    val position = remember {
        val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        val margin = SCREEN_MARGIN.value.toInt()
        val x = bounds.x + bounds.width - windowSize.width.value.toInt() - margin
        // Where the OS shows its own notifications: above the taskbar on
        // Windows, under the menu bar / top panel on macOS and Linux.
        // Wayland compositors give XWayland no work area, so leave room for
        // a top bar when the reported one starts at the screen edge.
        val top = if (isLinux && bounds.y == 0) LINUX_TOP_BAR.value.toInt() else bounds.y
        val y = if (isWindows) bounds.y + bounds.height - windowSize.height.value.toInt() - margin else top + margin
        WindowPosition(x.dp, y.dp)
    }
    val state = rememberWindowState(size = windowSize, position = position)

    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    // Restarts while hovered, so the popup never vanishes under the pointer.
    LaunchedEffect(request.request.id, hovered) {
        if (!hovered) {
            delay(POPUP_TIMEOUT_MS)
            onDismiss()
        }
    }

    val actions = buildList {
        if (request.type != SignerType.CONNECT) add(PopupAction(Strings.get("d_approve", language), onApprove))
        add(PopupAction(Strings.get("reject", language), onReject))
    }

    val content: @Composable (Dp) -> Unit = { margin ->
        val dark = isSystemInDarkTheme()
        val appear = remember { MutableTransitionState(false).apply { targetState = true } }
        Box(Modifier.fillMaxSize().padding(margin)) {
            AnimatedVisibility(
                visibleState = appear,
                enter = fadeIn() + slideInHorizontally { it / 3 },
            ) {
                if (isMac) {
                    MacNotification(title, message, actions, dark, hover, onOpen, onDismiss, language)
                } else {
                    WindowsToast(title, message, actions, dark, hover, onOpen, onDismiss, language)
                }
            }
        }
    }

    if (isLinux) {
        // Compose windows are JFrames, which X11 always hands to the window
        // manager: Hyprland & co. would border, and focus, them as a normal
        // window. A plain AWT POPUP JWindow is override-redirect instead —
        // never managed, framed or focused, like a real notification surface —
        // so host the card in one through a ComposePanel. AWT still types it
        // NORMAL, which Hyprland focuses on map, hence markAsX11Popup.
        // Without a
        // transparent margin there is no drop shadow; the shape rounds it.
        val currentContent by rememberUpdatedState(content)
        DisposableEffect(Unit) {
            val pad = SHADOW_PADDING.value.toInt()
            val width = cardSize.width.value.toInt()
            val height = cardSize.height.value.toInt()
            val popup = JWindow().apply {
                type = java.awt.Window.Type.POPUP
                focusableWindowState = false
                // Never take keyboard focus from what the user is typing in.
                isAutoRequestFocus = false
                isAlwaysOnTop = true
                setBounds(position.x.value.toInt() + pad, position.y.value.toInt() + pad, width, height)
                shape = RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), 16.0, 16.0)
                contentPane.add(ComposePanel().apply { setContent { currentContent(0.dp) } })
                // Create the native window first so it can be typed before it maps.
                addNotify()
                markAsX11Popup(this)
                isVisible = true
            }
            onDispose { popup.dispose() }
        }
    } else {
        Window(
            onCloseRequest = onDismiss,
            state = state,
            title = BuildVariant.appName,
            icon = painterResource("icon.png"),
            undecorated = true,
            transparent = true,
            resizable = false,
            focusable = false,
            alwaysOnTop = true,
        ) { content(SHADOW_PADDING) }
    }
}

/** Windows 11 toast: app header with close button, title and body, then a row of equal-width buttons. */
@Composable
private fun WindowsToast(
    title: String,
    message: String,
    actions: List<PopupAction>,
    dark: Boolean,
    hover: MutableInteractionSource,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    language: String,
) {
    val background = if (dark) Color(0xFF2C2C2C) else Color(0xFFF3F3F3)
    val outline = if (dark) Color(0x1FFFFFFF) else Color(0x14000000)
    val primary = if (dark) Color(0xFFFFFFFF) else Color(0xE4000000)
    val secondary = if (dark) Color(0xC8FFFFFF) else Color(0x9E000000)
    val buttonFill = if (dark) Color(0x0FFFFFFF) else Color(0xB3FFFFFF)
    val buttonOutline = if (dark) Color(0x17FFFFFF) else Color(0x0F000000)
    val shape = RoundedCornerShape(8.dp)

    Column(
        Modifier
            .fillMaxSize()
            .shadow(12.dp, shape)
            .clip(shape)
            .background(background)
            .border(BorderStroke(1.dp, outline), shape)
            .hoverable(hover),
    ) {
        Column(Modifier.fillMaxWidth().weight(1f).clickable(onClick = onOpen)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource("icon.png"), contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(BuildVariant.appName, color = secondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                CloseGlyph(secondary, Strings.get("d_close", language), Modifier.size(32.dp), onDismiss)
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(title, color = primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(message, color = secondary, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            actions.forEach { action ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(buttonFill)
                        .border(BorderStroke(1.dp, buttonOutline), RoundedCornerShape(4.dp))
                        .clickable(onClick = action.onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(action.label, color = primary, fontSize = 14.sp, maxLines = 1)
                }
            }
        }
    }
}

/**
 * macOS alert-style notification: app icon, bold title and body on the left,
 * the actions stacked on the right behind a hairline, and the round close
 * button on the top-left corner while hovered.
 */
@Composable
private fun MacNotification(
    title: String,
    message: String,
    actions: List<PopupAction>,
    dark: Boolean,
    hover: MutableInteractionSource,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    language: String,
) {
    val background = if (dark) Color(0xF2323232) else Color(0xF5F6F6F6)
    val outline = if (dark) Color(0x24FFFFFF) else Color(0x1A000000)
    val primary = if (dark) Color(0xFFFFFFFF) else Color(0xD9000000)
    val secondary = if (dark) Color(0xB3FFFFFF) else Color(0x99000000)
    val divider = if (dark) Color(0x1FFFFFFF) else Color(0x1A000000)
    val shape = RoundedCornerShape(18.dp)
    val hovered by hover.collectIsHoveredAsState()

    Box(Modifier.fillMaxSize().hoverable(hover)) {
        Row(
            Modifier
                .fillMaxSize()
                .shadow(10.dp, shape)
                .clip(shape)
                .background(background)
                .border(BorderStroke(1.dp, outline), shape),
        ) {
            Row(
                Modifier.weight(1f).fillMaxHeight().clickable(onClick = onOpen).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource("icon.png"), contentDescription = null, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, color = primary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(message, color = primary, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(divider))
            Column(Modifier.width(86.dp).fillMaxHeight()) {
                actions.forEachIndexed { index, action ->
                    if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(divider))
                    Box(
                        Modifier.fillMaxWidth().weight(1f).clickable(onClick = action.onClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(action.label, color = primary, fontSize = 13.sp, textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
        }
        if (hovered) {
            CloseGlyph(
                secondary,
                Strings.get("d_close", language),
                Modifier
                    .offset((-6).dp, (-6).dp)
                    .size(20.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(background)
                    .border(BorderStroke(1.dp, outline), CircleShape),
                onDismiss,
                fontSize = 11,
            )
        }
    }
}

@Composable
private fun CloseGlyph(color: Color, label: String, modifier: Modifier, onClick: () -> Unit, fontSize: Int = 14) {
    Box(
        modifier.clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text("✕", color = color, fontSize = fontSize.sp)
    }
}

/**
 * Makes AWT announce the popup as `_NET_WM_WINDOW_TYPE_POPUP_MENU`. AWT writes
 * the X11 window type from its peer right before mapping the window, and on
 * XWayland that peer still says NORMAL for a POPUP window — which compositors
 * such as Hyprland focus when it maps, taking focus from whatever the user is
 * typing in. Setting the peer's own type fixes what AWT writes. Needs
 * `--add-opens=java.desktop/sun.awt.X11` (set in build.gradle.kts) and must
 * run after addNotify, before the window is shown.
 */
private fun markAsX11Popup(window: java.awt.Window) {
    runCatching {
        val toXWindow = Class.forName("sun.awt.X11.XToolkit").getDeclaredMethod("windowToXWindow", Long::class.javaPrimitiveType)
        toXWindow.isAccessible = true
        val peer = toXWindow.invoke(null, Native.getWindowID(window)) ?: return
        Class.forName("sun.awt.X11.XWindowPeer").getDeclaredField("windowType").apply {
            isAccessible = true
            set(peer, java.awt.Window.Type.POPUP)
        }
    }.onFailure { AmberLogger.e("RequestNotificationPopup", "could not set the X11 window type", it) }
}

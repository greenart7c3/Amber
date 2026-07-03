package com.greenart7c3.nostrsigner.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AccountsStore
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.Notifier
import com.greenart7c3.nostrsigner.desktop.core.PassphraseLock
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import com.greenart7c3.nostrsigner.desktop.core.Strings
import com.greenart7c3.nostrsigner.desktop.core.describe
import com.greenart7c3.nostrsigner.desktop.ui.App
import com.greenart7c3.nostrsigner.desktop.ui.NostrSignerTheme
import com.greenart7c3.nostrsigner.desktop.ui.handleShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Desktop counterpart of `AccountStateViewModel`: which account is active. */
object Session {
    val account = MutableStateFlow<DesktopAccount?>(null)
    val loading = MutableStateFlow(true)
    val addingAccount = MutableStateFlow(false)

    fun boot() {
        AmberDesktop.applicationIOScope.launch {
            var engineStarted = false
            PassphraseLock.state.collect { status ->
                when (status) {
                    PassphraseLock.Status.LOCKED -> {
                        // Key material is evicted; drop the account reference so
                        // nothing in the UI can reach a decrypted signer.
                        account.value = null
                        loading.value = false
                    }

                    PassphraseLock.Status.DISABLED, PassphraseLock.Status.UNLOCKED -> {
                        val saved = AmberDesktop.settings.currentAccount
                        val npub = saved.ifBlank { AccountsStore.accounts.value.firstOrNull()?.npub ?: "" }
                        if (npub.isNotBlank()) {
                            account.value = AmberDesktop.account(npub)
                        }
                        loading.value = false
                        if (engineStarted) {
                            AmberDesktop.engine.updateFilter()
                            AmberDesktop.client.connect()
                        } else {
                            AmberDesktop.engine.start()
                            engineStarted = true
                        }
                    }
                }
            }
        }
    }

    suspend fun switchTo(npub: String) {
        SettingsStore.update { it.copy(currentAccount = npub) }
        account.value = AmberDesktop.account(npub)
        addingAccount.value = false
    }

    fun onAccountAdded(newAccount: DesktopAccount) {
        account.value = newAccount
        addingAccount.value = false
        AmberDesktop.applicationIOScope.launch {
            AmberDesktop.engine.updateFilter()
            AmberDesktop.engine.client.connect()
        }
    }

    suspend fun logout(npub: String) {
        AmberDesktop.engine.pending.value = AmberDesktop.engine.pending.value.filter { it.account.npub != npub }
        AmberDesktop.store(npub).deleteAllFiles()
        AccountsStore.delete(npub)
        AmberDesktop.evictAccount(npub)
        val next = AccountsStore.accounts.value.firstOrNull()?.npub ?: ""
        SettingsStore.update { it.copy(currentAccount = next) }
        account.value = if (next.isBlank()) null else AmberDesktop.account(next)
        AmberDesktop.engine.updateFilter()
    }

    fun saveMeta(acc: DesktopAccount) {
        AccountManager.saveAccountMeta(acc)
    }
}

fun main() {
    Session.boot()

    application {
        val windowState = rememberWindowState(size = DpSize(1100.dp, 780.dp))
        val pending by AmberDesktop.engine.pending.collectAsState()
        val settings by SettingsStore.settings.collectAsState()
        val language by Strings.currentLanguage.collectAsState()
        val lockStatus by PassphraseLock.state.collectAsState()
        // Window visibility is a flow so the native tray's menu callbacks (which
        // fire on the tray library's own thread) can toggle it safely.
        val windowVisibleState = remember { MutableStateFlow(true) }
        val windowVisible by windowVisibleState.collectAsState()
        val trayState = rememberTrayState()

        val isLinux = remember {
            System.getProperty("os.name").lowercase().let { it.contains("linux") || it.contains("nix") || it.contains("nux") }
        }
        // AWT's tray only works with an XEmbed host, which excludes Wayland; on
        // Linux we use the dorkbox tray (StatusNotifierItem/AppIndicator)
        // instead, and keep AWT for Windows/macOS where it works well.
        val awtTrayUsable = remember {
            !isLinux && isTraySupported && runCatching { java.awt.SystemTray.getSystemTray() }.isSuccess
        }
        val nativeTray = remember {
            // Escape hatch for environments where the native GTK/tray stack
            // misbehaves: set AMBER_DISABLE_TRAY to skip it (the app then just
            // exits on window close, and notifications still work).
            if (isLinux && System.getenv("AMBER_DISABLE_TRAY") == null) {
                NativeTray.create(
                    iconStream = { NativeTray::class.java.getResourceAsStream("/icon.png") },
                    tooltip = Strings.get("d_tray_tooltip", language),
                    openLabel = Strings.get("d_tray_open", language),
                    lockLabel = Strings.get("d_lock_now", language),
                    quitLabel = Strings.get("d_tray_quit", language),
                    onToggle = { windowVisibleState.value = !windowVisibleState.value },
                    onLock = { PassphraseLock.lock() },
                    onQuit = { java.awt.EventQueue.invokeLater { exitApplication() } },
                )
            } else {
                null
            }
        }
        DisposableEffect(Unit) { onDispose { nativeTray?.shutdown() } }
        LaunchedEffect(pending.size, windowVisible, lockStatus, language) {
            nativeTray?.update(
                tooltip = if (pending.isEmpty()) Strings.get("d_tray_tooltip", language) else Strings.format("d_tray_pending", pending.size, language = language),
                openLabel = if (windowVisible) Strings.get("d_tray_hide", language) else Strings.get("d_tray_open", language),
                lockLabel = Strings.get("d_lock_now", language),
                showLock = lockStatus == PassphraseLock.Status.UNLOCKED,
            )
        }

        val trayUsable = awtTrayUsable || nativeTray != null
        val trayActive = trayUsable && settings.closeToTray

        // Notify and surface the window whenever a new approval request arrives.
        var seenPending by remember { mutableStateOf(0) }
        LaunchedEffect(pending.size) {
            if (pending.size > seenPending) {
                val request = pending.last()
                if (settings.showNotifications) {
                    val message = "${request.appName} ${request.type.describe(request.kind, language)}"
                    // Prefer the OS-native notification channel (freedesktop /
                    // notify-send on Linux, incl. Wayland/Hyprland; osascript on
                    // macOS). Only fall back to the AWT tray notification — which
                    // needs a usable system tray — when there is no native channel.
                    val delivered = withContext(Dispatchers.IO) { Notifier.notify("Amber", message) }
                    if (!delivered && awtTrayUsable) {
                        trayState.sendNotification(
                            Notification(
                                title = "Amber",
                                message = message,
                                type = Notification.Type.Info,
                            ),
                        )
                    }
                }
                windowVisibleState.value = true
            }
            seenPending = pending.size
        }

        if (awtTrayUsable) {
            Tray(
                icon = painterResource("icon.png"),
                state = trayState,
                tooltip = if (pending.isEmpty()) Strings.get("d_tray_tooltip", language) else Strings.format("d_tray_pending", pending.size, language = language),
                onAction = { windowVisibleState.value = true },
                menu = {
                    Item(
                        if (windowVisible) Strings.get("d_tray_hide", language) else Strings.get("d_tray_open", language),
                        onClick = { windowVisibleState.value = !windowVisibleState.value },
                    )
                    if (lockStatus == PassphraseLock.Status.UNLOCKED) {
                        Item(Strings.get("d_lock_now", language), onClick = { PassphraseLock.lock() })
                    }
                    Separator()
                    Item(Strings.get("d_tray_quit", language), onClick = ::exitApplication)
                },
            )
        }

        Window(
            onCloseRequest = {
                if (trayActive) {
                    windowVisibleState.value = false
                } else {
                    exitApplication()
                }
            },
            state = windowState,
            visible = windowVisible,
            title = if (pending.isEmpty()) "Amber" else "Amber (${pending.size})",
            icon = painterResource("icon.png"),
            onPreviewKeyEvent = { event ->
                handleShortcut(
                    event,
                    hideWindow = {
                        if (trayActive) windowVisibleState.value = false else exitApplication()
                    },
                    quit = ::exitApplication,
                )
            },
        ) {
            NostrSignerTheme {
                App()
            }
        }
    }
}

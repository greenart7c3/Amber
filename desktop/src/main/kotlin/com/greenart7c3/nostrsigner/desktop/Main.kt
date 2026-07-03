package com.greenart7c3.nostrsigner.desktop

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
import com.greenart7c3.nostrsigner.desktop.core.PassphraseLock
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import com.greenart7c3.nostrsigner.desktop.core.describe
import com.greenart7c3.nostrsigner.desktop.ui.App
import com.greenart7c3.nostrsigner.desktop.ui.NostrSignerTheme
import com.greenart7c3.nostrsigner.desktop.ui.handleShortcut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
        var windowVisible by remember { mutableStateOf(true) }
        val trayState = rememberTrayState()
        // AWT may claim support but fail to actually add the icon (e.g. bare
        // X servers without a notification area); treat that as unsupported.
        val trayUsable = remember { isTraySupported && runCatching { java.awt.SystemTray.getSystemTray() }.isSuccess }
        val trayActive = trayUsable && settings.closeToTray

        // Notify and surface the window whenever a new approval request arrives.
        var seenPending by remember { mutableStateOf(0) }
        LaunchedEffect(pending.size) {
            if (pending.size > seenPending) {
                val request = pending.last()
                if (settings.showNotifications && trayUsable) {
                    trayState.sendNotification(
                        Notification(
                            title = "Amber",
                            message = "${request.appName} ${request.type.describe(request.kind)}",
                            type = Notification.Type.Info,
                        ),
                    )
                }
                windowVisible = true
            }
            seenPending = pending.size
        }

        if (trayUsable) {
            val lockStatus by PassphraseLock.state.collectAsState()
            Tray(
                icon = painterResource("icon.png"),
                state = trayState,
                tooltip = if (pending.isEmpty()) "Amber — Nostr signer" else "Amber — ${pending.size} pending request(s)",
                onAction = { windowVisible = true },
                menu = {
                    Item(
                        if (windowVisible) "Hide Amber" else "Open Amber",
                        onClick = { windowVisible = !windowVisible },
                    )
                    if (lockStatus == PassphraseLock.Status.UNLOCKED) {
                        Item("Lock now", onClick = { PassphraseLock.lock() })
                    }
                    Separator()
                    Item("Quit Amber", onClick = ::exitApplication)
                },
            )
        }

        Window(
            onCloseRequest = {
                if (trayActive) {
                    windowVisible = false
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
                        if (trayActive) windowVisible = false else exitApplication()
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

package com.greenart7c3.nostrsigner.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AccountsStore
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import com.greenart7c3.nostrsigner.desktop.ui.App
import com.greenart7c3.nostrsigner.desktop.ui.NostrSignerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Desktop counterpart of `AccountStateViewModel`: which account is active. */
object Session {
    val account = MutableStateFlow<DesktopAccount?>(null)
    val loading = MutableStateFlow(true)
    val addingAccount = MutableStateFlow(false)

    fun boot() {
        AmberDesktop.applicationIOScope.launch {
            val saved = AmberDesktop.settings.currentAccount
            val npub = saved.ifBlank { AccountsStore.accounts.value.firstOrNull()?.npub ?: "" }
            if (npub.isNotBlank()) {
                account.value = AmberDesktop.account(npub)
            }
            loading.value = false
            AmberDesktop.engine.start()
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

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = if (pending.isEmpty()) "Amber" else "Amber (${pending.size})",
            icon = painterResource("icon.png"),
        ) {
            NostrSignerTheme {
                App()
            }
        }
    }
}

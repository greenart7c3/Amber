package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.account
import com.greenart7c3.nostrsigner.commonsui.accounts
import com.greenart7c3.nostrsigner.commonsui.auth_whitelist
import com.greenart7c3.nostrsigner.commonsui.auth_whitelist_description
import com.greenart7c3.nostrsigner.commonsui.connect_via_tor_short
import com.greenart7c3.nostrsigner.commonsui.d_desktop
import com.greenart7c3.nostrsigner.commonsui.d_diagnostics
import com.greenart7c3.nostrsigner.commonsui.d_security_keys_desc
import com.greenart7c3.nostrsigner.commonsui.d_sign_policy
import com.greenart7c3.nostrsigner.commonsui.d_sign_policy_sub
import com.greenart7c3.nostrsigner.commonsui.d_theme
import com.greenart7c3.nostrsigner.commonsui.d_view_logs
import com.greenart7c3.nostrsigner.commonsui.language
import com.greenart7c3.nostrsigner.commonsui.security
import com.greenart7c3.nostrsigner.commonsui.tor_subtitle
import com.greenart7c3.nostrsigner.desktop.Session
import com.greenart7c3.nostrsigner.desktop.core.AccountsStore
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.DesktopMasterKey
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(account: DesktopAccount, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val settings by SettingsStore.settings.collectAsState()
    val accounts by AccountsStore.accounts.collectAsState()
    var showBackupDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var logoutNpub by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    ScrollbarBox(rememberScrollbarAdapter(scrollState), modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            SettingsSection(stringResource(Res.string.account)) { AccountSection(account) { showBackupDialog = true } }
            SettingsSection(stringResource(Res.string.d_sign_policy), stringResource(Res.string.d_sign_policy_sub)) { SignPolicySection(account) }
            SettingsSection(stringResource(Res.string.d_theme)) { ThemeSection(settings.darkTheme) }
            SettingsSection(stringResource(Res.string.language)) { LanguagePicker() }
            SettingsSection(stringResource(Res.string.d_desktop)) { DesktopSection(settings) }
            SettingsSection(stringResource(Res.string.connect_via_tor_short), stringResource(Res.string.tor_subtitle)) { TorSection() }
            SettingsSection(stringResource(Res.string.auth_whitelist), stringResource(Res.string.auth_whitelist_description)) { AuthWhitelistSection() }
            SettingsSection(stringResource(Res.string.accounts)) { AccountsSection(accounts, account.npub) { logoutNpub = it } }
            SettingsSection(
                stringResource(Res.string.security),
                stringResource(Res.string.d_security_keys_desc, DesktopMasterKey.passwordSourceDescription ?: "…"),
            ) { SecuritySection() }
            SettingsSection(stringResource(Res.string.d_diagnostics)) {
                AmberOutlinedButton(text = stringResource(Res.string.d_view_logs), onClick = { showLogsDialog = true })
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showBackupDialog) BackupDialog(account = account, onDismiss = { showBackupDialog = false })
    if (showLogsDialog) LogsDialog(account = account, onDismiss = { showLogsDialog = false })
    logoutNpub?.let { npub ->
        LogoutConfirmDialog(
            onConfirm = {
                logoutNpub = null
                scope.launch { Session.logout(npub) }
            },
            onDismiss = { logoutNpub = null },
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalLayoutWidth.current == LayoutWidth.EXPANDED) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 20.dp)) {
                Column(Modifier.width(240.dp).padding(end = 32.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (description != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
            }
            HorizontalDivider()
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(4.dp))
            }
            content()
        }
    }
}

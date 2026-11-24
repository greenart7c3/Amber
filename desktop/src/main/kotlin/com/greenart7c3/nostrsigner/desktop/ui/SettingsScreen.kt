package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.isTraySupported
import com.greenart7c3.nostrsigner.common.relayHostAndPort
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.account
import com.greenart7c3.nostrsigner.commonsui.accounts
import com.greenart7c3.nostrsigner.commonsui.add
import com.greenart7c3.nostrsigner.commonsui.add_relay_to_whitelist
import com.greenart7c3.nostrsigner.commonsui.auth_whitelist
import com.greenart7c3.nostrsigner.commonsui.auth_whitelist_description
import com.greenart7c3.nostrsigner.commonsui.backup_keys
import com.greenart7c3.nostrsigner.commonsui.builtin_tor_active
import com.greenart7c3.nostrsigner.commonsui.builtin_tor_description
import com.greenart7c3.nostrsigner.commonsui.builtin_tor_title
import com.greenart7c3.nostrsigner.commonsui.cancel
import com.greenart7c3.nostrsigner.commonsui.connect_via_tor_short
import com.greenart7c3.nostrsigner.commonsui.d_active
import com.greenart7c3.nostrsigner.commonsui.d_add_an_account
import com.greenart7c3.nostrsigner.commonsui.d_change_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_change_the_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_clear
import com.greenart7c3.nostrsigner.commonsui.d_close
import com.greenart7c3.nostrsigner.commonsui.d_copy_nsec
import com.greenart7c3.nostrsigner.commonsui.d_copy_nsec_warning
import com.greenart7c3.nostrsigner.commonsui.d_current_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_desktop
import com.greenart7c3.nostrsigner.commonsui.d_diagnostics
import com.greenart7c3.nostrsigner.commonsui.d_encrypt_and_copy
import com.greenart7c3.nostrsigner.commonsui.d_encrypted_backup
import com.greenart7c3.nostrsigner.commonsui.d_encrypted_key_copied
import com.greenart7c3.nostrsigner.commonsui.d_failed_update_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_keep_in_tray
import com.greenart7c3.nostrsigner.commonsui.d_keep_in_tray_sub
import com.greenart7c3.nostrsigner.commonsui.d_local_bunker_port
import com.greenart7c3.nostrsigner.commonsui.d_local_bunker_port_invalid
import com.greenart7c3.nostrsigner.commonsui.d_local_bunker_server
import com.greenart7c3.nostrsigner.commonsui.d_local_bunker_server_sub
import com.greenart7c3.nostrsigner.commonsui.d_lock_15min
import com.greenart7c3.nostrsigner.commonsui.d_lock_1hour
import com.greenart7c3.nostrsigner.commonsui.d_lock_5min
import com.greenart7c3.nostrsigner.commonsui.d_lock_after
import com.greenart7c3.nostrsigner.commonsui.d_lock_never
import com.greenart7c3.nostrsigner.commonsui.d_lock_now
import com.greenart7c3.nostrsigner.commonsui.d_log_out
import com.greenart7c3.nostrsigner.commonsui.d_log_out_confirm
import com.greenart7c3.nostrsigner.commonsui.d_log_out_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_log_out_q
import com.greenart7c3.nostrsigner.commonsui.d_logs
import com.greenart7c3.nostrsigner.commonsui.d_new_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_no_logs
import com.greenart7c3.nostrsigner.commonsui.d_notifications
import com.greenart7c3.nostrsigner.commonsui.d_notifications_sub
import com.greenart7c3.nostrsigner.commonsui.d_nsec_copied
import com.greenart7c3.nostrsigner.commonsui.d_os_secret_unavailable
import com.greenart7c3.nostrsigner.commonsui.d_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_passphrase_changed
import com.greenart7c3.nostrsigner.commonsui.d_passphrase_never_stored
import com.greenart7c3.nostrsigner.commonsui.d_passphrases_no_match
import com.greenart7c3.nostrsigner.commonsui.d_password
import com.greenart7c3.nostrsigner.commonsui.d_password_required
import com.greenart7c3.nostrsigner.commonsui.d_policy_approve
import com.greenart7c3.nostrsigner.commonsui.d_policy_basic
import com.greenart7c3.nostrsigner.commonsui.d_policy_sign_all
import com.greenart7c3.nostrsigner.commonsui.d_public_key
import com.greenart7c3.nostrsigner.commonsui.d_repeat_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_saved
import com.greenart7c3.nostrsigner.commonsui.d_secret_key_nsec
import com.greenart7c3.nostrsigner.commonsui.d_security_keys_desc
import com.greenart7c3.nostrsigner.commonsui.d_seed_words
import com.greenart7c3.nostrsigner.commonsui.d_show
import com.greenart7c3.nostrsigner.commonsui.d_sign_policy
import com.greenart7c3.nostrsigner.commonsui.d_sign_policy_sub
import com.greenart7c3.nostrsigner.commonsui.d_start_on_boot
import com.greenart7c3.nostrsigner.commonsui.d_start_on_boot_sub
import com.greenart7c3.nostrsigner.commonsui.d_switch
import com.greenart7c3.nostrsigner.commonsui.d_theme
import com.greenart7c3.nostrsigner.commonsui.d_theme_dark
import com.greenart7c3.nostrsigner.commonsui.d_theme_light
import com.greenart7c3.nostrsigner.commonsui.d_theme_system
import com.greenart7c3.nostrsigner.commonsui.d_tor_external
import com.greenart7c3.nostrsigner.commonsui.d_tor_external_sub
import com.greenart7c3.nostrsigner.commonsui.d_tor_invalid_port
import com.greenart7c3.nostrsigner.commonsui.d_tor_local_note
import com.greenart7c3.nostrsigner.commonsui.d_tor_off
import com.greenart7c3.nostrsigner.commonsui.d_tor_proxy_ok
import com.greenart7c3.nostrsigner.commonsui.d_tor_socks_port
import com.greenart7c3.nostrsigner.commonsui.d_tor_stopped
import com.greenart7c3.nostrsigner.commonsui.d_use_12_chars
import com.greenart7c3.nostrsigner.commonsui.d_view_logs
import com.greenart7c3.nostrsigner.commonsui.d_working
import com.greenart7c3.nostrsigner.commonsui.d_wrong_current_passphrase
import com.greenart7c3.nostrsigner.commonsui.d_wrong_passphrase
import com.greenart7c3.nostrsigner.commonsui.do_you_really_want_to_disable_tor_text
import com.greenart7c3.nostrsigner.commonsui.do_you_really_want_to_disable_tor_title
import com.greenart7c3.nostrsigner.commonsui.failed_to_connect_to_tor_orbot
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.language
import com.greenart7c3.nostrsigner.commonsui.name
import com.greenart7c3.nostrsigner.commonsui.no
import com.greenart7c3.nostrsigner.commonsui.remove
import com.greenart7c3.nostrsigner.commonsui.save
import com.greenart7c3.nostrsigner.commonsui.security
import com.greenart7c3.nostrsigner.commonsui.tor_connecting
import com.greenart7c3.nostrsigner.commonsui.tor_connection_failed
import com.greenart7c3.nostrsigner.commonsui.tor_restart
import com.greenart7c3.nostrsigner.commonsui.tor_starting
import com.greenart7c3.nostrsigner.commonsui.tor_subtitle
import com.greenart7c3.nostrsigner.commonsui.warning
import com.greenart7c3.nostrsigner.commonsui.yes
import com.greenart7c3.nostrsigner.desktop.Session
import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AccountRecord
import com.greenart7c3.nostrsigner.desktop.core.AccountsStore
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AutoStart
import com.greenart7c3.nostrsigner.desktop.core.DateTimes
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.DesktopKeyStore
import com.greenart7c3.nostrsigner.desktop.core.DesktopSettings
import com.greenart7c3.nostrsigner.desktop.core.PassphraseLock
import com.greenart7c3.nostrsigner.desktop.core.RelayHttpClients
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import com.greenart7c3.nostrsigner.desktop.core.Strings
import com.greenart7c3.nostrsigner.desktop.core.TorManager
import com.greenart7c3.nostrsigner.desktop.core.TorMode
import com.greenart7c3.nostrsigner.desktop.core.TorStatus
import com.greenart7c3.nostrsigner.desktop.core.toShortenHex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
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
                stringResource(Res.string.d_security_keys_desc, DesktopKeyStore.passwordSourceDescription ?: "…"),
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
private fun ColumnScope.AccountSection(
    account: DesktopAccount,
    onOpenBackup: () -> Unit,
) {
    val accountName by account.name.collectAsState()
    var name by remember(account.npub, accountName) { mutableStateOf(accountName) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(Res.string.name)) },
            singleLine = true,
            modifier = Modifier.widthIn(max = 420.dp).weight(1f, fill = false),
        )
        AmberOutlinedButton(
            text = stringResource(Res.string.save),
            onClick = {
                account.name.value = name
                Session.saveMeta(account)
                Toaster.toast(getStringBlocking(Res.string.d_saved))
            },
        )
    }
    Text(
        stringResource(Res.string.d_public_key, account.npub),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    AmberButton(text = stringResource(Res.string.backup_keys), onClick = onOpenBackup)
}

@Composable
private fun SignPolicySection(account: DesktopAccount) {
    val policies = listOf(
        0 to stringResource(Res.string.d_policy_basic),
        1 to stringResource(Res.string.d_policy_approve),
        2 to stringResource(Res.string.d_policy_sign_all),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        policies.forEach { (value, label) ->
            FilterChip(
                selected = account.signPolicy == value,
                onClick = {
                    account.signPolicy = value
                    Session.saveMeta(account)
                },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun ThemeSection(darkTheme: Boolean?) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            null to stringResource(Res.string.d_theme_system),
            false to stringResource(Res.string.d_theme_light),
            true to stringResource(Res.string.d_theme_dark),
        ).forEach { (value, label) ->
            FilterChip(
                selected = darkTheme == value,
                onClick = { SettingsStore.update { it.copy(darkTheme = value) } },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun ColumnScope.DesktopSection(settings: DesktopSettings) {
    if (isTraySupported) {
        SettingSwitch(
            title = stringResource(Res.string.d_keep_in_tray),
            description = stringResource(Res.string.d_keep_in_tray_sub),
            checked = settings.closeToTray,
            onCheckedChange = { value -> SettingsStore.update { it.copy(closeToTray = value) } },
        )
    }
    SettingSwitch(
        title = stringResource(Res.string.d_notifications),
        description = stringResource(Res.string.d_notifications_sub),
        checked = settings.showNotifications,
        onCheckedChange = { value -> SettingsStore.update { it.copy(showNotifications = value) } },
    )
    if (AutoStart.isSupported()) {
        SettingSwitch(
            title = stringResource(Res.string.d_start_on_boot),
            description = stringResource(Res.string.d_start_on_boot_sub),
            checked = settings.startOnBoot,
            onCheckedChange = { value ->
                SettingsStore.update { it.copy(startOnBoot = value) }
                AutoStart.setEnabled(value)
            },
        )
    }
    SettingSwitch(
        title = stringResource(Res.string.d_local_bunker_server),
        description = stringResource(Res.string.d_local_bunker_server_sub, AmberDesktop.localBunkerPort()),
        checked = settings.localBunkerServer,
        onCheckedChange = { value ->
            SettingsStore.update { it.copy(localBunkerServer = value) }
            AmberDesktop.applyLocalBunkerServerSetting()
        },
    )
    LocalBunkerPortRow(settings.localBunkerPort)
}

@Composable
private fun LocalBunkerPortRow(configuredPort: Int?) {
    var portText by remember(configuredPort) { mutableStateOf(AmberDesktop.localBunkerPort().toString()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = portText,
            onValueChange = { portText = it.filter(Char::isDigit).take(5) },
            label = { Text(stringResource(Res.string.d_local_bunker_port)) },
            singleLine = true,
            modifier = Modifier.widthIn(max = 180.dp),
        )
        AmberOutlinedButton(
            text = stringResource(Res.string.save),
            onClick = {
                val port = portText.toIntOrNull()
                if (port == null || port !in 1024..65535) {
                    Toaster.toast(getStringBlocking(Res.string.d_local_bunker_port_invalid))
                } else {
                    val error = AmberDesktop.changeLocalBunkerPort(port)
                    Toaster.toast(getStringBlocking(error ?: Res.string.d_saved))
                }
            },
        )
    }
}

@Composable
private fun ColumnScope.AccountsSection(
    accounts: List<AccountRecord>,
    activeNpub: String,
    onLogout: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    accounts.forEach { record ->
        AccountRow(
            record = record,
            isActive = record.npub == activeNpub,
            onSwitch = { scope.launch { Session.switchTo(record.npub) } },
            onLogout = { onLogout(record.npub) },
        )
        HorizontalDivider()
    }
    Spacer(Modifier.height(8.dp))
    AmberOutlinedButton(text = stringResource(Res.string.d_add_an_account), onClick = { Session.addingAccount.value = true })
}

@Composable
private fun AccountRow(
    record: AccountRecord,
    isActive: Boolean,
    onSwitch: () -> Unit,
    onLogout: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(record.npub)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                record.name.ifBlank { record.npub.toShortenHex() },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (isActive) {
                Text(
                    stringResource(Res.string.d_active),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!isActive) {
            AmberTextButton(
                text = stringResource(Res.string.d_switch),
                onClick = onSwitch,
            )
        }
        AmberTextButton(
            text = stringResource(Res.string.d_log_out),
            onClick = onLogout,
        )
    }
}

@Composable
private fun PassphraseField(
    label: StringResource,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LogoutConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var loggingOut by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!loggingOut) onDismiss() },
        title = { Text(stringResource(Res.string.d_log_out_q)) },
        text = {
            Column {
                Text(stringResource(Res.string.d_log_out_confirm))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(Res.string.d_log_out_passphrase))
                Spacer(Modifier.height(4.dp))
                PassphraseField(
                    label = Res.string.d_passphrase,
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    enabled = !loggingOut,
                )
            }
        },
        confirmButton = {
            AmberTextButton(
                text = if (loggingOut) stringResource(Res.string.d_working) else stringResource(Res.string.d_log_out),
                enabled = !loggingOut,
                onClick = {
                    loggingOut = true
                    scope.launch {
                        try {
                            if (verifyPassphrase(passphrase)) onConfirm()
                        } finally {
                            loggingOut = false
                        }
                    }
                },
            )
        },
        dismissButton = {
            AmberTextButton(
                text = stringResource(Res.string.cancel),
                enabled = !loggingOut,
                onClick = onDismiss,
            )
        },
    )
}

private suspend fun verifyPassphrase(passphrase: String): Boolean = try {
    val verified = PassphraseLock.verify(passphrase.toCharArray())
    if (!verified) Toaster.toast(getStringBlocking(Res.string.d_wrong_passphrase))
    verified
} catch (e: PassphraseLock.OsSecretUnavailableException) {
    Toaster.toast(getStringBlocking(Res.string.d_os_secret_unavailable, e.storeDescription))
    false
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

@Composable
private fun LanguagePicker() {
    val current by Strings.currentLanguage.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    val currentName = Strings.supportedLanguages.firstOrNull { it.first == current }?.second ?: current

    Box {
        AmberOutlinedButton(text = currentName, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Strings.supportedLanguages.forEach { (tag, name) ->
                DropdownMenuItem(
                    text = { Text(name + if (tag == current) "  ✓" else "") },
                    onClick = {
                        Strings.setLanguage(tag)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ColumnScope.TorSection() {
    val settings by SettingsStore.settings.collectAsState()
    val torStatus by TorManager.status.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmDisable by remember { mutableStateOf(false) }

    TorModeChips(
        selected = settings.torMode,
        onSelect = { mode ->
            when (mode) {
                settings.torMode -> {}
                TorMode.DISABLED -> confirmDisable = true
                else -> applyTorMode(scope, mode, settings.proxyPort)
            }
        },
    )
    Spacer(Modifier.height(4.dp))
    when (settings.torMode) {
        TorMode.BUILTIN -> BuiltinTorSection(torStatus)
        TorMode.EXTERNAL -> ExternalTorSection(settings.proxyPort) { applyTorMode(scope, TorMode.EXTERNAL, it) }
        TorMode.DISABLED -> {}
    }
    Text(
        stringResource(Res.string.d_tor_local_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (confirmDisable) {
        ConfirmDisableTorDialog(
            onConfirm = {
                confirmDisable = false
                applyTorMode(scope, TorMode.DISABLED, settings.proxyPort)
            },
            onDismiss = { confirmDisable = false },
        )
    }
}

@Composable
private fun TorModeChips(
    selected: TorMode,
    onSelect: (TorMode) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            TorMode.DISABLED to stringResource(Res.string.d_tor_off),
            TorMode.BUILTIN to stringResource(Res.string.builtin_tor_title),
            TorMode.EXTERNAL to stringResource(Res.string.d_tor_external),
        ).forEach { (mode, label) ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun ColumnScope.BuiltinTorSection(torStatus: TorStatus) {
    Text(stringResource(Res.string.builtin_tor_description), style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            torStatusText(torStatus),
            style = MaterialTheme.typography.bodyMedium,
            color = if (torStatus is TorStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false),
        )
        AmberTextButton(
            text = stringResource(Res.string.tor_restart),
            onClick = { TorManager.restart(AmberDesktop.applicationIOScope) },
        )
    }
}

@Composable
private fun torStatusText(status: TorStatus): String = when (status) {
    TorStatus.Stopped -> stringResource(Res.string.d_tor_stopped)
    is TorStatus.Connecting -> if (status.percentage <= 0) {
        stringResource(Res.string.tor_starting)
    } else {
        stringResource(Res.string.tor_connecting, status.percentage)
    }
    TorStatus.Connected -> stringResource(Res.string.builtin_tor_active)
    is TorStatus.Failed -> listOfNotNull(
        stringResource(Res.string.tor_connection_failed),
        status.message?.takeIf { it.isNotBlank() },
    ).joinToString(": ")
}

@Composable
private fun ColumnScope.ExternalTorSection(
    proxyPort: Int,
    onSave: (Int) -> Unit,
) {
    var portText by remember(proxyPort) { mutableStateOf(proxyPort.toString()) }
    Text(stringResource(Res.string.d_tor_external_sub), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = portText,
            onValueChange = { value -> portText = value.filter { it.isDigit() }.take(5) },
            label = { Text(stringResource(Res.string.d_tor_socks_port)) },
            singleLine = true,
            modifier = Modifier.widthIn(max = 200.dp),
        )
        AmberOutlinedButton(
            text = stringResource(Res.string.save),
            onClick = {
                val port = portText.toIntOrNull()
                if (port == null || port !in 1..65535) {
                    Toaster.toast(getStringBlocking(Res.string.d_tor_invalid_port))
                } else {
                    onSave(port)
                }
            },
        )
    }
}

@Composable
private fun ConfirmDisableTorDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.do_you_really_want_to_disable_tor_title)) },
        text = { Text(stringResource(Res.string.do_you_really_want_to_disable_tor_text)) },
        confirmButton = {
            AmberTextButton(text = stringResource(Res.string.yes), onClick = onConfirm)
        },
        dismissButton = {
            AmberTextButton(text = stringResource(Res.string.no), onClick = onDismiss)
        },
    )
}

private fun applyTorMode(
    scope: CoroutineScope,
    mode: TorMode,
    port: Int,
) {
    SettingsStore.update { it.copy(torMode = mode, proxyPort = port) }
    AmberDesktop.applyTorSettings()
    if (mode == TorMode.EXTERNAL) {
        scope.launch {
            val alive = withContext(Dispatchers.IO) { RelayHttpClients.isSocksProxyAlive(port) }
            Toaster.toast(getStringBlocking(if (alive) Res.string.d_tor_proxy_ok else Res.string.failed_to_connect_to_tor_orbot))
        }
    }
}

@Composable
private fun ColumnScope.SecuritySection() {
    val settings by SettingsStore.settings.collectAsState()
    var showChangeDialog by remember { mutableStateOf(false) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AmberOutlinedButton(
            text = stringResource(Res.string.d_lock_now),
            onClick = { PassphraseLock.lock() },
        )
        AmberOutlinedButton(
            text = stringResource(Res.string.d_change_passphrase),
            onClick = { showChangeDialog = true },
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(stringResource(Res.string.d_lock_after), style = MaterialTheme.typography.bodySmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            0 to stringResource(Res.string.d_lock_never),
            5 to stringResource(Res.string.d_lock_5min),
            15 to stringResource(Res.string.d_lock_15min),
            60 to stringResource(Res.string.d_lock_1hour),
        ).forEach { (minutes, label) ->
            FilterChip(
                selected = settings.autoLockMinutes == minutes,
                onClick = {
                    SettingsStore.update { it.copy(autoLockMinutes = minutes) }
                    PassphraseLock.touch()
                },
                label = { Text(label) },
            )
        }
    }

    if (showChangeDialog) {
        ChangePassphraseDialog { showChangeDialog = false }
    }
}

@Composable
private fun ChangePassphraseDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var newPassphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(stringResource(Res.string.d_change_the_passphrase)) },
        text = {
            Column {
                Text(stringResource(Res.string.d_passphrase_never_stored), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                PassphraseField(label = Res.string.d_current_passphrase, value = current, onValueChange = { current = it })
                Spacer(Modifier.height(8.dp))
                PassphraseField(label = Res.string.d_new_passphrase, value = newPassphrase, onValueChange = { newPassphrase = it })
                Spacer(Modifier.height(8.dp))
                PassphraseField(label = Res.string.d_repeat_passphrase, value = confirm, onValueChange = { confirm = it })
            }
        },
        confirmButton = {
            AmberTextButton(
                text = if (working) stringResource(Res.string.d_working) else stringResource(Res.string.save),
                enabled = !working,
                onClick = {
                    val error = validateNewPassphrase(newPassphrase, confirm)
                    if (error != null) {
                        Toaster.toast(getStringBlocking(error))
                        return@AmberTextButton
                    }
                    working = true
                    scope.launch {
                        try {
                            if (applyPassphraseChange(current, newPassphrase)) onDismiss()
                        } finally {
                            working = false
                        }
                    }
                },
            )
        },
        dismissButton = {
            AmberTextButton(
                text = stringResource(Res.string.cancel),
                enabled = !working,
                onClick = onDismiss,
            )
        },
    )
}

private fun validateNewPassphrase(newPassphrase: String, confirm: String): StringResource? = when {
    newPassphrase.length < PassphraseLock.MIN_PASSPHRASE_LENGTH -> Res.string.d_use_12_chars
    newPassphrase != confirm -> Res.string.d_passphrases_no_match
    else -> null
}

private suspend fun applyPassphraseChange(current: String, newPassphrase: String): Boolean = try {
    if (PassphraseLock.changePassphrase(current.toCharArray(), newPassphrase.toCharArray())) {
        Toaster.toast(getStringBlocking(Res.string.d_passphrase_changed))
        true
    } else {
        Toaster.toast(getStringBlocking(Res.string.d_wrong_current_passphrase))
        false
    }
} catch (e: PassphraseLock.OsSecretUnavailableException) {
    Toaster.toast(getStringBlocking(Res.string.d_os_secret_unavailable, e.storeDescription))
    false
} catch (e: Exception) {
    Toaster.toast(e.message ?: getStringBlocking(Res.string.d_failed_update_passphrase))
    false
}

@Composable
private fun BackupDialog(
    account: DesktopAccount,
    onDismiss: () -> Unit,
) {
    val copyText = rememberCopyText()
    var confirmCopy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.backup_keys)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PlainKeyBackupSection(account = account, onRequestCopy = { confirmCopy = true })
                Spacer(Modifier.height(12.dp))
                EncryptedKeyBackupSection(account = account, copyText = copyText)
            }
        },
        confirmButton = {
            AmberTextButton(text = stringResource(Res.string.d_close), onClick = onDismiss)
        },
    )
    if (confirmCopy) {
        ConfirmCopyNsecDialog(
            onConfirm = {
                confirmCopy = false
                copyText(account.getNsec())
                account.didBackup = true
                Session.saveMeta(account)
                Toaster.toast(getStringBlocking(Res.string.d_nsec_copied))
            },
            onDismiss = { confirmCopy = false },
        )
    }
}

@Composable
private fun ColumnScope.PlainKeyBackupSection(
    account: DesktopAccount,
    onRequestCopy: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showSecret by remember { mutableStateOf(false) }
    var seedWords by remember { mutableStateOf("") }

    Text(stringResource(Res.string.d_secret_key_nsec), style = MaterialTheme.typography.titleSmall)
    if (showSecret) {
        Text(account.getNsec(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        if (seedWords.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.d_seed_words), style = MaterialTheme.typography.titleSmall)
            Text(seedWords, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        }
    } else {
        AmberTextButton(
            text = stringResource(Res.string.d_show),
            onClick = {
                showSecret = true
                scope.launch { seedWords = AccountManager.seedWords(account.npub) }
            },
        )
    }
    Row {
        AmberTextButton(text = stringResource(Res.string.d_copy_nsec), onClick = onRequestCopy)
    }
}

@Composable
private fun ColumnScope.EncryptedKeyBackupSection(
    account: DesktopAccount,
    copyText: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var ncryptsec by remember { mutableStateOf("") }

    Text(stringResource(Res.string.d_encrypted_backup), style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(stringResource(Res.string.d_password)) },
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    if (ncryptsec.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(
            ncryptsec,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.heightIn(max = 90.dp).verticalScroll(rememberScrollState()),
        )
    }
    AmberTextButton(
        text = stringResource(Res.string.d_encrypt_and_copy),
        onClick = {
            if (password.isBlank()) {
                Toaster.toast(getStringBlocking(Res.string.d_password_required))
                return@AmberTextButton
            }
            ncryptsec = account.nip49Encrypt(password)
            copyText(ncryptsec)
            account.didBackup = true
            Session.saveMeta(account)
            Toaster.toast(getStringBlocking(Res.string.d_encrypted_key_copied))
        },
    )
}

@Composable
private fun ConfirmCopyNsecDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.warning)) },
        text = { Text(stringResource(Res.string.d_copy_nsec_warning)) },
        confirmButton = {
            AmberTextButton(text = stringResource(Res.string.d_copy_nsec), onClick = onConfirm)
        },
        dismissButton = {
            AmberTextButton(text = stringResource(Res.string.cancel), onClick = onDismiss)
        },
    )
}

@Composable
private fun LogsDialog(
    account: DesktopAccount,
    onDismiss: () -> Unit,
) {
    val store = AmberDesktop.store(account.npub)
    val logs by store.logs.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.d_logs)) },
        text = {
            val window = LocalWindowContentSize.current
            Column(Modifier.heightIn(max = (window.height - 220.dp).coerceIn(120.dp, 420.dp)).verticalScroll(rememberScrollState())) {
                if (logs.isEmpty()) {
                    Text(stringResource(Res.string.d_no_logs))
                }
                logs.sortedByDescending { it.time }.forEach { log ->
                    Text(
                        "${DateTimes.format(log.time)} · ${log.type} · ${log.url}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(log.message, style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                }
            }
        },
        confirmButton = {
            AmberTextButton(
                text = stringResource(Res.string.d_clear),
                onClick = { store.clearLogs() },
            )
        },
        dismissButton = {
            AmberTextButton(
                text = stringResource(Res.string.d_close),
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun ColumnScope.AuthWhitelistSection() {
    val settings by SettingsStore.settings.collectAsState()
    var input by remember { mutableStateOf("") }

    fun addEntry() {
        val host = relayHostAndPort(input)
        if (host.isNotBlank() && host !in settings.authWhitelist) {
            SettingsStore.update { it.copy(authWhitelist = it.authWhitelist + host) }
        }
        input = ""
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(stringResource(Res.string.add_relay_to_whitelist)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        AmberOutlinedButton(
            text = stringResource(Res.string.add),
            enabled = relayHostAndPort(input).isNotBlank(),
            onClick = { addEntry() },
        )
    }
    settings.authWhitelist.forEach { host ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(host, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            AmberTextButton(
                text = stringResource(Res.string.remove),
                onClick = { SettingsStore.update { it.copy(authWhitelist = it.authWhitelist - host) } },
            )
        }
        HorizontalDivider()
    }
}

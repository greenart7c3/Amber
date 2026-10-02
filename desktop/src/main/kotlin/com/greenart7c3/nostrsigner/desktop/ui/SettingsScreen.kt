package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.isTraySupported
import com.greenart7c3.nostrsigner.desktop.Session
import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AccountsStore
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AutoStart
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.DesktopKeyStore
import com.greenart7c3.nostrsigner.desktop.core.PassphraseLock
import com.greenart7c3.nostrsigner.desktop.core.RelayHttpClients
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import com.greenart7c3.nostrsigner.desktop.core.Strings
import com.greenart7c3.nostrsigner.desktop.core.TorManager
import com.greenart7c3.nostrsigner.desktop.core.TorMode
import com.greenart7c3.nostrsigner.desktop.core.TorStatus
import com.greenart7c3.nostrsigner.desktop.core.toShortenHex
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(account: DesktopAccount) {
    val scope = rememberCoroutineScope()
    val settings by SettingsStore.settings.collectAsState()
    val accounts by AccountsStore.accounts.collectAsState()
    val language by Strings.currentLanguage.collectAsState()
    var showBackupDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf(account.name.value) }

    val scrollState = rememberScrollState()
    ScrollbarBox(rememberScrollbarAdapter(scrollState), Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
            SectionTitle(Strings.get("account", language))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(Strings.get("name", language)) },
                    singleLine = true,
                    modifier = Modifier.widthIn(max = 420.dp).weight(1f, fill = false),
                )
                AmberOutlinedButton(
                    text = Strings.get("save", language),
                    onClick = {
                        account.name.value = name
                        Session.saveMeta(account)
                        Toaster.toast(Strings.get("d_saved", language))
                    },
                )
            }
            Text(
                Strings.format("d_public_key", account.npub, language = language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            AmberButton(text = Strings.get("backup_keys", language), onClick = { showBackupDialog = true })

            Spacer(Modifier.height(16.dp))
            SectionTitle(Strings.get("d_sign_policy", language))
            Text(
                Strings.get("d_sign_policy_sub", language),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            val policies = listOf(
                0 to Strings.get("d_policy_basic", language),
                1 to Strings.get("d_policy_approve", language),
                2 to Strings.get("d_policy_sign_all", language),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            Spacer(Modifier.height(16.dp))
            SectionTitle(Strings.get("d_theme", language))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf<Pair<Boolean?, String>>(
                    null to Strings.get("d_theme_system", language),
                    false to Strings.get("d_theme_light", language),
                    true to Strings.get("d_theme_dark", language),
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = settings.darkTheme == value,
                        onClick = { SettingsStore.update { it.copy(darkTheme = value) } },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle(Strings.get("language", language))
            LanguagePicker()

            Spacer(Modifier.height(16.dp))
            SectionTitle(Strings.get("d_desktop", language))
            if (isTraySupported) {
                SettingSwitch(
                    title = Strings.get("d_keep_in_tray", language),
                    description = Strings.get("d_keep_in_tray_sub", language),
                    checked = settings.closeToTray,
                    onCheckedChange = { value -> SettingsStore.update { it.copy(closeToTray = value) } },
                )
            }
            SettingSwitch(
                title = Strings.get("d_notifications", language),
                description = Strings.get("d_notifications_sub", language),
                checked = settings.showNotifications,
                onCheckedChange = { value -> SettingsStore.update { it.copy(showNotifications = value) } },
            )
            if (AutoStart.isSupported()) {
                SettingSwitch(
                    title = Strings.get("d_start_on_boot", language),
                    description = Strings.get("d_start_on_boot_sub", language),
                    checked = settings.startOnBoot,
                    onCheckedChange = { value ->
                        SettingsStore.update { it.copy(startOnBoot = value) }
                        AutoStart.setEnabled(value)
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
            SectionTitle(Strings.get("connect_via_tor_short", language))
            TorSection()

            Spacer(Modifier.height(16.dp))
            SectionTitle(Strings.get("accounts", language))
            accounts.forEach { record ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            record.name.ifBlank { record.npub.toShortenHex() },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (record.npub == account.npub) {
                            Text(
                                Strings.get("d_active", language),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (record.npub != account.npub) {
                        AmberTextButton(
                            text = Strings.get("d_switch", language),
                            onClick = { scope.launch { Session.switchTo(record.npub) } },
                        )
                    }
                    AmberTextButton(
                        text = Strings.get("d_log_out", language),
                        onClick = { showLogoutConfirm = record.npub },
                    )
                }
                HorizontalDivider()
            }
            Spacer(Modifier.height(8.dp))
            AmberOutlinedButton(text = Strings.get("d_add_an_account", language), onClick = { Session.addingAccount.value = true })

            Spacer(Modifier.height(16.dp))
            SectionTitle(Strings.get("security", language))
            Text(
                Strings.format("d_security_keys_desc", DesktopKeyStore.passwordSourceDescription ?: "…", language = language),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            SecuritySection()

            Spacer(Modifier.height(16.dp))
            SectionTitle(Strings.get("d_diagnostics", language))
            AmberOutlinedButton(text = Strings.get("d_view_logs", language), onClick = { showLogsDialog = true })
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showBackupDialog) {
        BackupDialog(account) { showBackupDialog = false }
    }
    if (showLogsDialog) {
        LogsDialog(account) { showLogsDialog = false }
    }
    showLogoutConfirm?.let { npub ->
        var logoutPassphrase by remember { mutableStateOf("") }
        var loggingOut by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!loggingOut) showLogoutConfirm = null },
            title = { Text(Strings.get("d_log_out_q", language)) },
            text = {
                Column {
                    Text(Strings.get("d_log_out_confirm", language))
                    Spacer(Modifier.height(8.dp))
                    Text(Strings.get("d_log_out_passphrase", language))
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = logoutPassphrase,
                        onValueChange = { logoutPassphrase = it },
                        label = { Text(Strings.get("d_passphrase", language)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        enabled = !loggingOut,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                AmberTextButton(
                    text = if (loggingOut) Strings.get("d_working", language) else Strings.get("d_log_out", language),
                    enabled = !loggingOut,
                    onClick = {
                        loggingOut = true
                        scope.launch {
                            if (PassphraseLock.verify(logoutPassphrase.toCharArray())) {
                                showLogoutConfirm = null
                                Session.logout(npub)
                            } else {
                                Toaster.toast(Strings.get("d_wrong_passphrase", language))
                            }
                            loggingOut = false
                        }
                    },
                )
            },
            dismissButton = {
                AmberTextButton(
                    text = Strings.get("cancel", language),
                    enabled = !loggingOut,
                    onClick = { showLogoutConfirm = null },
                )
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
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
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TorSection() {
    val settings by SettingsStore.settings.collectAsState()
    val language by Strings.currentLanguage.collectAsState()
    val torStatus by TorManager.status.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmDisable by remember { mutableStateOf(false) }
    var portText by remember(settings.proxyPort) { mutableStateOf(settings.proxyPort.toString()) }

    fun applyMode(mode: TorMode, port: Int = settings.proxyPort) {
        SettingsStore.update { it.copy(torMode = mode, proxyPort = port) }
        AmberDesktop.applyTorSettings()
        if (mode == TorMode.EXTERNAL) {
            scope.launch {
                val alive = withContext(Dispatchers.IO) { RelayHttpClients.isSocksProxyAlive(port) }
                Toaster.toast(Strings.get(if (alive) "d_tor_proxy_ok" else "failed_to_connect_to_tor_orbot", language))
            }
        }
    }

    Text(Strings.get("tor_subtitle", language), style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            TorMode.DISABLED to Strings.get("d_tor_off", language),
            TorMode.BUILTIN to Strings.get("builtin_tor_title", language),
            TorMode.EXTERNAL to Strings.get("d_tor_external", language),
        ).forEach { (mode, label) ->
            FilterChip(
                selected = settings.torMode == mode,
                onClick = {
                    when {
                        mode == settings.torMode -> {}
                        // Like Android: confirm before traffic moves back to the clearnet.
                        mode == TorMode.DISABLED -> confirmDisable = true
                        else -> applyMode(mode)
                    }
                },
                label = { Text(label) },
            )
        }
    }
    Spacer(Modifier.height(4.dp))
    when (settings.torMode) {
        TorMode.BUILTIN -> {
            Text(Strings.get("builtin_tor_description", language), style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val statusText = when (val status = torStatus) {
                    TorStatus.Stopped -> Strings.get("d_tor_stopped", language)
                    is TorStatus.Connecting -> if (status.percentage <= 0) {
                        Strings.get("tor_starting", language)
                    } else {
                        Strings.format("tor_connecting", status.percentage, language = language)
                    }
                    TorStatus.Connected -> Strings.get("builtin_tor_active", language)
                    is TorStatus.Failed -> listOfNotNull(Strings.get("tor_connection_failed", language), status.message?.takeIf { it.isNotBlank() }).joinToString(": ")
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (torStatus is TorStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                AmberTextButton(
                    text = Strings.get("tor_restart", language),
                    onClick = { TorManager.restart(AmberDesktop.applicationIOScope) },
                )
            }
        }

        TorMode.EXTERNAL -> {
            Text(Strings.get("d_tor_external_sub", language), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { value -> portText = value.filter { it.isDigit() }.take(5) },
                    label = { Text(Strings.get("d_tor_socks_port", language)) },
                    singleLine = true,
                    modifier = Modifier.widthIn(max = 200.dp),
                )
                AmberOutlinedButton(
                    text = Strings.get("save", language),
                    onClick = {
                        val port = portText.toIntOrNull()
                        if (port == null || port !in 1..65535) {
                            Toaster.toast(Strings.get("d_tor_invalid_port", language))
                        } else {
                            applyMode(TorMode.EXTERNAL, port)
                        }
                    },
                )
            }
        }

        TorMode.DISABLED -> {}
    }
    Text(
        Strings.get("d_tor_local_note", language),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text(Strings.get("do_you_really_want_to_disable_tor_title", language)) },
            text = { Text(Strings.get("do_you_really_want_to_disable_tor_text", language)) },
            confirmButton = {
                AmberTextButton(
                    text = Strings.get("yes", language),
                    onClick = {
                        confirmDisable = false
                        applyMode(TorMode.DISABLED)
                    },
                )
            },
            dismissButton = {
                AmberTextButton(text = Strings.get("no", language), onClick = { confirmDisable = false })
            },
        )
    }
}

@Composable
private fun SecuritySection() {
    val settings by SettingsStore.settings.collectAsState()
    val language by Strings.currentLanguage.collectAsState()
    var showChangeDialog by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AmberOutlinedButton(
            text = Strings.get("d_lock_now", language),
            onClick = { PassphraseLock.lock() },
        )
        AmberOutlinedButton(
            text = Strings.get("d_change_passphrase", language),
            onClick = { showChangeDialog = true },
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(Strings.get("d_lock_after", language), style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            0 to Strings.get("d_lock_never", language),
            5 to Strings.get("d_lock_5min", language),
            15 to Strings.get("d_lock_15min", language),
            60 to Strings.get("d_lock_1hour", language),
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
    val language by Strings.currentLanguage.collectAsState()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(Strings.get("d_change_the_passphrase", language)) },
        text = {
            Column {
                Text(
                    Strings.get("d_passphrase_never_stored", language),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = { Text(Strings.get("d_current_passphrase", language)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = new,
                    onValueChange = { new = it },
                    label = { Text(Strings.get("d_new_passphrase", language)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text(Strings.get("d_repeat_passphrase", language)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            AmberTextButton(
                text = if (working) Strings.get("d_working", language) else Strings.get("save", language),
                enabled = !working,
                onClick = {
                    if (new.length < 8) {
                        Toaster.toast(Strings.get("d_use_8_chars", language))
                        return@AmberTextButton
                    }
                    if (new != confirm) {
                        Toaster.toast(Strings.get("d_passphrases_no_match", language))
                        return@AmberTextButton
                    }
                    working = true
                    scope.launch {
                        try {
                            if (PassphraseLock.changePassphrase(current.toCharArray(), new.toCharArray())) {
                                Toaster.toast(Strings.get("d_passphrase_changed", language))
                                onDismiss()
                            } else {
                                Toaster.toast(Strings.get("d_wrong_current_passphrase", language))
                            }
                        } catch (e: Exception) {
                            Toaster.toast(e.message ?: Strings.get("d_failed_update_passphrase", language))
                        }
                        working = false
                    }
                },
            )
        },
        dismissButton = {
            AmberTextButton(
                text = Strings.get("cancel", language),
                enabled = !working,
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun BackupDialog(
    account: DesktopAccount,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val language by Strings.currentLanguage.collectAsState()
    var showSecret by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var ncryptsec by remember { mutableStateOf("") }
    var seedWords by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.get("backup_keys", language)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(Strings.get("d_secret_key_nsec", language), style = MaterialTheme.typography.titleSmall)
                if (showSecret) {
                    Text(account.getNsec(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    if (seedWords.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(Strings.get("d_seed_words", language), style = MaterialTheme.typography.titleSmall)
                        Text(seedWords, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    AmberTextButton(
                        text = Strings.get("d_show", language),
                        onClick = {
                            showSecret = true
                            scope.launch { seedWords = AccountManager.seedWords(account.npub) }
                        },
                    )
                }
                Row {
                    AmberTextButton(
                        text = Strings.get("d_copy_nsec", language),
                        onClick = {
                            clipboard.setText(AnnotatedString(account.getNsec()))
                            account.didBackup = true
                            Session.saveMeta(account)
                            Toaster.toast(Strings.get("d_nsec_copied", language))
                        },
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(Strings.get("d_encrypted_backup", language), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(Strings.get("d_password", language)) },
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
                    text = Strings.get("d_encrypt_and_copy", language),
                    onClick = {
                        if (password.isBlank()) {
                            Toaster.toast(Strings.get("d_password_required", language))
                            return@AmberTextButton
                        }
                        ncryptsec = account.nip49Encrypt(password)
                        clipboard.setText(AnnotatedString(ncryptsec))
                        account.didBackup = true
                        Session.saveMeta(account)
                        Toaster.toast(Strings.get("d_encrypted_key_copied", language))
                    },
                )
            }
        },
        confirmButton = {
            AmberTextButton(
                text = Strings.get("d_close", language),
                onClick = onDismiss,
            )
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
    val language by Strings.currentLanguage.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.get("d_logs", language)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (logs.isEmpty()) {
                    Text(Strings.get("d_no_logs", language))
                }
                logs.sortedByDescending { it.time }.forEach { log ->
                    Text(
                        "${DateFormat.getDateTimeInstance().format(Date(log.time))} · ${log.type} · ${log.url}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(log.message, style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                }
            }
        },
        confirmButton = {
            AmberTextButton(
                text = Strings.get("d_clear", language),
                onClick = {
                    store.clearLogs()
                },
            )
        },
        dismissButton = {
            AmberTextButton(
                text = Strings.get("d_close", language),
                onClick = onDismiss,
            )
        },
    )
}

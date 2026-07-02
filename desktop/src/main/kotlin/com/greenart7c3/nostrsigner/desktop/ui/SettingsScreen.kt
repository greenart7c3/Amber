package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.DesktopKeyStore
import com.greenart7c3.nostrsigner.desktop.core.PassphraseLock
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import com.greenart7c3.nostrsigner.desktop.core.toShortenHex
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(account: DesktopAccount) {
    val scope = rememberCoroutineScope()
    val settings by SettingsStore.settings.collectAsState()
    val accounts by AccountsStore.accounts.collectAsState()
    var showBackupDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf(account.name.value) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(12.dp))

        SectionTitle("Account")
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.weight(1f),
            )
            AmberOutlinedButton(
                modifier = Modifier.weight(0.4f),
                text = "Save",
                onClick = {
                    account.name.value = name
                    Session.saveMeta(account)
                    Toaster.toast("Saved")
                },
            )
        }
        Text("Public key: ${account.npub}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        AmberButton(text = "Backup keys", onClick = { showBackupDialog = true })

        Spacer(Modifier.height(16.dp))
        SectionTitle("Sign policy")
        Text(
            "Default policy applied to newly connected applications.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(4.dp))
        val policies = listOf(
            0 to "Basic permissions",
            1 to "Approve requested permissions",
            2 to "Sign everything",
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
        SectionTitle("Theme")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf<Pair<Boolean?, String>>(null to "System", false to "Light", true to "Dark").forEach { (value, label) ->
                FilterChip(
                    selected = settings.darkTheme == value,
                    onClick = { SettingsStore.update { it.copy(darkTheme = value) } },
                    label = { Text(label) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("Desktop")
        if (isTraySupported) {
            SettingSwitch(
                title = "Keep running in the tray",
                description = "Closing the window minimizes Amber to the system tray so it keeps answering requests.",
                checked = settings.closeToTray,
                onCheckedChange = { value -> SettingsStore.update { it.copy(closeToTray = value) } },
            )
        }
        SettingSwitch(
            title = "Notifications",
            description = "Show a system notification when a request needs your approval.",
            checked = settings.showNotifications,
            onCheckedChange = { value -> SettingsStore.update { it.copy(showNotifications = value) } },
        )

        Spacer(Modifier.height(16.dp))
        SectionTitle("Accounts")
        accounts.forEach { record ->
            Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            record.name.ifBlank { record.npub.toShortenHex() },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (record.npub == account.npub) {
                            Text("Active", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (record.npub != account.npub) {
                        TextButton(onClick = { scope.launch { Session.switchTo(record.npub) } }) {
                            Text("Switch")
                        }
                    }
                    TextButton(onClick = { showLogoutConfirm = record.npub }) {
                        Text("Log out")
                    }
                }
            }
        }
        AmberOutlinedButton(text = "Add an account", onClick = { Session.addingAccount.value = true })

        Spacer(Modifier.height(16.dp))
        SectionTitle("Security")
        Text(
            "Keys are encrypted with AES-256; the encryption key is protected by: " +
                (DesktopKeyStore.passwordSourceDescription ?: "…"),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        SecuritySection()

        Spacer(Modifier.height(16.dp))
        SectionTitle("Diagnostics")
        AmberOutlinedButton(text = "View logs", onClick = { showLogsDialog = true })
        Spacer(Modifier.height(24.dp))
    }

    if (showBackupDialog) {
        BackupDialog(account) { showBackupDialog = false }
    }
    if (showLogsDialog) {
        LogsDialog(account) { showLogsDialog = false }
    }
    showLogoutConfirm?.let { npub ->
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = null },
            title = { Text("Log out?") },
            text = { Text("This deletes the account key and its connections from this device. Make sure the key is backed up.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = null
                        scope.launch { Session.logout(npub) }
                    },
                ) { Text("Log out") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = null }) { Text("Cancel") }
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
private fun SecuritySection() {
    val scope = rememberCoroutineScope()
    val lockStatus by PassphraseLock.state.collectAsState()
    val settings by SettingsStore.settings.collectAsState()
    var dialog by remember { mutableStateOf<PassphraseDialogMode?>(null) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    if (lockStatus == PassphraseLock.Status.DISABLED) {
        Text(
            "Set a passphrase to keep your keys encrypted even against software " +
                "that can read your files. You will be asked for it when Amber starts.",
            style = MaterialTheme.typography.bodySmall,
        )
        AmberButton(text = "Set a passphrase", onClick = { dialog = PassphraseDialogMode.SET })
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmberOutlinedButton(
                modifier = Modifier.weight(1f),
                text = "Lock now",
                onClick = { PassphraseLock.lock() },
            )
            AmberOutlinedButton(
                modifier = Modifier.weight(1f),
                text = "Change passphrase",
                onClick = { dialog = PassphraseDialogMode.CHANGE },
            )
            AmberOutlinedButton(
                modifier = Modifier.weight(1f),
                text = "Remove passphrase",
                onClick = { showRemoveConfirm = true },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("Lock automatically after", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Never", 5 to "5 min", 15 to "15 min", 60 to "1 hour").forEach { (minutes, label) ->
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
    }

    dialog?.let { mode ->
        PassphraseDialog(mode) { dialog = null }
    }
    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove the passphrase?") },
            text = {
                Text(
                    "The encryption key will go back to the OS credential store " +
                        "(or a local file), and Amber will no longer ask for a " +
                        "passphrase at startup.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveConfirm = false
                        scope.launch {
                            PassphraseLock.disable()
                            Toaster.toast("Passphrase removed")
                        }
                    },
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

private enum class PassphraseDialogMode { SET, CHANGE }

@Composable
private fun PassphraseDialog(
    mode: PassphraseDialogMode,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text(if (mode == PassphraseDialogMode.SET) "Set a passphrase" else "Change the passphrase") },
        text = {
            Column {
                Text(
                    "The passphrase is never stored anywhere. If you forget it, the " +
                        "only way back in is restoring your keys from a backup (nsec " +
                        "or seed words).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                if (mode == PassphraseDialogMode.CHANGE) {
                    OutlinedTextField(
                        value = current,
                        onValueChange = { current = it },
                        label = { Text("Current passphrase") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = new,
                    onValueChange = { new = it },
                    label = { Text("New passphrase (min. 8 characters)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Repeat the new passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !working,
                onClick = {
                    if (new.length < 8) {
                        Toaster.toast("Use at least 8 characters")
                        return@TextButton
                    }
                    if (new != confirm) {
                        Toaster.toast("The passphrases do not match")
                        return@TextButton
                    }
                    working = true
                    scope.launch {
                        try {
                            if (mode == PassphraseDialogMode.SET) {
                                PassphraseLock.enable(new.toCharArray())
                                Toaster.toast("Passphrase set")
                                onDismiss()
                            } else {
                                if (PassphraseLock.changePassphrase(current.toCharArray(), new.toCharArray())) {
                                    Toaster.toast("Passphrase changed")
                                    onDismiss()
                                } else {
                                    Toaster.toast("Wrong current passphrase")
                                }
                            }
                        } catch (e: Exception) {
                            Toaster.toast(e.message ?: "Failed to update the passphrase")
                        }
                        working = false
                    }
                },
            ) { Text(if (working) "Working…" else "Save") }
        },
        dismissButton = {
            TextButton(enabled = !working, onClick = onDismiss) { Text("Cancel") }
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
    var showSecret by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var ncryptsec by remember { mutableStateOf("") }
    var seedWords by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup keys") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Secret key (nsec)", style = MaterialTheme.typography.titleSmall)
                if (showSecret) {
                    Text(account.getNsec(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    if (seedWords.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text("Seed words", style = MaterialTheme.typography.titleSmall)
                        Text(seedWords, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    TextButton(
                        onClick = {
                            showSecret = true
                            scope.launch { seedWords = AccountManager.seedWords(account.npub) }
                        },
                    ) { Text("Show") }
                }
                Row {
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(account.getNsec()))
                            account.didBackup = true
                            Session.saveMeta(account)
                            Toaster.toast("Secret key copied. Clear your clipboard after pasting it!")
                        },
                    ) { Text("Copy nsec") }
                }

                Spacer(Modifier.height(12.dp))
                Text("Encrypted backup (ncryptsec)", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
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
                TextButton(
                    onClick = {
                        if (password.isBlank()) {
                            Toaster.toast("Password is required")
                            return@TextButton
                        }
                        ncryptsec = account.nip49Encrypt(password)
                        clipboard.setText(AnnotatedString(ncryptsec))
                        account.didBackup = true
                        Session.saveMeta(account)
                        Toaster.toast("Encrypted key copied to the clipboard")
                    },
                ) { Text("Encrypt and copy") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
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
        title = { Text("Logs") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (logs.isEmpty()) {
                    Text("No logs")
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
            TextButton(
                onClick = {
                    store.clearLogs()
                },
            ) { Text("Clear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

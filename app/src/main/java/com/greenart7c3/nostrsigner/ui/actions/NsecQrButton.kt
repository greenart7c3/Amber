package com.greenart7c3.nostrsigner.ui.actions

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.encrypt_and_show_qr_code
import com.greenart7c3.nostrsigner.commonsui.failed_to_encrypt_key
import com.greenart7c3.nostrsigner.commonsui.failed_to_export_key
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.show_qr_code
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.Biometrics.authenticate
import com.greenart7c3.nostrsigner.ui.LocalAppScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/**
 * The nsec to show, or null after telling the user it could not be exported. Caught
 * because a throw here (device locked, or a WorkManager cold start before settings
 * load) would cancel the composition scope and crash the app.
 */
@Suppress("NoComments")
private suspend fun exportNsec(
    account: Account,
    context: Context,
): String? = try {
    account.keyExport.getNsec()
} catch (e: Exception) {
    if (e is CancellationException) throw e
    AmberLog.e(Amber.TAG, "Failed to export secret key", e)
    withContext(Dispatchers.Main) {
        Toast.makeText(context, getStringBlocking(Res.string.failed_to_export_key), Toast.LENGTH_SHORT).show()
    }
    null
}

private suspend fun encryptForQr(
    account: Account,
    password: String,
    context: Context,
): String? = try {
    account.keyExport.nip49Encrypt(password)
} catch (e: Exception) {
    if (e is CancellationException) throw e
    AmberLog.e(Amber.TAG, "Failed to encrypt secret key for QR", e)
    withContext(Dispatchers.Main) {
        Toast.makeText(context, getStringBlocking(Res.string.failed_to_encrypt_key), Toast.LENGTH_SHORT).show()
    }
    null
}

@Composable
internal fun NSecQrButton(
    account: Account,
    password: String,
    onLoading: (Boolean) -> Unit,
    onBackupFinish: () -> Unit,
    onShowQrCode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appScope = LocalAppScope.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showDialog = remember { mutableStateOf(false) }

    if (showDialog.value) {
        NsecQrDialog(
            account = account,
            onBackupFinish = onBackupFinish,
            onClose = { showDialog.value = false },
        )
    }

    val showQr: () -> Unit = {
        if (password.isNotBlank()) {
            appScope.launch {
                onLoading(true)
                try {
                    encryptForQr(account, password, context)?.let { ncryptsec ->
                        account.didBackup = true
                        onBackupFinish()
                        onShowQrCode(ncryptsec)
                    }
                } finally {
                    onLoading(false)
                }
            }
        } else {
            showDialog.value = true
        }
    }

    val keyguardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) showQr()
    }

    val text = stringResource(if (password.isBlank()) Res.string.show_qr_code else Res.string.encrypt_and_show_qr_code)

    BackupIconButton(
        modifier = modifier,
        icon = { Icon(Icons.Outlined.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp)) },
        text = text,
        onClick = {
            authenticate(
                title = text,
                context = context,
                keyguardLauncher = keyguardLauncher,
                onApproved = showQr,
                onError = { _, message ->
                    scope.launch {
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                },
            )
        },
    )
}

@Composable
private fun NsecQrDialog(
    account: Account,
    onBackupFinish: () -> Unit,
    onClose: () -> Unit,
) {
    val appScope = LocalAppScope.current
    val context = LocalContext.current
    var nsec by remember { mutableStateOf("") }
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            val exported = exportNsec(account, context)
            if (exported == null) currentOnClose() else nsec = exported
        }
    }
    if (nsec.isNotBlank()) {
        QrCodeDialog(content = nsec) {
            appScope.launch {
                account.didBackup = true
                onBackupFinish()
                onClose()
            }
        }
    }
}

package com.greenart7c3.nostrsigner.ui.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.greenart7c3.nostrsigner.AccountInfo
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun LogoutButton(
    acc: AccountInfo,
    accountStateViewModel: AccountStateViewModel,
) {
    val scope = rememberCoroutineScope()
    var logoutDialog by remember { mutableStateOf(false) }
    if (logoutDialog) {
        LogoutDialog(
            onCancel = {
                logoutDialog = false
            },
            onConfirm = {
                scope.launch(Dispatchers.IO) {
                    val account = LocalPreferences.loadFromEncryptedStorage(acc.npub)
                    account?.let {
                        val database = NostrSigner.instance.getDatabase(account.keyPair.pubKey.toNpub())
                        val permissions = database.applicationDao().getAll(it.keyPair.pubKey.toHexKey())
                        permissions.forEach { app ->
                            database.applicationDao().delete(app)
                        }
                    }

                    logoutDialog = false
                    accountStateViewModel.logOff(acc.npub)
                }
            },
        )
    }

    IconButton(
        onClick = { logoutDialog = true },
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Default.Logout,
            contentDescription = stringResource(R.string.logout),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

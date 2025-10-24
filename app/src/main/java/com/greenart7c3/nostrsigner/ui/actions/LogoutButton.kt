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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.greenart7c3.nostrsigner.AccountInfo
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun LogoutButton(
    acc: AccountInfo,
    accountStateViewModel: AccountStateViewModel,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var logoutDialog by remember { mutableStateOf(false) }
    if (logoutDialog) {
        LogoutDialog(
            onCancel = {
                logoutDialog = false
            },
            onConfirm = {
                scope.launch(Dispatchers.IO) {
                    val account = LocalPreferences.loadFromEncryptedStorage(context, acc.npub)
                    account?.let {
                        val database = Amber.instance.getDatabase(it.npub)
                        val permissions = database.dao().getAll(it.hexKey)
                        permissions.forEach { app ->
                            database.dao().delete(app)
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

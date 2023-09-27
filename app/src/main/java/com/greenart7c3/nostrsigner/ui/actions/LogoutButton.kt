package com.greenart7c3.nostrsigner.ui.actions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.greenart7c3.nostrsigner.AccountInfo
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel

@Composable
fun LogoutButton(
    acc: AccountInfo,
    accountStateViewModel: AccountStateViewModel
) {
    var logoutDialog by remember { mutableStateOf(false) }
    if (logoutDialog) {
        LogoutDialog(
            onCancel = {
                logoutDialog = false
            },
            onConfirm = {
                logoutDialog = false
                accountStateViewModel.logOff(acc.npub)
            }
        )
    }

    IconButton(
        onClick = { logoutDialog = true }
    ) {
        Icon(
            imageVector = Icons.Default.Logout,
            contentDescription = stringResource(R.string.logout),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

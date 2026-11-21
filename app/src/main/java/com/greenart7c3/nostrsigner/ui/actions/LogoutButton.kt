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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.greenart7c3.nostrsigner.AccountInfo
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.logout
import org.jetbrains.compose.resources.stringResource

@Composable
fun LogoutButton(
    acc: AccountInfo,
    onLogOff: (npub: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var logoutDialog by remember { mutableStateOf(false) }
    if (logoutDialog) {
        LogoutDialog(
            onCancel = {
                logoutDialog = false
            },
            onConfirm = {
                // The logout deletes the account's databases, its saved apps included.
                logoutDialog = false
                onLogOff(acc.npub)
            },
        )
    }

    IconButton(
        onClick = { logoutDialog = true },
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Default.Logout,
            contentDescription = stringResource(Res.string.logout),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.greenart7c3.nostrsigner.AccountInfo
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.active_account
import com.greenart7c3.nostrsigner.models.Account
import org.jetbrains.compose.resources.stringResource

@Composable
fun ActiveMarker(
    acc: AccountInfo,
    account: Account,
) {
    val isCurrentUser by remember(account) {
        derivedStateOf {
            account.npub == acc.npub
        }
    }

    if (isCurrentUser) {
        Icon(
            imageVector = Icons.Default.RadioButtonChecked,
            contentDescription = stringResource(Res.string.active_account),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

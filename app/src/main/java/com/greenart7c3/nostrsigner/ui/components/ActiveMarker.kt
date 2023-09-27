package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.greenart7c3.nostrsigner.AccountInfo
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.vitorpamplona.quartz.encoders.toNpub

@Composable
fun ActiveMarker(acc: AccountInfo, account: Account) {
    val isCurrentUser by remember(account) {
        derivedStateOf {
            account.keyPair.pubKey.toNpub() == acc.npub
        }
    }

    if (isCurrentUser) {
        Icon(
            imageVector = Icons.Default.RadioButtonChecked,
            contentDescription = stringResource(R.string.active_account),
            tint = MaterialTheme.colorScheme.secondary
        )
    }
}

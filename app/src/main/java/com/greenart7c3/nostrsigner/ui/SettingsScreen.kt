package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.actions.AccountBackupDialog
import com.greenart7c3.nostrsigner.ui.actions.AccountsBottomSheet
import com.greenart7c3.nostrsigner.ui.actions.LogoutDialog
import com.greenart7c3.nostrsigner.ui.components.HyperlinkText
import com.greenart7c3.nostrsigner.ui.components.IconRow
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier,
    accountStateViewModel: AccountStateViewModel,
    account: Account
) {
    var backupDialogOpen by remember { mutableStateOf(false) }
    var logoutDialog by remember { mutableStateOf(false) }
    var shouldShowBottomSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val sheetState = rememberModalBottomSheetState(
        confirmValueChange = { it != SheetValue.PartiallyExpanded },
        skipPartiallyExpanded = true
    )
    val scope = rememberCoroutineScope()

    if (shouldShowBottomSheet) {
        AccountsBottomSheet(
            sheetState = sheetState,
            account = account,
            accountStateViewModel = accountStateViewModel,
            onClose = {
                scope.launch {
                    shouldShowBottomSheet = false
                    sheetState.hide()
                }
            }
        )
    }

    if (logoutDialog) {
        LogoutDialog(
            onCancel = {
                logoutDialog = false
            },
            onConfirm = {
                logoutDialog = false
                accountStateViewModel.logOff(account.keyPair.pubKey.toNpub())
            }
        )
    }

    Column(
        modifier
    ) {
        Box(
            Modifier
                .padding(16.dp)
        ) {
            IconRow(
                title = "Accounts",
                icon = Icons.Default.Person,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    scope.launch {
                        sheetState.show()
                        shouldShowBottomSheet = true
                    }
                }
            )
        }
        Box(
            Modifier
                .padding(16.dp)
        ) {
            IconRow(
                title = stringResource(R.string.backup_keys),
                icon = Icons.Default.Key,
                tint = MaterialTheme.colorScheme.onBackground,
                onClick = {
                    backupDialogOpen = true
                }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        HyperlinkText(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            fullText = "v${BuildConfig.VERSION_NAME}\n\n${context.getString(R.string.support_development)}\n\n${context.getString(R.string.source_code)}\n\n${context.getString(R.string.made_possible_by)} ${context.getString(R.string.opensats)}",
            hyperLinks = mutableMapOf(
                stringResource(R.string.opensats) to stringResource(R.string.opensats_uri),
                stringResource(R.string.source_code) to stringResource(R.string.amber_github_uri),
                stringResource(R.string.support_development) to stringResource(R.string.support_development_uri)
            ),
            textStyle = TextStyle(
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.inverseSurface
            ),
            linkTextColor = MaterialTheme.colorScheme.primary,
            linkTextDecoration = TextDecoration.Underline,
            fontSize = 18.sp
        )
    }

    if (backupDialogOpen) {
        AccountBackupDialog(account, onClose = { backupDialogOpen = false })
    }
}

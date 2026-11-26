package com.greenart7c3.nostrsigner.ui.actions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.AccountStorage
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.cloud_backup_title
import com.greenart7c3.nostrsigner.commonsui.do_not_leave_the_app_until_the_key_is_generated
import com.greenart7c3.nostrsigner.commonsui.export_all_accounts_title
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.password_is_required
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.CenterCircularProgressIndicator
import com.greenart7c3.nostrsigner.ui.components.IconRow
import com.greenart7c3.nostrsigner.ui.navigation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun AccountBackupScreen(
    navController: NavController,
    onShowQrCode: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val appContext = LocalContext.current.applicationContext
    var isLoading by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(),
        ) {
            if (isLoading) {
                CenterCircularProgressIndicator(Modifier, text = stringResource(Res.string.do_not_leave_the_app_until_the_key_is_generated))
            } else {
                // Collapsible tips section
                var tipsExpanded by remember { mutableStateOf(false) }
                TipsCard(expanded = tipsExpanded, onToggle = { tipsExpanded = !tipsExpanded })

                Spacer(modifier = Modifier.height(16.dp))

                // Password section
                val password = remember { mutableStateOf(TextFieldValue("")) }
                var errorMessage by remember { mutableStateOf("") }
                var showCharsPassword by remember { mutableStateOf(false) }
                val keyboardController = LocalSoftwareKeyboardController.current

                PasswordCard(
                    password = password.value,
                    errorMessage = errorMessage,
                    showCharsPassword = showCharsPassword,
                    onPasswordChange = {
                        password.value = it
                        if (errorMessage.isNotEmpty()) errorMessage = ""
                    },
                    onToggleVisibility = { showCharsPassword = !showCharsPassword },
                    onDone = {
                        if (password.value.text.isBlank()) {
                            errorMessage = getStringBlocking(Res.string.password_is_required)
                        }
                        keyboardController?.hide()
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))

                SavedAccounts.all(appContext).forEach { accountInfo ->
                    SavedAccountBackupCard(
                        npub = accountInfo.npub,
                        password = password.value.text,
                        onLoading = { isLoading = it },
                        onShowQrCode = onShowQrCode,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                OtherBackupOptions(navController)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/** The backup card of the saved account [npub], once its key (and seed words) are loaded. */
@Composable
private fun SavedAccountBackupCard(
    npub: String,
    password: String,
    onLoading: (Boolean) -> Unit,
    onShowQrCode: (String) -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    var localAccount by remember { mutableStateOf<Account?>(null) }
    var seedWords by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            localAccount = AccountStorage.load(appContext, npub)
            localAccount?.let { acc ->
                seedWords = acc.keyExport.seedWords()
            }
        }
    }

    localAccount?.let { account ->
        AccountBackupCard(
            account = account,
            password = password,
            seedWords = seedWords,
            onLoading = onLoading,
            onShowQrCode = onShowQrCode,
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

/** Exporting every account at once, and the cloud backup (online flavors only). */
@Composable
private fun OtherBackupOptions(navController: NavController) {
    IconRow(
        title = stringResource(Res.string.export_all_accounts_title),
        icon = Icons.Filled.SaveAlt,
        tint = MaterialTheme.colorScheme.onBackground,
        onClick = { navController.navigate(Route.ExportAllAccounts.route) },
    )

    if (!BuildFlavorChecker.isOfflineFlavor()) {
        Spacer(modifier = Modifier.height(4.dp))
        IconRow(
            title = stringResource(Res.string.cloud_backup_title),
            icon = Icons.Filled.CloudUpload,
            tint = MaterialTheme.colorScheme.onBackground,
            onClick = { navController.navigate(Route.CloudBackup.route) },
        )
    }
}

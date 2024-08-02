package com.greenart7c3.nostrsigner.ui.actions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.MainLoginPage
import com.greenart7c3.nostrsigner.ui.components.ActiveMarker
import com.greenart7c3.nostrsigner.ui.components.CloseButton
import com.greenart7c3.nostrsigner.ui.components.PostButton
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.vitorpamplona.quartz.encoders.toNpub

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsBottomSheet(
    sheetState: SheetState,
    account: Account,
    accountStateViewModel: AccountStateViewModel,
    onClose: () -> Unit,
) {
    ModalBottomSheet(
        windowInsets = WindowInsets.navigationBars,
        sheetState = sheetState,
        onDismissRequest = {
            onClose()
        },
    ) {
        val context = LocalContext.current
        val accounts = LocalPreferences.allSavedAccounts(context)
        var popupExpanded by remember { mutableStateOf(false) }
        val scrollState = rememberScrollState()
        var showNameDialog by remember { mutableStateOf(false) }
        var currentNpub by remember { mutableStateOf("") }

        Column(modifier = Modifier.verticalScroll(scrollState)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.select_account), fontWeight = FontWeight.Bold)
            }
            accounts.forEach { acc ->
                val name = LocalPreferences.getAccountName(context, acc.npub)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            accountStateViewModel.switchUser(acc.npub, null)
                        }
                        .padding(16.dp, 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                if (name.isNotBlank()) {
                                    Text(name)
                                }
                                Text(acc.npub.toShortenHex())
                            }
                            Column(modifier = Modifier.width(32.dp)) {
                                ActiveMarker(acc, account)
                            }
                        }
                    }

                    if (showNameDialog) {
                        EditAccountDialog(
                            npub = currentNpub,
                            onClose = { showNameDialog = false },
                            onPost = {
                                LocalPreferences.setAccountName(context, currentNpub, it)
                                showNameDialog = false
                                currentNpub = ""
                                accountStateViewModel.switchUser(account.keyPair.pubKey.toNpub(), Route.Settings.route)
                            },
                        )
                    }

                    IconButton(
                        onClick = {
                            showNameDialog = true
                            currentNpub = acc.npub
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit_name),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    LogoutButton(acc, accountStateViewModel)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { popupExpanded = true }) {
                    Text(stringResource(R.string.add_new_account))
                }
            }
        }

        if (popupExpanded) {
            Dialog(
                onDismissRequest = { popupExpanded = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box {
                        MainLoginPage(accountStateViewModel)
                        TopAppBar(
                            title = { Text(text = stringResource(R.string.add_new_account)) },
                            navigationIcon = {
                                IconButton(onClick = { popupExpanded = false }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Default.ArrowBack,
                                        contentDescription = "Back",
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditAccountDialog(
    npub: String,
    onClose: () -> Unit,
    onPost: (String) -> Unit,
) {
    val context = LocalContext.current
    val name = LocalPreferences.getAccountName(context, npub)
    var textFieldvalue by remember {
        mutableStateOf(TextFieldValue(name))
    }
    Dialog(
        onDismissRequest = {
            onClose()
        },
    ) {
        Surface {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CloseButton(
                        onCancel = {
                            onClose()
                        },
                    )
                    PostButton(
                        isActive = true,
                        onPost = {
                            onPost(textFieldvalue.text)
                        },
                    )
                }

                Column(
                    modifier = Modifier
                        .padding(horizontal = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    OutlinedTextField(
                        value = textFieldvalue.text,
                        onValueChange = {
                            textFieldvalue = TextFieldValue(it)
                        },
                        label = {
                            Text("Name")
                        },
                    )
                }
            }
        }
    }
}

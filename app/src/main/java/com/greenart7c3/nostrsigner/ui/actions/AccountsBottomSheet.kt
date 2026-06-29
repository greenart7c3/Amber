package com.greenart7c3.nostrsigner.ui.actions

import android.content.ClipData
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.NavHostControllerWrapper
import com.greenart7c3.nostrsigner.ui.components.ActiveMarker
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.fromHex
import com.greenart7c3.nostrsigner.ui.verticalScrollbar
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsBottomSheet(
    sheetState: SheetState,
    account: Account,
    accountStateViewModel: AccountStateViewModel,
    navController: NavHostControllerWrapper,
    onClose: () -> Unit,
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = {
            onClose()
        },
    ) {
        CompositionLocalProvider(
            LocalDensity provides Density(
                LocalDensity.current.density,
                1f,
            ),
        ) {
            val context = LocalContext.current
            val accounts = LocalPreferences.allSavedAccounts(context)
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .verticalScrollbar(scrollState)
                    .verticalScroll(scrollState),
            ) {
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
                    // Reading the name/picture from SharedPreferences touches disk, so do it
                    // off the main thread instead of synchronously during composition.
                    val accountInfo by produceState("" to "", acc.npub) {
                        value = withContext(Dispatchers.IO) {
                            LocalPreferences.getAccountName(context, acc.npub) to
                                LocalPreferences.getProfileUrl(context, acc.npub)
                        }
                    }
                    val name = accountInfo.first
                    val profileUrl = accountInfo.second
                    val borderColor = remember(acc.npub) {
                        Color.fromHex(acc.npub.bechToBytes().toHexKey().slice(0..5))
                    }
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
                                if (profileUrl.isNotBlank() && !BuildFlavorChecker.isOfflineFlavor()) {
                                    SubcomposeAsyncImage(
                                        model = profileUrl,
                                        contentDescription = stringResource(R.string.account_picture),
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .clip(RoundedCornerShape(50))
                                            .height(40.dp)
                                            .width(40.dp),
                                        error = {
                                            Icon(
                                                Icons.Outlined.Person,
                                                stringResource(R.string.account_picture),
                                                modifier = Modifier
                                                    .border(1.dp, borderColor, CircleShape)
                                                    .height(40.dp)
                                                    .width(40.dp),
                                            )
                                        },
                                    )
                                } else {
                                    Icon(
                                        Icons.Outlined.Person,
                                        stringResource(R.string.account_picture),
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .border(1.dp, borderColor, CircleShape)
                                            .height(40.dp)
                                            .width(40.dp),
                                    )
                                }
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

                        IconButton(
                            onClick = {
                                scope.launch {
                                    clipboardManager.setClipEntry(
                                        ClipEntry(
                                            ClipData.newPlainText(
                                                "",
                                                acc.npub,
                                            ),
                                        ),
                                    )
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.copy_to_clipboard),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        IconButton(
                            onClick = {
                                onClose()
                                navController.navController.navigate("EditProfile/${acc.npub}")
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
                    TextButton(
                        onClick = {
                            onClose()
                            navController.navController.navigate(Route.Login.route)
                        },
                        content = {
                            Text(stringResource(R.string.add_new_account))
                        },
                    )
                }
            }
        }
    }
}

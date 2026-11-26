package com.greenart7c3.nostrsigner.ui.actions

import android.content.ClipData
import android.content.Context
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.BottomSheetDefaults
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.greenart7c3.nostrsigner.AccountInfo
import com.greenart7c3.nostrsigner.AccountStorage
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.account_picture
import com.greenart7c3.nostrsigner.commonsui.add_new_account
import com.greenart7c3.nostrsigner.commonsui.copy_to_clipboard
import com.greenart7c3.nostrsigner.commonsui.edit_name
import com.greenart7c3.nostrsigner.commonsui.select_account
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.NavHostControllerWrapper
import com.greenart7c3.nostrsigner.ui.components.AccountSearchField
import com.greenart7c3.nostrsigner.ui.components.ActiveMarker
import com.greenart7c3.nostrsigner.ui.components.FavoriteAccountButton
import com.greenart7c3.nostrsigner.ui.components.NoAccountsFound
import com.greenart7c3.nostrsigner.ui.components.ProfileSubscriptionEffect
import com.greenart7c3.nostrsigner.ui.components.accountMatchesSearch
import com.greenart7c3.nostrsigner.ui.components.favoritesFirst
import com.greenart7c3.nostrsigner.ui.components.rememberAccountNames
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.amoledBorder
import com.greenart7c3.nostrsigner.ui.theme.fromHex
import com.greenart7c3.nostrsigner.ui.theme.outlineIfAny
import com.greenart7c3.nostrsigner.ui.verticalScrollbar
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsBottomSheet(
    sheetState: SheetState,
    account: Account,
    onSwitchAccount: (npub: String) -> Unit,
    onLogOff: (npub: String) -> Unit,
    navController: NavHostControllerWrapper,
    onClose: () -> Unit,
) {
    val clipboardManager = LocalClipboard.current
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        modifier = Modifier.outlineIfAny(amoledBorder(), BottomSheetDefaults.ExpandedShape),
        sheetState = sheetState,
        onDismissRequest = { onClose() },
    ) {
        SelectAccountTitle()
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
            val context = LocalContext.current
            val favorites by SavedAccounts.favorites(context).collectAsStateWithLifecycle()
            val savedAccounts = remember { SavedAccounts.all(context) }
            val loadedAccounts by rememberLoadedAccounts(context, savedAccounts)
            val loadedByNpub = remember(loadedAccounts) { loadedAccounts.associateBy { it.npub } }
            val names by rememberAccountNames(loadedAccounts)
            var query by remember { mutableStateOf("") }
            val accounts = savedAccounts.matching(query, names, favorites)
            val scrollState = rememberScrollState()

            if (savedAccounts.size > 1) {
                AccountSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }

            AccountsList(
                accounts = accounts,
                scrollState = scrollState,
                onAddAccount = { navController.closeAndNavigate(onClose, Route.Login.route) },
            ) { acc ->
                AccountRow(
                    acc = acc,
                    loadedAccount = loadedByNpub[acc.npub],
                    name = names[acc.npub].orEmpty(),
                    activeAccount = account,
                    onSwitch = { onSwitchAccount(acc.npub) },
                ) {
                    AccountRowActions(
                        acc = acc,
                        isFavorite = acc.npub in favorites,
                        onCopy = { scope.launch { clipboardManager.copyNpub(acc.npub) } },
                        onEdit = { navController.closeAndNavigate(onClose, "EditProfile/${acc.npub}") },
                        onLogOff = onLogOff,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberLoadedAccounts(
    context: Context,
    savedAccounts: List<AccountInfo>,
): State<List<Account>> = produceState(emptyList<Account>(), savedAccounts) {
    value = withContext(Dispatchers.IO) {
        savedAccounts.mapNotNull { AccountStorage.load(context, it.npub) }
    }
}

private fun List<AccountInfo>.matching(
    query: String,
    names: Map<String, String>,
    favorites: Set<String>,
): List<AccountInfo> = filter { accountMatchesSearch(query, it.npub, names[it.npub].orEmpty()) }
    .favoritesFirst(favorites) { it.npub }

private fun NavHostControllerWrapper.closeAndNavigate(
    onClose: () -> Unit,
    route: String,
) {
    onClose()
    navController.navigate(route)
}

private suspend fun Clipboard.copyNpub(npub: String) {
    setClipEntry(
        ClipEntry(
            ClipData.newPlainText(
                "",
                npub,
            ),
        ),
    )
}

@Composable
private fun SelectAccountTitle(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(Res.string.select_account), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AccountsList(
    accounts: List<AccountInfo>,
    scrollState: ScrollState,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier,
    accountRow: @Composable (AccountInfo) -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScrollbar(scrollState)
            .verticalScroll(scrollState),
    ) {
        if (accounts.isEmpty()) {
            NoAccountsFound()
        }
        accounts.forEach { acc ->
            key(acc.npub) {
                accountRow(acc)
            }
        }
        AddAccountButton(onClick = onAddAccount)
    }
}

@Composable
private fun AccountRow(
    acc: AccountInfo,
    loadedAccount: Account?,
    name: String,
    activeAccount: Account,
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    loadedAccount?.let { ProfileSubscriptionEffect(it) }
    val profileUrl = loadedAccount?.picture?.collectAsStateWithLifecycle()?.value ?: ""
    val borderColor = remember(acc.npub) {
        Color.fromHex(acc.npub.bechToBytes().toHexKey().slice(0..5))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                onSwitch()
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
                AccountPicture(profileUrl = profileUrl, borderColor = borderColor)
                Column(modifier = Modifier.weight(1f)) {
                    if (name.isNotBlank()) {
                        Text(name)
                    }
                    Text(acc.npub.toShortenHex())
                }
                Column(modifier = Modifier.width(32.dp)) {
                    ActiveMarker(acc, activeAccount)
                }
            }
        }

        actions()
    }
}

@Composable
private fun AccountPicture(
    profileUrl: String,
    borderColor: Color,
    modifier: Modifier = Modifier,
) {
    if (profileUrl.isNotBlank() && !BuildFlavorChecker.isOfflineFlavor()) {
        SubcomposeAsyncImage(
            model = profileUrl,
            contentDescription = stringResource(Res.string.account_picture),
            modifier = modifier
                .padding(end = 8.dp)
                .clip(RoundedCornerShape(50))
                .height(40.dp)
                .width(40.dp),
            error = {
                Icon(
                    Icons.Outlined.Person,
                    stringResource(Res.string.account_picture),
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
            stringResource(Res.string.account_picture),
            modifier = modifier
                .padding(end = 8.dp)
                .border(1.dp, borderColor, CircleShape)
                .height(40.dp)
                .width(40.dp),
        )
    }
}

@Composable
private fun RowScope.AccountRowActions(
    acc: AccountInfo,
    isFavorite: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onLogOff: (npub: String) -> Unit,
) {
    FavoriteAccountButton(acc.npub, isFavorite)

    IconButton(onClick = onCopy) {
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = stringResource(Res.string.copy_to_clipboard),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }

    IconButton(onClick = onEdit) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = stringResource(Res.string.edit_name),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
    LogoutButton(acc, onLogOff)
}

@Composable
private fun AddAccountButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onClick,
            content = {
                Text(stringResource(Res.string.add_new_account))
            },
        )
    }
}

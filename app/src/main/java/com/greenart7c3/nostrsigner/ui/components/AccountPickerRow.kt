package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.select_account
import com.greenart7c3.nostrsigner.commonsui.switch_account
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.theme.amoledBorder
import com.greenart7c3.nostrsigner.ui.theme.isAmberDarkTheme
import com.greenart7c3.nostrsigner.ui.theme.outlineIfAny
import com.greenart7c3.nostrsigner.ui.theme.primaryVariant
import com.greenart7c3.nostrsigner.ui.verticalScrollbar
import org.jetbrains.compose.resources.stringResource

/**
 * Single tappable account row with avatar + name + Switch pill.
 * When [accounts] has more than one entry, tapping the row opens a bottom-sheet
 * picker. With a single account the row is inert and the Switch pill is hidden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPickerRow(
    accounts: List<Account>,
    selectedAccountIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canSwitch = accounts.size > 1
    val selected = accounts.getOrNull(selectedAccountIndex) ?: accounts.first()

    SelectedAccountRow(
        account = selected,
        canSwitch = canSwitch,
        onClick = { sheetOpen = true },
        modifier = modifier,
    )

    if (sheetOpen) {
        AccountPickerSheet(
            accounts = accounts,
            selectedAccountIndex = selectedAccountIndex,
            sheetState = sheetState,
            onSelect = { index ->
                onSelect(index)
                sheetOpen = false
            },
            onDismiss = { sheetOpen = false },
        )
    }
}

@Composable
private fun SelectedAccountRow(
    account: Account,
    canSwitch: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(enabled = canSwitch) { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfilePictureIcon(account = account)
        Spacer(Modifier.width(12.dp))
        val name by account.name.collectAsStateWithLifecycle()
        AccountNameLines(
            name = name,
            npub = account.npub,
            nameFontSize = 16.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (canSwitch) {
            Spacer(Modifier.width(8.dp))
            SwitchAccountPill()
        }
    }
}

@Composable
private fun AccountNameLines(
    name: String,
    npub: String,
    nameFontSize: TextUnit,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        Text(
            text = name.ifBlank { npub.toShortenHex() },
            fontWeight = FontWeight.Medium,
            fontSize = nameFontSize,
            maxLines = maxLines,
        )
        if (name.isNotBlank()) {
            Text(
                text = npub.toShortenHex(),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = maxLines,
            )
        }
    }
}

@Composable
private fun SwitchAccountPill(modifier: Modifier = Modifier) {
    val pillContent = if (isAmberDarkTheme()) {
        MaterialTheme.colorScheme.primary
    } else {
        primaryVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.CompareArrows,
            contentDescription = null,
            tint = pillContent,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(Res.string.switch_account),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = pillContent,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPickerSheet(
    accounts: List<Account>,
    selectedAccountIndex: Int,
    sheetState: SheetState,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        modifier = modifier.outlineIfAny(amoledBorder(), BottomSheetDefaults.ExpandedShape),
        sheetState = sheetState,
        onDismissRequest = onDismiss,
    ) {
        Text(
            text = stringResource(Res.string.select_account),
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        )
        var query by remember { mutableStateOf("") }
        val names by rememberAccountNames(accounts)
        AccountSearchField(
            query = query,
            onQueryChange = { query = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        AccountPickerList(
            accounts = accounts,
            selectedAccountIndex = selectedAccountIndex,
            query = query,
            names = names,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun AccountPickerList(
    accounts: List<Account>,
    selectedAccountIndex: Int,
    query: String,
    names: Map<String, String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScrollbar(scrollState)
            .verticalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        val favorites by SavedAccounts.favorites(LocalContext.current).collectAsStateWithLifecycle()
        val shownWithSelectableIndex = accounts.withIndex().toList()
            .filter { (_, acc) -> accountMatchesSearch(query, acc.npub, names[acc.npub].orEmpty()) }
            .favoritesFirst(favorites) { it.value.npub }
        if (shownWithSelectableIndex.isEmpty()) {
            NoAccountsFound()
        }
        shownWithSelectableIndex.forEach { (index, acc) ->
            key(acc.npub) {
                AccountPickerItem(
                    account = acc,
                    isSelected = index == selectedAccountIndex,
                    isFavorite = acc.npub in favorites,
                    onClick = { onSelect(index) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Suppress("NoComments")
@Composable
private fun AccountPickerItem(
    account: Account,
    isSelected: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name by account.name.collectAsStateWithLifecycle()
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Clipped before the click, so the ripple keeps the rounded shape.
            .clip(RoundedCornerShape(10.dp))
            .background(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(10.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfilePictureIcon(account = account)
        Spacer(Modifier.width(12.dp))
        AccountNameLines(
            name = name,
            npub = account.npub,
            nameFontSize = 15.sp,
            maxLines = Int.MAX_VALUE,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF1D8802),
            )
        }
        FavoriteAccountButton(account.npub, isFavorite)
    }
}

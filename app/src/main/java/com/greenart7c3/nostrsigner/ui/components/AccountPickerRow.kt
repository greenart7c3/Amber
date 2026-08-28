package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.theme.primaryVariant
import com.greenart7c3.nostrsigner.ui.verticalScrollbar

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
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canSwitch = accounts.size > 1
    val selected = accounts.getOrNull(selectedAccountIndex) ?: accounts.first()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(enabled = canSwitch) { sheetOpen = true }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfilePictureIcon(account = selected)
        Spacer(Modifier.width(12.dp))
        val name by selected.name.collectAsStateWithLifecycle()
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = name.ifBlank { selected.npub.toShortenHex() },
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                maxLines = 1,
            )
            if (name.isNotBlank()) {
                Text(
                    text = selected.npub.toShortenHex(),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (canSwitch) {
            Spacer(Modifier.width(8.dp))
            val pillContent = if (isSystemInDarkTheme()) {
                MaterialTheme.colorScheme.primary
            } else {
                primaryVariant
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
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
                    text = stringResource(R.string.switch_account),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = pillContent,
                )
            }
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { sheetOpen = false },
        ) {
            Text(
                text = stringResource(R.string.select_account),
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            )
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScrollbar(scrollState)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                accounts.forEachIndexed { index, acc ->
                    val name by acc.name.collectAsStateWithLifecycle()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(index)
                                sheetOpen = false
                            }
                            .background(
                                color = if (index == selectedAccountIndex) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(10.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfilePictureIcon(account = acc)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name.ifBlank { acc.npub.toShortenHex() },
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                            )
                            if (name.isNotBlank()) {
                                Text(
                                    text = acc.npub.toShortenHex(),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (index == selectedAccountIndex) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF1D8802),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

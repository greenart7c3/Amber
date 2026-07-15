package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.ThemePreviews

@Composable
fun SignerConnectAppTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Tab(
        modifier = modifier.padding(horizontal = 16.dp),
        selected = selected,
        onClick = onClick,
        text = {
            Text(
                text = text,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
    )
}

@ThemePreviews
@Composable
fun SignerConnectAppTabPreview() {
    AmberPreview {
        PrimaryTabRow(selectedTabIndex = 0) {
            SignerConnectAppTab(
                text = "Applications",
                selected = true,
                onClick = {},
            )
            SignerConnectAppTab(
                text = "Activity",
                selected = false,
                onClick = {},
            )
        }
    }
}

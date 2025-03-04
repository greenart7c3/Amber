package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.R

@Composable
fun RememberMyChoice(
    shouldRunAcceptOrReject: Boolean?,
    remember: Boolean,
    packageName: String?,
    alwaysShow: Boolean = false,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onChanged: () -> Unit,
) {
    if (shouldRunAcceptOrReject != null) {
        LaunchedEffect(Unit) {
            if (shouldRunAcceptOrReject) {
                onAccept()
            } else {
                onReject()
            }
        }
    }
    if (packageName != null || alwaysShow) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .clickable {
                    onChanged()
                },
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.remember_my_choice_and_don_t_ask_again),
            )
            Switch(
                checked = remember,
                onCheckedChange = {
                    onChanged()
                },
            )
        }
    }
}

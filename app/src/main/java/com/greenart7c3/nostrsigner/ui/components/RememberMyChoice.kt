package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun RememberMyChoice(
    shouldRunOnAccept: Boolean,
    remember: MutableState<Boolean>,
    packageName: String?,
    onAccept: () -> Unit
) {
    if (shouldRunOnAccept) {
        LaunchedEffect(Unit) {
            onAccept()
        }
    }
    if (packageName != null) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable {
                    remember.value = !remember.value
                }
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = "Remember my choice and don't ask again"
            )
            Switch(
                checked = remember.value,
                onCheckedChange = {
                    remember.value = !remember.value
                }
            )
        }
    }
}

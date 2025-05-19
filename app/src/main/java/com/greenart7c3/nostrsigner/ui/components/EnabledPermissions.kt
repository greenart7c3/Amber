package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.models.Permission

@Composable
fun EnabledPermissions(
    localPermissions: List<Permission>,
) {
    val enabledPermissions = localPermissions.map {
        remember { mutableStateOf(it.checked) }
    }
    if (localPermissions.isNotEmpty()) {
        localPermissions.forEachIndexed { index, permission ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                border = BorderStroke(1.dp, Color.LightGray),
                colors = CardDefaults.cardColors().copy(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable {
                            permission.checked = !permission.checked
                            enabledPermissions[index].value = permission.checked
                        },
                ) {
                    Checkbox(
                        checked = enabledPermissions[index].value,
                        onCheckedChange = { _ ->
                            permission.checked = !permission.checked
                            enabledPermissions[index].value = permission.checked
                        },
                    )
                    Text(
                        modifier = Modifier.weight(1f),
                        text = permission.toLocalizedString(LocalContext.current),
                    )
                }
            }
        }
    }
}

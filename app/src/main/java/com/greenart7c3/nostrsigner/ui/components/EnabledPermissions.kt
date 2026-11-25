package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.commonsui.select_deselect_all
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.PreviewsLightDark
import org.jetbrains.compose.resources.stringResource

@Composable
fun EnabledPermissions(
    localPermissions: List<Permission>,
    modifier: Modifier = Modifier,
) {
    val enabledPermissions = localPermissions.map {
        remember { mutableStateOf(it.checked) }
    }

    val allCheckedState = when {
        enabledPermissions.all { it.value } -> ToggleableState.On
        enabledPermissions.none { it.value } -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }

    val toggleAll = {
        val newValue = allCheckedState != ToggleableState.On
        localPermissions.forEachIndexed { index, item ->
            item.checked = newValue
            enabledPermissions[index].value = newValue
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(8.dp),
    ) {
        SelectAllRow(
            state = allCheckedState,
            onToggle = { toggleAll() },
        )

        LazyColumn(
            Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(localPermissions) { index, item ->
                PermissionCard(
                    checked = enabledPermissions[index].value,
                    text = item.localizedBlocking(),
                    onToggle = {
                        item.checked = !item.checked
                        enabledPermissions[index].value = item.checked
                    },
                )
            }
        }
    }
}

@Composable
private fun SelectAllRow(
    state: ToggleableState,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggle() },
    ) {
        TriStateCheckbox(
            state = state,
            onClick = { onToggle() },
        )
        Text(stringResource(Res.string.select_deselect_all))
    }
}

@Composable
private fun PermissionCard(
    checked: Boolean,
    text: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
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
                .clickable { onToggle() },
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { _ -> onToggle() },
            )
            Text(
                modifier = Modifier.weight(1f),
                text = text,
            )
        }
    }
}

@PreviewsLightDark
@Composable
private fun EnabledPermissionsPreview() {
    AmberPreview {
        EnabledPermissions(
            localPermissions = listOf(
                Permission("get_public_key", null),
                Permission("sign_event", 1),
                Permission("nip04_encrypt", null, checked = false),
            ),
        )
    }
}

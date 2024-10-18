package com.greenart7c3.nostrsigner.ui.actions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.greenart7c3.nostrsigner.R

@Composable
fun RemoveAllPermissionsDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = stringResource(R.string.remove))
        },
        text = {
            Text(text = "Are you sure you want to remove all permissions from this application?")
        },
        onDismissRequest = {
            onCancel()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                },
            ) {
                Text(text = stringResource(R.string.remove))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onCancel()
                },
            ) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

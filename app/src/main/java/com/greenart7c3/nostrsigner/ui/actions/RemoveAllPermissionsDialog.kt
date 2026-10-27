package com.greenart7c3.nostrsigner.ui.actions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.ui.theme.amoledBorder
import com.greenart7c3.nostrsigner.ui.theme.outlineIfAny

@Composable
fun RemoveAllPermissionsDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.outlineIfAny(amoledBorder(), AlertDialogDefaults.shape),
        title = {
            Text(text = stringResource(R.string.remove))
        },
        text = {
            Text(text = stringResource(R.string.remove_all_message))
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

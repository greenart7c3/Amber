package com.greenart7c3.nostrsigner.ui.actions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.cancel
import com.greenart7c3.nostrsigner.commonsui.remove
import com.greenart7c3.nostrsigner.commonsui.remove_all_message
import com.greenart7c3.nostrsigner.ui.theme.amoledBorder
import com.greenart7c3.nostrsigner.ui.theme.outlineIfAny
import org.jetbrains.compose.resources.stringResource

@Composable
fun RemoveAllPermissionsDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.outlineIfAny(amoledBorder(), AlertDialogDefaults.shape),
        title = {
            Text(text = stringResource(Res.string.remove))
        },
        text = {
            Text(text = stringResource(Res.string.remove_all_message))
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
                Text(text = stringResource(Res.string.remove))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onCancel()
                },
            ) {
                Text(text = stringResource(Res.string.cancel))
            }
        },
    )
}

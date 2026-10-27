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
fun LogoutDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.outlineIfAny(amoledBorder(), AlertDialogDefaults.shape),
        title = {
            Text(text = stringResource(R.string.logout))
        },
        text = {
            Text(
                text = stringResource(
                    R.string.logging_out_deletes_all_your_local_information_make_sure_to_have_your_private_keys_backed_up_to_avoid_losing_your_account_do_you_want_to_continue,
                ),
            )
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
                Text(text = stringResource(R.string.logout))
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

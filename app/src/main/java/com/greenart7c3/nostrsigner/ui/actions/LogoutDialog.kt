package com.greenart7c3.nostrsigner.ui.actions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.cancel
import com.greenart7c3.nostrsigner.commonsui.logging_out_deletes_all_your_local_information_make_sure_to_have_your_private_keys_backed_up_to_avoid_losing_your_account_do_you_want_to_continue
import com.greenart7c3.nostrsigner.commonsui.logout
import com.greenart7c3.nostrsigner.ui.theme.amoledBorder
import com.greenart7c3.nostrsigner.ui.theme.outlineIfAny
import org.jetbrains.compose.resources.stringResource

@Composable
fun LogoutDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.outlineIfAny(amoledBorder(), AlertDialogDefaults.shape),
        title = {
            Text(text = stringResource(Res.string.logout))
        },
        text = {
            Text(
                text = stringResource(
                    Res.string.logging_out_deletes_all_your_local_information_make_sure_to_have_your_private_keys_backed_up_to_avoid_losing_your_account_do_you_want_to_continue,
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
                Text(text = stringResource(Res.string.logout))
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

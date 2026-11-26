package com.greenart7c3.nostrsigner.ui.actions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.account_backup_tips3_md
import com.greenart7c3.nostrsigner.commonsui.encryption_password
import com.greenart7c3.nostrsigner.commonsui.hide_password
import com.greenart7c3.nostrsigner.commonsui.show_password
import com.greenart7c3.nostrsigner.ui.components.MarkdownText
import com.greenart7c3.nostrsigner.ui.theme.amoledBorder
import com.greenart7c3.nostrsigner.ui.theme.outlineIfAny
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PasswordCard(
    password: TextFieldValue,
    errorMessage: String,
    showCharsPassword: Boolean,
    onPasswordChange: (TextFieldValue) -> Unit,
    onToggleVisibility: () -> Unit,
    onDone: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().outlineIfAny(amoledBorder(), CardDefaults.elevatedShape)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(Res.string.encryption_password),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val tipsContent = stringResource(Res.string.account_backup_tips3_md)
            MarkdownText(
                markdown = tipsContent,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            BackupPasswordField(password, errorMessage, showCharsPassword, onPasswordChange, onToggleVisibility, onDone)
        }
    }
}

@Composable
private fun BackupPasswordField(
    password: TextFieldValue,
    errorMessage: String,
    showCharsPassword: Boolean,
    onPasswordChange: (TextFieldValue) -> Unit,
    onToggleVisibility: () -> Unit,
    onDone: () -> Unit,
) {
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentType = ContentType.Password },
        value = password,
        onValueChange = onPasswordChange,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        placeholder = { Text(text = stringResource(Res.string.encryption_password)) },
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (showCharsPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (showCharsPassword) {
                        stringResource(Res.string.hide_password)
                    } else {
                        stringResource(Res.string.show_password)
                    },
                )
            }
        },
        visualTransformation = if (showCharsPassword) VisualTransformation.None else PasswordVisualTransformation(),
        isError = errorMessage.isNotBlank(),
        supportingText = if (errorMessage.isNotBlank()) {
            { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }
        } else {
            null
        },
        singleLine = true,
    )
}

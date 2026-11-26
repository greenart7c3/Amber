package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.body_text_optional
import com.greenart7c3.nostrsigner.commonsui.bug_report
import com.greenart7c3.nostrsigner.commonsui.enhancement_request
import com.greenart7c3.nostrsigner.commonsui.failed_to_send_event
import com.greenart7c3.nostrsigner.commonsui.feedback_sent
import com.greenart7c3.nostrsigner.commonsui.feedback_type
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.required
import com.greenart7c3.nostrsigner.commonsui.send
import com.greenart7c3.nostrsigner.commonsui.subject
import com.greenart7c3.nostrsigner.commonsui.warning
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.FeedbackType
import com.greenart7c3.nostrsigner.service.FeedbackSender
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun FeedbackScreen(
    account: Account,
    onDismiss: () -> Unit,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val relayClient = LocalRelayClient.current
    val settingsStore = LocalSettingsStore.current
    val appScope = LocalAppScope.current
    var header by remember { mutableStateOf(TextFieldValue("")) }
    var body by remember { mutableStateOf(TextFieldValue("")) }
    var feedbackType by remember { mutableStateOf(FeedbackType.BUG_REPORT) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        FeedbackForm(
            feedbackType = feedbackType,
            onFeedbackTypeSelect = { feedbackType = it },
            header = header,
            onHeaderChange = { header = it },
            body = body,
            onBodyChange = { body = it },
            modifier = Modifier.weight(1f),
        )

        FeedbackSendRow(
            enabled = header.text.isNotBlank(),
            onSend = {
                appScope.launch {
                    sendFeedback(
                        onLoading = onLoading,
                        onDismiss = onDismiss,
                        send = {
                            FeedbackSender(relayClient, settingsStore).send(
                                header.text,
                                body.text,
                                feedbackType,
                                account,
                            )
                        },
                    )
                }
            },
        )
    }
}

private suspend fun sendFeedback(
    onLoading: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    send: suspend () -> Boolean,
) {
    try {
        onLoading(true)
        val result = send()
        if (result) {
            ToastManager.toast(
                getStringBlocking(Res.string.warning),
                getStringBlocking(Res.string.feedback_sent),
            )
            onLoading(false)
            onDismiss()
        } else {
            ToastManager.toast(
                getStringBlocking(Res.string.warning),
                getStringBlocking(Res.string.failed_to_send_event),
            )
            onLoading(false)
        }
    } catch (e: Exception) {
        onLoading(false)
        if (e is CancellationException) throw e
    }
}

@Composable
private fun FeedbackForm(
    feedbackType: FeedbackType,
    onFeedbackTypeSelect: (FeedbackType) -> Unit,
    header: TextFieldValue,
    onHeaderChange: (TextFieldValue) -> Unit,
    body: TextFieldValue,
    onBodyChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FeedbackTypePicker(
            selected = feedbackType,
            onSelect = onFeedbackTypeSelect,
        )

        HorizontalDivider()

        OutlinedTextField(
            value = header,
            onValueChange = onHeaderChange,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            label = {
                Text(stringResource(Res.string.subject))
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            isError = header.text.isBlank(),
            supportingText = if (header.text.isBlank()) {
                { Text(stringResource(Res.string.required)) }
            } else {
                null
            },
        )

        OutlinedTextField(
            value = body,
            onValueChange = onBodyChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            label = {
                Text(stringResource(Res.string.body_text_optional))
            },
            textStyle = MaterialTheme.typography.bodyLarge,
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun FeedbackTypePicker(
    selected: FeedbackType,
    onSelect: (FeedbackType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.feedback_type),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FeedbackType.entries.forEach { type ->
                FeedbackTypeChip(
                    type = type,
                    selected = selected == type,
                    onClick = { onSelect(type) },
                )
            }
        }
    }
}

@Composable
private fun FeedbackTypeChip(
    type: FeedbackType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = {
            Text(
                text = stringResource(
                    when (type) {
                        FeedbackType.BUG_REPORT -> Res.string.bug_report
                        FeedbackType.ENHANCEMENT_REQUEST -> Res.string.enhancement_request
                    },
                ),
            )
        },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun FeedbackSendRow(
    enabled: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        AmberButton(
            enabled = enabled,
            text = stringResource(Res.string.send),
            onClick = onSend,
        )
    }
}

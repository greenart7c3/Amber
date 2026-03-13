package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.FeedbackType
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

@Composable
fun FeedbackScreen(
    modifier: Modifier = Modifier,
    account: Account,
    onDismiss: () -> Unit,
    onLoading: (Boolean) -> Unit,
) {
    var header by remember { mutableStateOf(TextFieldValue("")) }
    var body by remember { mutableStateOf(TextFieldValue("")) }
    var feedbackType by remember { mutableStateOf(FeedbackType.BUG_REPORT) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.feedback_type),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    FeedbackType.entries.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = feedbackType == type,
                            onClick = { feedbackType = type },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = FeedbackType.entries.size,
                            ),
                            label = {
                                Text(
                                    text = stringResource(
                                        when (type) {
                                            FeedbackType.BUG_REPORT -> R.string.bug_report
                                            FeedbackType.ENHANCEMENT_REQUEST -> R.string.enhancement_request
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
            }

            HorizontalDivider()

            OutlinedTextField(
                value = header,
                onValueChange = { header = it },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                label = {
                    Text(stringResource(R.string.subject))
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                isError = header.text.isBlank(),
                supportingText = if (header.text.isBlank()) {
                    { Text(stringResource(R.string.required)) }
                } else {
                    null
                },
            )

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                label = {
                    Text(stringResource(R.string.body_text_optional))
                },
                textStyle = MaterialTheme.typography.bodyLarge,
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            AmberButton(
                enabled = header.text.isNotBlank(),
                text = stringResource(R.string.send),
                onClick = {
                    Amber.instance.applicationIOScope.launch {
                        try {
                            onLoading(true)
                            val result = Amber.instance.sendFeedBack(
                                header.text,
                                body.text,
                                feedbackType,
                                account,
                            )
                            if (result) {
                                ToastManager.toast(
                                    Amber.instance.getString(R.string.warning),
                                    Amber.instance.getString(R.string.feedback_sent),
                                )
                                onLoading(false)
                                onDismiss()
                            } else {
                                ToastManager.toast(
                                    Amber.instance.getString(R.string.warning),
                                    Amber.instance.getString(R.string.failed_to_send_event),
                                )
                                onLoading(false)
                            }
                        } catch (e: Exception) {
                            onLoading(false)
                            if (e is CancellationException) throw e
                        }
                    }
                },
            )
        }
    }
}

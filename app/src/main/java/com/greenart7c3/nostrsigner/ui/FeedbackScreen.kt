package com.greenart7c3.nostrsigner.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.FeedbackType
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import com.greenart7c3.nostrsigner.ui.theme.light
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun FeedbackScreen(
    modifier: Modifier = Modifier,
    account: Account,
    accountStateViewModel: AccountStateViewModel,
    onDismiss: () -> Unit,
    onLoading: (Boolean) -> Unit,
) {
    var header by remember { mutableStateOf(TextFieldValue("")) }
    var body by remember { mutableStateOf(TextFieldValue("")) }
    var feedbackType by remember { mutableStateOf(FeedbackType.BUG_REPORT) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize(),
    ) {
        val maxHeight = maxHeight

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = true),
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item {
                        NamedRadio(
                            isSelected = feedbackType == FeedbackType.BUG_REPORT,
                            name = stringResource(R.string.bug_report),
                            onClick = {
                                feedbackType = FeedbackType.BUG_REPORT
                            },
                        )
                    }
                    item { Spacer(modifier = Modifier.width(8.dp)) }
                    item {
                        NamedRadio(
                            isSelected = feedbackType == FeedbackType.ENHANCEMENT_REQUEST,
                            name = stringResource(id = R.string.enhancement_request),
                            onClick = {
                                feedbackType = FeedbackType.ENHANCEMENT_REQUEST
                            },
                        )
                    }
                }

                TextField(
                    header,
                    onValueChange = { header = it },
                    modifier = Modifier
                        .fillMaxWidth(),
                    maxLines = 3,
                    placeholder = {
                        Text(
                            stringResource(id = R.string.subject),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.light(),
                        )
                    },
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                )

                val scrollState = rememberScrollState()
                TextField(
                    value = body,
                    onValueChange = { body = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(maxHeight * 0.4f)
                        .verticalScroll(scrollState),
                    placeholder = {
                        Text(
                            stringResource(id = R.string.body_text_optional),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.light(),
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                )
            }

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
                                accountStateViewModel.toast(
                                    Amber.instance.getString(R.string.warning),
                                    Amber.instance.getString(R.string.feedback_sent),
                                )
                                onLoading(false)
                                onDismiss()
                            } else {
                                accountStateViewModel.toast(
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

// taken from https://github.com/dluvian/voyage
@Composable
fun NamedRadio(
    isSelected: Boolean,
    name: String,
    onClick: () -> Unit,
    isEnabled: Boolean = true,
) {
    NamedItem(
        modifier = Modifier.clickable(onClick = onClick),
        name = name,
        item = {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                enabled = isEnabled,
            )
        },
    )
}

// taken from https://github.com/dluvian/voyage
@Composable
fun NamedItem(
    name: String,
    item: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        item()
        Text(text = name, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

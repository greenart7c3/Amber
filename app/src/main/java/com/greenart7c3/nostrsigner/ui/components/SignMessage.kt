package com.greenart7c3.nostrsigner.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.SignerType
import kotlinx.coroutines.launch

@Composable
fun SignMessage(
    paddingValues: PaddingValues,
    content: String,
    shouldRunOnAccept: Boolean?,
    remember: MutableState<Boolean>,
    packageName: String?,
    applicationName: String?,
    appName: String,
    type: SignerType,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    var showMore by androidx.compose.runtime.remember {
        mutableStateOf(false)
    }
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(paddingValues),
    ) {
        val message = stringResource(R.string.sign_message)

        packageName?.let {
            Text(
                modifier = Modifier
                    .fillMaxWidth(),
                text = it,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(4.dp))
        }

        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(applicationName ?: appName)
                }
                append(" requests $message")
            },
            fontSize = 18.sp,
        )
        Spacer(Modifier.size(4.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            Column(Modifier.padding(6.dp)) {
                Text(
                    "Event content",
                    fontWeight = FontWeight.Bold,
                )

                Text(
                    content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }

        RawJsonButton(
            onCLick = {
                showMore = !showMore
            },
            if (!showMore) stringResource(R.string.show_details) else stringResource(R.string.hide_details),
        )
        if (showMore) {
            RawJson(
                content,
                "",
                Modifier.height(200.dp),
                stringResource(R.string.content),
                type,
            ) {
                clipboardManager.setText(AnnotatedString(content))

                coroutineScope.launch {
                    Toast.makeText(
                        context,
                        context.getString(R.string.data_copied_to_the_clipboard),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        RememberMyChoice(
            shouldRunOnAccept,
            remember.value,
            packageName,
            false,
            onAccept,
            onReject,
        ) {
            remember.value = !remember.value
        }

        AcceptRejectButtons(
            onAccept,
            onReject,
        )
    }
}

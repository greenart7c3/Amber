package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.vitorpamplona.quartz.encoders.toNpub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun LogsScreen(
    account: Account,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp),
    ) {
        val logsFlow = NostrSigner.getInstance().getDatabase(account.keyPair.pubKey.toNpub()).applicationDao().getLogs()
        val logs = logsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

        LazyColumn(
            Modifier.weight(1f),
        ) {
            items(logs.value.size) { index ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                ) {
                    Column(Modifier.padding(6.dp)) {
                        val log = logs.value[index]
                        Text(
                            buildAnnotatedString {
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("Date: ")
                                }
                                append(TimeUtils.convertLongToDateTime(log.time))
                            },
                        )
                        Text(
                            buildAnnotatedString {
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("URL: ")
                                }
                                append(log.url)
                            },
                        )
                        Text(
                            buildAnnotatedString {
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("Type: ")
                                }
                                append(log.type)
                            },
                        )
                        Text(
                            buildAnnotatedString {
                                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("Message: ")
                                }
                                append(log.message)
                            },
                        )
                    }
                }
            }
        }
        Box(
            Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        NostrSigner.getInstance().getDatabase(account.keyPair.pubKey.toNpub()).applicationDao().clearLogs()
                    }
                },
            ) {
                Text(text = stringResource(R.string.clear_logs))
            }
        }
    }
}

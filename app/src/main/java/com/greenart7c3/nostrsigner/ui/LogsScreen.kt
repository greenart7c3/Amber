package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.ui.components.AmberButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun LogsScreen(
    paddingValues: PaddingValues,
    account: Account,
) {
    val scope = rememberCoroutineScope()
    val logsFlow = NostrSigner.instance.getDatabase(account.npub).applicationDao().getLogs()
    val logs = logsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val state = rememberLazyListState()

    LazyColumn(
        state = state,
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = paddingValues,
    ) {
        item {
            AmberButton(
                modifier = Modifier.padding(bottom = 8.dp),
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        NostrSigner.instance.getDatabase(account.npub).applicationDao().clearLogs()
                    }
                },
                text = stringResource(R.string.clear_logs),
            )
        }
        items(logs.value) { log ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        modifier = Modifier.padding(top = 16.dp),
                        text = TimeUtils.formatLongToCustomDateTimeWithSeconds(log.time),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = log.url,
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = log.type,
                        fontSize = 20.sp,
                    )
                    Text(
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                        text = log.message,
                        fontSize = 20.sp,
                    )

                    Spacer(Modifier.weight(1f))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

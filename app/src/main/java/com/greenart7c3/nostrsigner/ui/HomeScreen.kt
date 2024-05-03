package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.service.ApplicationNameCache
import com.greenart7c3.nostrsigner.ui.components.MultiEventHomeScreen
import com.greenart7c3.nostrsigner.ui.components.SingleEventHomeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    modifier: Modifier,
    intents: List<IntentData>,
    packageName: String?,
    applicationName: String?,
    account: Account,
    database: AppDatabase,
) {
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            loading = true
            try {
                database.applicationDao().getAllApplications().forEach {
                    ApplicationNameCache.names[it.application.key] = it.application.name
                }
            } finally {
                loading = false
            }
        }
    }

    if (loading) {
        CenterCircularProgressIndicator(modifier)
    } else {
        Box(
            modifier,
        ) {
            if (intents.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    Arrangement.Center,
                    Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.nothing_to_approve_yet),
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.why_not_explore_your_favorite_nostr_app_a_bit))
                }
            } else if (intents.size == 1) {
                SingleEventHomeScreen(
                    packageName,
                    applicationName,
                    intents.first(),
                    account,
                    database,
                ) {
                    loading = it
                }
            } else {
                MultiEventHomeScreen(
                    intents,
                    packageName,
                    account,
                    database,
                ) {
                    loading = it
                }
            }
        }
    }
}

data class Result(
    val `package`: String?,
    val signature: String?,
    val id: String?,
)

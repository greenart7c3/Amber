package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.ui.components.MultiEventHomeScreen
import com.greenart7c3.nostrsigner.ui.components.SingleEventHomeScreen

@Composable
fun HomeScreen(
    modifier: Modifier,
    intents: List<IntentData>,
    packageName: String?,
    applicationName: String?,
    account: Account
) {
    var loading by remember { mutableStateOf(false) }
    if (loading) {
        CenterCircularProgressIndicator(modifier)
    } else {
        Box(
            modifier
        ) {
            if (intents.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    Arrangement.Center,
                    Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.no_event_to_sign))
                }
            } else if (intents.size == 1) {
                SingleEventHomeScreen(
                    packageName,
                    applicationName,
                    intents.first(),
                    account
                )
            } else {
                MultiEventHomeScreen(
                    intents,
                    applicationName,
                    packageName,
                    account
                ) {
                    loading = it
                }
            }
        }
    }
}

class Result(
    val `package`: String?,
    val signature: String?,
    val id: String?
)

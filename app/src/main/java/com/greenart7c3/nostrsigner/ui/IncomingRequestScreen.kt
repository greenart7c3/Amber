package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.service.ApplicationNameCache
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.components.BunkerSingleEventHomeScreen
import com.greenart7c3.nostrsigner.ui.components.IntentSingleEventHomeScreen
import com.greenart7c3.nostrsigner.ui.components.MultiEventHomeScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun IncomingRequestScreen(
    modifier: Modifier,
    intents: List<IntentData>,
    bunkerRequests: List<AmberBunkerRequest>,
    packageName: String?,
    applicationName: String?,
    account: Account,
    navController: NavController,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            loading = true
            try {
                LocalPreferences.allSavedAccounts(context).forEach { account ->
                    Amber.instance.getDatabase(account.npub).applicationDao().getAllApplications().forEach {
                        if (it.application.name.isNotBlank()) {
                            ApplicationNameCache.names["${account.npub.toShortenHex()}-${it.application.key}"] = it.application.name
                        }
                    }
                }
            } finally {
                loading = false
            }
        }
    }

    if (loading) {
        CenterCircularProgressIndicator(modifier)
    } else {
        if (intents.isEmpty() && bunkerRequests.isEmpty()) {
            Column(
                modifier.fillMaxSize(),
                Arrangement.Center,
                Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.nothing_to_approve_yet),
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    stringResource(R.string.why_not_explore_your_favorite_nostr_app_a_bit),
                    textAlign = TextAlign.Center,
                )
            }
        } else if (intents.size == 1) {
            IntentSingleEventHomeScreen(
                modifier = modifier,
                packageName = packageName,
                applicationName = applicationName,
                intentData = intents.first(),
                account = account,
                onRemoveIntentData = onRemoveIntentData,
                onLoading = onLoading,
            )
        } else if (bunkerRequests.size == 1) {
            BunkerSingleEventHomeScreen(
                modifier = modifier,
                bunkerRequest = bunkerRequests.first(),
                account = account,
                onLoading = onLoading,
            )
        } else {
            MultiEventHomeScreen(
                modifier = modifier,
                intents = intents,
                bunkerRequests = bunkerRequests,
                packageName = packageName,
                accountParam = account,
                navController = navController,
                onRemoveIntentData = onRemoveIntentData,
                onLoading = onLoading,
            )
        }
    }
}

data class Result(
    val `package`: String?,
    val signature: String?,
    val result: String?,
    val id: String?,
)

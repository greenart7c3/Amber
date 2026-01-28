package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.ui.components.BunkerSingleEventHomeScreen
import com.greenart7c3.nostrsigner.ui.components.IntentSingleEventHomeScreen
import com.greenart7c3.nostrsigner.ui.components.MultiEventHomeScreen

@Composable
fun IncomingRequestScreen(
    modifier: Modifier,
    intents: List<IntentData>,
    bunkerRequests: List<AmberBunkerRequest>,
    packageName: String?,
    applicationName: String?,
    account: Account,
    accountStateViewModel: AccountStateViewModel,
    navController: NavController,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
) {
    var loading by remember { mutableStateOf(false) }

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
                accountStateViewModel = accountStateViewModel,
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

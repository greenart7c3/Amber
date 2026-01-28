package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType

@Composable
fun MultiEventHomeScreen(
    modifier: Modifier,
    intents: List<IntentData>,
    bunkerRequests: List<AmberBunkerRequest>,
    packageName: String?,
    accountParam: Account,
    navController: NavController,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
) {
    if (bunkerRequests.isNotEmpty()) {
        BunkerMultiEventHomeScreen(
            modifier = modifier,
            packageName = packageName,
            accountParam = accountParam,
            navController = navController,
            bunkerRequests = bunkerRequests,
            onLoading = onLoading,
        )
    } else {
        IntentMultiEventHomeScreen(
            modifier = modifier,
            intents = intents,
            packageName = packageName,
            accountParam = accountParam,
            navController = navController,
            onRemoveIntentData = onRemoveIntentData,
            onLoading = onLoading,
        )
    }
}

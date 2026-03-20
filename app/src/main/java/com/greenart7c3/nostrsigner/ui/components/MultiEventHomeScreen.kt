package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import kotlinx.collections.immutable.ImmutableList

@Composable
fun MultiEventHomeScreen(
    modifier: Modifier,
    intents: ImmutableList<IntentData>,
    bunkerRequests: ImmutableList<AmberBunkerRequest>,
    packageName: String?,
    accountParam: Account,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
) {
    if (bunkerRequests.isNotEmpty()) {
        BunkerMultiEventHomeScreen(
            modifier = modifier,
            packageName = packageName,
            accountParam = accountParam,
            bunkerRequests = bunkerRequests,
            onLoading = onLoading,
        )
    } else {
        IntentMultiEventHomeScreen(
            modifier = modifier,
            intents = intents,
            packageName = packageName,
            accountParam = accountParam,
            onRemoveIntentData = onRemoveIntentData,
            onLoading = onLoading,
        )
    }
}

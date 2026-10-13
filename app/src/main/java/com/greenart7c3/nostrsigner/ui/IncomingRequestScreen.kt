package com.greenart7c3.nostrsigner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.ui.components.BunkerSingleEventHomeScreen
import com.greenart7c3.nostrsigner.ui.components.IntentSingleEventHomeScreen
import com.greenart7c3.nostrsigner.ui.components.MultiEventHomeScreen
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import kotlinx.collections.immutable.ImmutableList

@Composable
fun IncomingRequestScreen(
    horizontalPadding: Dp,
    modifier: Modifier,
    scaffoldPadding: PaddingValues,
    intents: ImmutableList<IntentData>,
    bunkerRequests: ImmutableList<AmberBunkerRequest>,
    packageName: String?,
    applicationName: String?,
    account: Account,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
    isExternalRequest: Boolean = false,
) {
    var loading by remember { mutableStateOf(false) }
    // A connect needs the account choice and the replace-or-create-new prompt that
    // only the single screen has, so it is never batched with other requests.
    val connectRequest = bunkerRequests.firstOrNull { it.request is BunkerRequestConnect }

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
                if (isExternalRequest) {
                    Spacer(Modifier.size(16.dp))
                    OutlinedButton(
                        onClick = {
                            Amber.instance.getMainActivity()?.finishAndRemoveTask()
                        },
                    ) {
                        Text(stringResource(R.string.invalid_intent_close_app))
                    }
                }
            }
        } else if (intents.size == 1) {
            IntentSingleEventHomeScreen(
                horizontalPadding = horizontalPadding,
                modifier = modifier,
                packageName = packageName,
                applicationName = applicationName,
                intentData = intents.first(),
                account = account,
                onRemoveIntentData = onRemoveIntentData,
                onLoading = onLoading,
            )
        } else if (connectRequest != null || bunkerRequests.size == 1) {
            val bunkerRequest = connectRequest ?: bunkerRequests.first()
            key(bunkerRequest.request.id) {
                BunkerSingleEventHomeScreen(
                    horizontalPadding = horizontalPadding,
                    modifier = modifier,
                    scaffoldPadding = scaffoldPadding,
                    bunkerRequest = bunkerRequest,
                    account = account,
                    onLoading = onLoading,
                )
            }
        } else {
            MultiEventHomeScreen(
                modifier = modifier,
                intents = intents,
                bunkerRequests = bunkerRequests,
                packageName = packageName,
                accountParam = account,
                onRemoveIntentData = onRemoveIntentData,
                onLoading = onLoading,
            )
        }
    }
}

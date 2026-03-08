package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.RememberType

/**
 * Scope for a kind 22242 relay authentication permission.
 * [SPECIFIC] means the permission applies only to the specific relay URL in the event.
 * [ALL] means the permission applies to all relays (wildcard "*").
 */
enum class RelayAuthScope {
    SPECIFIC,
    ALL,
}

@Composable
fun BunkerRelayAuthScreen(
    modifier: Modifier,
    appName: String,
    relayUrl: String,
    shouldAcceptOrReject: Boolean?,
    defaultScope: RelayAuthScope,
    account: Account,
    onAccept: (RememberType, RelayAuthScope) -> Unit,
    onReject: (RememberType, RelayAuthScope) -> Unit,
) {
    var rememberType by remember { mutableStateOf(RememberType.NEVER) }
    var scope by remember { mutableStateOf(defaultScope) }
    val scopeIndex by remember(scope) { mutableIntStateOf(if (scope == RelayAuthScope.SPECIFIC) 0 else 1) }

    if (shouldAcceptOrReject != null) {
        LaunchedEffect(Unit) {
            if (shouldAcceptOrReject) {
                onAccept(RememberType.entries[0], scope)
            } else {
                onReject(RememberType.entries[0], scope)
            }
        }
    }

    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.size(16.dp))

        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(appName)
                }
                append(" ")
                append(stringResource(R.string.relay_auth_request, ""))
            },
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        Spacer(Modifier.size(8.dp))

        Text(
            relayUrl,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        Spacer(Modifier.size(16.dp))

        SigningAs(account)

        Spacer(modifier = Modifier.weight(1f))

        LabeledBorderBox(
            label = stringResource(R.string.relay_auth_scope),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            AmberToggles(
                selectedIndex = scopeIndex,
                count = 2,
                segmentWidth = 120.dp,
                content = {
                    ToggleOption(
                        modifier = Modifier.width(120.dp),
                        text = stringResource(R.string.for_this_relay_only),
                        isSelected = scope == RelayAuthScope.SPECIFIC,
                        onClick = { scope = RelayAuthScope.SPECIFIC },
                    )
                    ToggleOption(
                        modifier = Modifier.width(120.dp),
                        text = stringResource(R.string.for_all_relays),
                        isSelected = scope == RelayAuthScope.ALL,
                        onClick = { scope = RelayAuthScope.ALL },
                    )
                },
            )
        }

        Spacer(Modifier.size(8.dp))

        RememberMyChoice(
            shouldRunAcceptOrReject = null,
            packageName = null,
            alwaysShow = true,
            onAccept = { onAccept(it, scope) },
            onReject = { onReject(it, scope) },
        ) {
            rememberType = it
        }

        AcceptRejectButtons(
            onAccept = { onAccept(rememberType, scope) },
            onReject = { onReject(rememberType, scope) },
        )
    }
}

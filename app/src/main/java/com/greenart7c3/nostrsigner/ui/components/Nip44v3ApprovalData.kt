package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.ui.RememberType

/**
 * Dedicated approval screen for NIP-44 v3 encrypt/decrypt requests.
 *
 * Unlike the v2/v4 [EncryptDecryptData] screen, v3 authenticates an event
 * `kind` and a `scope`, so this screen surfaces that context explicitly. The
 * scope toggle grants either a single kind or all kinds.
 */
@Composable
fun Nip44v3ApprovalData(
    modifier: Modifier,
    isBunker: Boolean,
    appName: String,
    packageName: String?,
    type: SignerType,
    account: Account,
    kind: Int?,
    scope: String,
    encryptedData: EncryptedDataKind?,
    shouldRunOnAccept: Boolean?,
    defaultScope: DecryptTypeScope = DecryptTypeScope.SPECIFIC,
    onAccept: (RememberType, DecryptTypeScope) -> Unit,
    onReject: (RememberType, DecryptTypeScope) -> Unit,
) {
    var rememberType by remember { mutableStateOf(RememberType.NEVER) }
    var grantScope by remember { mutableStateOf(defaultScope) }

    val isEncrypt = type == SignerType.NIP44_V3_ENCRYPT
    val messageRes = if (isEncrypt) R.string.nip44_v3_wants_to_encrypt else R.string.nip44_v3_wants_to_decrypt
    val displayContent = nip44v3DisplayContent(encryptedData, isEncrypt)

    Column(modifier) {
        if (isBunker) {
            Text(
                buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(appName)
                    }
                    append(" ")
                    append(stringResource(messageRes))
                },
                fontSize = 18.sp,
            )
        } else {
            LocalAppIcon(packageName)
            Text(
                stringResource(messageRes),
                fontSize = 18.sp,
            )
        }

        Spacer(Modifier.size(8.dp))
        Nip44v3ContextBox(kind = kind, scope = scope)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors().copy(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    displayContent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }

        Spacer(Modifier.size(16.dp))
        SigningAs(account)
        Spacer(modifier = Modifier.weight(1f))

        Spacer(Modifier.size(8.dp))
        LabeledBorderBox(
            label = stringResource(R.string.encryption_scope),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            AmberToggles(
                selected = grantScope,
                options = listOf(DecryptTypeScope.SPECIFIC, DecryptTypeScope.ALL),
                onSelected = { grantScope = it },
                label = {
                    stringResource(
                        when (it) {
                            DecryptTypeScope.SPECIFIC -> R.string.for_this_kind_only
                            DecryptTypeScope.ALL -> R.string.for_all_kinds
                        },
                    )
                },
            )
        }
        Spacer(Modifier.size(8.dp))

        RememberMyChoice(
            shouldRunOnAccept,
            if (isBunker) null else packageName,
            isBunker,
            onAccept = { onAccept(it, grantScope) },
            onReject = { onReject(it, grantScope) },
        ) {
            rememberType = it
        }

        AcceptRejectButtons(
            onAccept = { onAccept(rememberType, grantScope) },
            onReject = { onReject(rememberType, grantScope) },
        )
    }
}

/**
 * Shows the NIP-44 v3 context (event kind + scope) being authenticated by the
 * request, so the user can see what they are granting access to.
 */
@Composable
fun Nip44v3ContextBox(
    kind: Int?,
    scope: String,
) {
    LabeledBorderBox(
        label = stringResource(R.string.nip44_v3_context),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            val kindLabel = if (kind != null) {
                val translation = Permission("sign_event", kind).toLocalizedString(Amber.instance)
                val unknown = stringResource(R.string.event_kind, kind.toString())
                // toLocalizedString returns the "Event kind N" fallback when the
                // kind has no translation; only append the name when one exists.
                if (translation != unknown) "$kind ($translation)" else kind.toString()
            } else {
                "?"
            }
            Text(stringResource(R.string.nip44_v3_kind, kindLabel))
            Text(
                stringResource(
                    R.string.nip44_v3_scope,
                    scope.ifEmpty { stringResource(R.string.nip44_v3_no_scope) },
                ),
            )
        }
    }
}

/**
 * The plaintext to show: encrypt stores it as `text`, decrypt as `result`
 * (populated when the request was parsed) — same shape as the v2 screen.
 */
private fun nip44v3DisplayContent(encryptedData: EncryptedDataKind?, isEncrypt: Boolean): String = if (isEncrypt) {
    (encryptedData as? ClearTextEncryptedDataKind)?.text ?: encryptedData?.result ?: ""
} else {
    encryptedData?.result ?: ""
}

package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.encryption_scope
import com.greenart7c3.nostrsigner.commonsui.event_kind
import com.greenart7c3.nostrsigner.commonsui.for_all_kinds
import com.greenart7c3.nostrsigner.commonsui.for_this_kind_only
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.commonsui.nip44_v3_context
import com.greenart7c3.nostrsigner.commonsui.nip44_v3_kind
import com.greenart7c3.nostrsigner.commonsui.nip44_v3_no_scope
import com.greenart7c3.nostrsigner.commonsui.nip44_v3_scope
import com.greenart7c3.nostrsigner.commonsui.nip44_v3_wants_to_decrypt
import com.greenart7c3.nostrsigner.commonsui.nip44_v3_wants_to_encrypt
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.models.nip44v3Plaintext
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.PreviewsLightDark
import com.greenart7c3.nostrsigner.ui.theme.previewAccount
import org.jetbrains.compose.resources.stringResource

/**
 * What a NIP-44 v3 request asks: encrypt or decrypt ([type]), under the event [kind]
 * and [scope] it authenticates, with the content prepared when it arrived.
 */
@Immutable
class Nip44v3Prompt(
    val type: SignerType,
    val kind: Int?,
    val scope: String,
    val encryptedData: EncryptedDataKind?,
)

/**
 * Dedicated approval screen for NIP-44 v3 encrypt/decrypt requests.
 *
 * Unlike the v2/v4 [EncryptDecryptData] screen, v3 authenticates an event
 * `kind` and a `scope`, so this screen surfaces that context explicitly. The
 * scope toggle grants either a single kind or all kinds.
 */
@Composable
fun Nip44v3ApprovalData(
    isBunker: Boolean,
    appName: String,
    packageName: String?,
    prompt: Nip44v3Prompt,
    account: Account,
    shouldRunOnAccept: Boolean?,
    onAccept: (RememberType, DecryptTypeScope) -> Unit,
    onReject: (RememberType, DecryptTypeScope) -> Unit,
    modifier: Modifier = Modifier,
    defaultScope: DecryptTypeScope = DecryptTypeScope.SPECIFIC,
    iconUrl: String = "",
) {
    val type = prompt.type
    val kind = prompt.kind
    val scope = prompt.scope
    val encryptedData = prompt.encryptedData
    var rememberType by remember { mutableStateOf(RememberType.NEVER) }
    var grantScope by remember { mutableStateOf(defaultScope) }

    val isEncrypt = type == SignerType.NIP44_V3_ENCRYPT
    val messageRes = if (isEncrypt) Res.string.nip44_v3_wants_to_encrypt else Res.string.nip44_v3_wants_to_decrypt
    val displayContent = encryptedData.nip44v3Plaintext()

    Column(modifier) {
        Nip44v3RequestHeader(
            isBunker = isBunker,
            appName = appName,
            packageName = packageName,
            message = stringResource(messageRes),
            iconUrl = iconUrl,
        )

        Spacer(Modifier.size(8.dp))
        Nip44v3ContextBox(kind = kind, scope = scope)

        Nip44v3ContentCard(
            displayContent = displayContent,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )

        Spacer(Modifier.size(16.dp))
        SigningAs(account)
        Spacer(modifier = Modifier.weight(1f))

        Spacer(Modifier.size(8.dp))
        Nip44v3GrantScopeBox(
            grantScope = grantScope,
            onSelect = { grantScope = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        Spacer(Modifier.size(8.dp))

        RememberMyChoice(
            shouldRunOnAccept,
            if (isBunker) null else packageName,
            onAccept = { onAccept(it, grantScope) },
            onReject = { onReject(it, grantScope) },
            onChange = { rememberType = it },
            alwaysShow = isBunker,
        )

        AcceptRejectButtons(
            onAccept = { onAccept(rememberType, grantScope) },
            onReject = { onReject(rememberType, grantScope) },
        )
    }
}

@Composable
private fun ColumnScope.Nip44v3RequestHeader(
    isBunker: Boolean,
    appName: String,
    packageName: String?,
    message: String,
    iconUrl: String,
) {
    if (isBunker) {
        RemoteAppIcon(iconUrl, appName)
        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(appName)
                }
                append(" ")
                append(message)
            },
            fontSize = 18.sp,
        )
    } else {
        LocalAppIcon(packageName)
        Text(
            message,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun Nip44v3ContentCard(
    displayContent: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
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
}

@Composable
private fun Nip44v3GrantScopeBox(
    grantScope: DecryptTypeScope,
    onSelect: (DecryptTypeScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    LabeledBorderBox(
        label = stringResource(Res.string.encryption_scope),
        modifier = modifier,
    ) {
        AmberToggles(
            selected = grantScope,
            options = listOf(DecryptTypeScope.SPECIFIC, DecryptTypeScope.ALL),
            onSelect = onSelect,
            label = {
                stringResource(
                    when (it) {
                        DecryptTypeScope.SPECIFIC -> Res.string.for_this_kind_only
                        DecryptTypeScope.ALL -> Res.string.for_all_kinds
                    },
                )
            },
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
    modifier: Modifier = Modifier,
) {
    LabeledBorderBox(
        label = stringResource(Res.string.nip44_v3_context),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            val kindLabel = if (kind != null) {
                val translation = Permission("sign_event", kind).localizedBlocking()
                val unknown = stringResource(Res.string.event_kind, kind.toString())
                // toLocalizedString returns the "Event kind N" fallback when the
                // kind has no translation; only append the name when one exists.
                if (translation != unknown) "$kind ($translation)" else kind.toString()
            } else {
                "?"
            }
            Text(stringResource(Res.string.nip44_v3_kind, kindLabel))
            Text(
                stringResource(
                    Res.string.nip44_v3_scope,
                    scope.ifEmpty { stringResource(Res.string.nip44_v3_no_scope) },
                ),
            )
        }
    }
}

@PreviewsLightDark
@Composable
private fun Nip44v3ApprovalDataPreview() {
    AmberPreview {
        Nip44v3ApprovalData(
            modifier = Modifier
                .fillMaxWidth()
                .height(700.dp)
                .padding(16.dp),
            isBunker = false,
            appName = "Amethyst",
            packageName = null,
            prompt = Nip44v3Prompt(
                type = SignerType.NIP44_V3_ENCRYPT,
                kind = null,
                scope = "chat",
                encryptedData = ClearTextEncryptedDataKind("Hello Nostr!", "Hello Nostr!"),
            ),
            account = previewAccount(),
            shouldRunOnAccept = null,
            onAccept = { _, _ -> },
            onReject = { _, _ -> },
        )
    }
}

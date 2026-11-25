package com.greenart7c3.nostrsigner.ui.components

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.approve
import com.greenart7c3.nostrsigner.commonsui.confirm
import com.greenart7c3.nostrsigner.commonsui.connect
import com.greenart7c3.nostrsigner.commonsui.decrypt_zap_event
import com.greenart7c3.nostrsigner.commonsui.encrypt_this_list_of_tags_with
import com.greenart7c3.nostrsigner.commonsui.encrypt_this_text_with
import com.greenart7c3.nostrsigner.commonsui.encrypt_with
import com.greenart7c3.nostrsigner.commonsui.is_requiring_some_permissions_please_review_them2
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.commonsui.read_from_encrypted_content
import com.greenart7c3.nostrsigner.commonsui.read_this_list_of_tags_from_encrypted_content
import com.greenart7c3.nostrsigner.commonsui.read_this_text_from_encrypted_content
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.models.PrivateZapEncryptedDataKind
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.service.MultiEventScreenIntents
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.ui.LocalAccountRepository
import com.greenart7c3.nostrsigner.ui.LocalAppScope
import com.greenart7c3.nostrsigner.ui.LocalSettingsStore
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.PreviewsLightDark
import com.greenart7c3.nostrsigner.ui.theme.previewAccount
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource

@Composable
fun IntentMultiEventHomeScreen(
    intents: ImmutableList<IntentData>,
    packageName: String?,
    accountParam: Account,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IntentMultiEventViewModel = viewModel(
        key = "intent-multi-${accountParam.npub}-$packageName",
        factory = IntentMultiEventViewModel.factory(
            appContext = LocalContext.current.applicationContext,
            settingsStore = LocalSettingsStore.current,
            accountRepository = LocalAccountRepository.current,
            appScope = LocalAppScope.current,
            packageName = packageName,
            account = accountParam,
        ),
    ),
) {
    val activity = LocalActivity.current
    LaunchedEffect(Unit) { viewModel.initChoices(intents) }

    Column(modifier) {
        LocalAppIcon(packageName)

        Text(
            stringResource(Res.string.is_requiring_some_permissions_please_review_them2),
            Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        )

        // Confirm signs each request under its own account.
        val batchAccounts = rememberBatchAccounts(intents.map { it.currentAccount }, accountParam)
        batchAccounts.SigningAsHeader()

        IntentRequestGroups(
            intents = intents,
            batchAccounts = batchAccounts,
            choices = viewModel.choices,
            onGroupApprove = viewModel::setGroupApproved,
            modifier = Modifier.weight(1f),
        )

        AmberButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            text = stringResource(Res.string.confirm),
            onClick = { viewModel.confirm(intents, activity, onRemoveIntentData, onLoading) },
        )
    }
}

/** The requests grouped by kind, each group with its approve toggle and remember options. */
@Composable
private fun IntentRequestGroups(
    intents: ImmutableList<IntentData>,
    batchAccounts: BatchAccounts,
    choices: RequestGroupChoices,
    onGroupApprove: (List<IntentData>, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups = remember(intents) {
        groupRequests(intents) {
            requestGroupKey(it.type, it.event?.kind, it.encryptedData, it.nip44v3Kind)
        }
    }
    val expandedGroups = remember { mutableStateMapOf<RequestGroupKey, Boolean>() }
    LazyColumn(modifier) {
        groups.forEach { (groupKey, groupIntents) ->
            val expanded = groups.size == 1 || (expandedGroups[groupKey] ?: false)
            if (groups.size > 1) {
                item(key = "group-header:${groupKey.type.name}:${groupKey.payload?.name ?: ""}:${groupKey.kind ?: ""}") {
                    RequestGroupHeader(
                        label = groupKey.toLabel(),
                        count = groupIntents.size,
                        approved = groupIntents.all { MultiEventScreenIntents.checkedStates[it.queueKey] ?: true },
                        expanded = expanded,
                        onApproveChange = { approve -> onGroupApprove(groupIntents, approve) },
                        onExpandToggle = { expandedGroups[groupKey] = !(expandedGroups[groupKey] ?: false) },
                    )
                }
            }
            if (groupKey.hasGroupOptions()) {
                item(key = "group-options:${groupKey.type.name}:${groupKey.payload?.name ?: ""}:${groupKey.kind ?: ""}") {
                    RequestGroupOptions(
                        groupKey = groupKey,
                        rememberType = choices.rememberType(groupKey),
                        onRememberTypeChange = { choices.setRememberType(groupKey, it) },
                        relayAuthScope = choices.relayAuthScope(groupKey),
                        onRelayAuthScopeChange = { choices.setRelayAuthScope(groupKey, it) },
                        decryptTypeScope = choices.decryptScope(groupKey),
                        onDecryptTypeScopeChange = { choices.setDecryptScope(groupKey, it) },
                    )
                }
            }
            if (expanded) {
                items(groupIntents, key = { it.id }) { intent ->
                    IntentRequestCard(
                        intent = intent,
                        signingAs = batchAccounts.forCard(intent.currentAccount),
                        approved = MultiEventScreenIntents.checkedStates[intent.queueKey] ?: true,
                        onApproveChange = { MultiEventScreenIntents.checkedStates[intent.queueKey] = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun IntentRequestCard(
    intent: IntentData,
    signingAs: Account?,
    approved: Boolean,
    onApproveChange: (Boolean) -> Unit,
) {
    BatchRequestCard(
        type = intent.type,
        label = intentRequestLabel(intent),
        preview = intentRequestPreview(intent),
        event = intent.event,
        encryptedData = intent.encryptedData,
        signingAs = signingAs,
        approved = approved,
        onApproveChange = onApproveChange,
    )
}

/** What the request asks for. */
@Composable
private fun intentRequestLabel(intent: IntentData): String {
    val type = intent.type
    val nip = type.toString().split("_").first()
    return when {
        type == SignerType.CONNECT -> stringResource(Res.string.connect)
        type.toString().contains("ENCRYPT") -> encryptLabel(intent.encryptedData, nip)
        type.toString().contains("DECRYPT") -> decryptLabel(intent.encryptedData, nip)
        type == SignerType.SIGN_EVENT -> intent.event!!.let { eventKindLabel(it.kind, it.tags) }
        else -> Permission(type.toString().lowercase(), null).localizedBlocking()
    }
}

@Composable
private fun encryptLabel(
    encryptedData: EncryptedDataKind?,
    nip: String,
): String = when (encryptedData) {
    is EventEncryptedDataKind -> {
        val p = Permission("sign_event", encryptedData.event.kind)
        stringResource(Res.string.encrypt_with, p.localizedBlocking(), nip)
    }
    is TagArrayEncryptedDataKind -> stringResource(Res.string.encrypt_this_list_of_tags_with, nip)
    else -> stringResource(Res.string.encrypt_this_text_with, nip)
}

@Composable
private fun decryptLabel(
    encryptedData: EncryptedDataKind?,
    nip: String,
): String = when (encryptedData) {
    is EventEncryptedDataKind -> {
        val p = Permission("sign_event", encryptedData.event.kind)
        stringResource(Res.string.read_from_encrypted_content, p.localizedBlocking(), nip)
    }
    is TagArrayEncryptedDataKind -> stringResource(Res.string.read_this_list_of_tags_from_encrypted_content, nip)
    is PrivateZapEncryptedDataKind -> stringResource(Res.string.decrypt_zap_event).capitalize(Locale.current)
    else -> stringResource(Res.string.read_this_text_from_encrypted_content, nip)
}

/** The request's content in short: the event's content (the relay, for a relay AUTH), or the data to encrypt or decrypt. */
private fun intentRequestPreview(intent: IntentData): String {
    if (intent.type == SignerType.SIGN_EVENT) {
        val event = intent.event!!
        return if (event.kind == 22242) AmberEvent.relay(event) ?: event.content else event.content
    }
    val encryptedData = intent.encryptedData
    return if (intent.type.name.contains("ENCRYPT") && encryptedData is ClearTextEncryptedDataKind) {
        encryptedData.text
    } else if (encryptedData is EventEncryptedDataKind) {
        if (encryptedData.sealEncryptedDataKind != null) {
            if (encryptedData.sealEncryptedDataKind is EventEncryptedDataKind) {
                encryptedData.sealEncryptedDataKind.event.content
            } else {
                encryptedData.sealEncryptedDataKind.result
            }
        } else {
            encryptedData.event.content
        }
    } else if (encryptedData is TagArrayEncryptedDataKind) {
        encryptedData.tagArray.joinToString(separator = ", ") {
            "[${it.joinToString(separator = ", ") { tag -> "\"$tag\"" }}]"
        }
    } else {
        encryptedData?.result ?: ""
    }
}

private const val PREVIEW_PUBKEY = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

private fun previewIntent(
    id: String,
    type: SignerType,
    data: String = "",
    event: Event? = null,
    encryptedData: ClearTextEncryptedDataKind? = null,
) = IntentData(
    data = data,
    name = "",
    type = type,
    pubKey = PREVIEW_PUBKEY,
    id = id,
    callBackUrl = null,
    compression = CompressionType.NONE,
    returnType = ReturnType.SIGNATURE,
    permissions = null,
    currentAccount = "",
    route = null,
    event = event,
    encryptedData = encryptedData,
)

private fun previewSignIntent(id: String, content: String) = previewIntent(
    id = id,
    type = SignerType.SIGN_EVENT,
    event = Event(
        id = "0".repeat(64),
        pubKey = PREVIEW_PUBKEY,
        createdAt = 1735689600,
        kind = 1,
        tags = arrayOf(arrayOf("t", "amber")),
        content = content,
        sig = "",
    ),
)

@PreviewsLightDark
@Composable
private fun IntentMultiEventHomeScreenPreview() {
    AmberPreview {
        IntentRequestGroups(
            modifier = Modifier
                .fillMaxWidth()
                .height(700.dp)
                .padding(16.dp),
            intents = persistentListOf(
                previewSignIntent("preview-sign-1", "Hello Nostr!"),
                previewSignIntent("preview-sign-2", "GM from Amber"),
                previewIntent(
                    id = "preview-nip44-decrypt",
                    type = SignerType.NIP44_DECRYPT,
                    data = "encrypted-payload",
                    encryptedData = ClearTextEncryptedDataKind("encrypted-payload", "Hello Nostr!"),
                ),
            ),
            batchAccounts = rememberBatchAccounts(listOf(""), previewAccount()),
            choices = RequestGroupChoices(),
            onGroupApprove = { _, _ -> },
        )
    }
}

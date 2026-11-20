package com.greenart7c3.nostrsigner.ui.components

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greenart7c3.nostrsigner.common.EncryptionType
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.nip46SignerType
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.approve
import com.greenart7c3.nostrsigner.commonsui.confirm
import com.greenart7c3.nostrsigner.commonsui.connect
import com.greenart7c3.nostrsigner.commonsui.decrypt_zap_event
import com.greenart7c3.nostrsigner.commonsui.deny
import com.greenart7c3.nostrsigner.commonsui.encrypt_this_list_of_tags_with
import com.greenart7c3.nostrsigner.commonsui.encrypt_this_text_with
import com.greenart7c3.nostrsigner.commonsui.encrypt_with
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.is_requiring_some_permissions_please_review_them
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.commonsui.read_from_encrypted_content
import com.greenart7c3.nostrsigner.commonsui.read_this_list_of_tags_from_encrypted_content
import com.greenart7c3.nostrsigner.commonsui.read_this_text_from_encrypted_content
import com.greenart7c3.nostrsigner.commonsui.relay_not_in_auth_whitelist
import com.greenart7c3.nostrsigner.commonsui.several_apps_are_requiring_some_permissions_please_review_them
import com.greenart7c3.nostrsigner.commonsui.show_details
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.BunkerClientMetadata
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.PrivateZapEncryptedDataKind
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.service.BunkerRequestUtils
import com.greenart7c3.nostrsigner.service.MultiEventScreenIntents
import com.greenart7c3.nostrsigner.service.isBlockedByAuthWhitelist
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.LocalAccountRepository
import com.greenart7c3.nostrsigner.ui.LocalAppScope
import com.greenart7c3.nostrsigner.ui.LocalBunkerResponder
import com.greenart7c3.nostrsigner.ui.LocalRelayClient
import com.greenart7c3.nostrsigner.ui.LocalSettingsStore
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.ThemePreviews
import com.greenart7c3.nostrsigner.ui.theme.previewAccount
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private data class RequestAppInfo(val name: String, val icon: String)

private fun AmberBunkerRequest.defaultAppInfo() = RequestAppInfo(
    name = clientMetadata?.name?.ifBlank { null } ?: name.ifBlank { localKey.toShortenHex() },
    icon = clientMetadata?.image ?: "",
)

/** The requests a batch may show: everything but connects. */
internal fun batchableBunkerRequests(requests: List<AmberBunkerRequest>): List<AmberBunkerRequest> = requests.filterNot { it.request is BunkerRequestConnect }

@Composable
fun BunkerMultiEventHomeScreen(
    bunkerRequests: ImmutableList<AmberBunkerRequest>,
    accountParam: Account,
    onLoading: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BunkerMultiEventViewModel = viewModel(
        key = "bunker-multi-${accountParam.npub}",
        factory = BunkerMultiEventViewModel.factory(
            appContext = LocalContext.current.applicationContext,
            settingsStore = LocalSettingsStore.current,
            accountRepository = LocalAccountRepository.current,
            relayClient = LocalRelayClient.current,
            bunkerResponder = LocalBunkerResponder.current,
            appScope = LocalAppScope.current,
            account = accountParam,
        ),
    ),
) {
    val activity = LocalActivity.current
    val batchRequests = remember(bunkerRequests) { batchableBunkerRequests(bunkerRequests) }
    LaunchedEffect(Unit) { viewModel.initChoices(batchRequests) }

    BunkerMultiEventContent(
        batchRequests = batchRequests,
        accountParam = accountParam,
        choices = viewModel.choices,
        onGroupApprove = viewModel::setGroupApproved,
        onConfirm = { viewModel.confirm(batchRequests, activity, onLoading) },
        loadSavedApp = viewModel::savedApp,
        modifier = modifier,
    )
}

@Composable
private fun BunkerMultiEventContent(
    batchRequests: List<AmberBunkerRequest>,
    accountParam: Account,
    choices: BunkerGroupChoices,
    onGroupApprove: (List<AmberBunkerRequest>, Boolean) -> Unit,
    onConfirm: () -> Unit,
    loadSavedApp: suspend (RequestAppKey) -> SavedAppInfo?,
    modifier: Modifier = Modifier,
) {
    // Requests are grouped by the app that sent them (in arrival order), then by request type.
    val apps = remember(batchRequests) {
        batchRequests.groupBy { it.appKey }.map { (appKey, requests) ->
            appKey to groupRequests(requests) { it.requestGroupKey() }
        }
    }
    val appInfos = rememberAppInfos(batchRequests, loadSavedApp)

    Column(
        modifier,
    ) {
        if (apps.size == 1) {
            val info = appInfoOf(appInfos, batchRequests, apps.first().first)
            RemoteAppIcon(info.icon, info.name)

            Text(
                stringResource(Res.string.is_requiring_some_permissions_please_review_them, info.name),
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
            )
        } else {
            Text(
                pluralStringResource(Res.plurals.several_apps_are_requiring_some_permissions_please_review_them, apps.size, apps.size),
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
        }

        // Confirm signs each request under its own account (see below).
        val batchAccounts = rememberBatchAccounts(batchRequests.map { it.currentAccount }, accountParam)
        batchAccounts.SigningAsHeader()

        BunkerRequestGroups(
            apps = apps,
            batchRequests = batchRequests,
            appInfos = appInfos,
            batchAccounts = batchAccounts,
            choices = choices,
            onGroupApprove = onGroupApprove,
            modifier = Modifier.weight(1f),
        )

        AmberButton(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            text = stringResource(Res.string.confirm),
            onClick = onConfirm,
        )
    }
}

/** Resolves each requesting app's name and icon: the saved app row, or the client metadata the request carried. */
@Composable
private fun rememberAppInfos(
    batchRequests: List<AmberBunkerRequest>,
    loadSavedApp: suspend (RequestAppKey) -> SavedAppInfo?,
): SnapshotStateMap<RequestAppKey, RequestAppInfo> {
    val appInfos = remember { mutableStateMapOf<RequestAppKey, RequestAppInfo>() }
    val load by rememberUpdatedState(loadSavedApp)

    // Skipped in previews: encrypted storage and the Amber singleton don't
    // exist in the preview renderer.
    if (!LocalInspectionMode.current) {
        LaunchedEffect(batchRequests) {
            batchRequests.map { it.appKey }.distinct().forEach { appKey ->
                val saved = load(appKey) ?: return@forEach
                val default = appInfoOf(appInfos, batchRequests, appKey)
                appInfos[appKey] = RequestAppInfo(
                    name = saved.name ?: default.name,
                    icon = saved.icon ?: default.icon,
                )
            }
        }
    }
    return appInfos
}

private fun appInfoOf(
    appInfos: SnapshotStateMap<RequestAppKey, RequestAppInfo>,
    batchRequests: List<AmberBunkerRequest>,
    appKey: RequestAppKey,
): RequestAppInfo = appInfos[appKey]
    ?: batchRequests.first { it.appKey == appKey }.defaultAppInfo()

@Composable
private fun BunkerRequestGroups(
    apps: List<Pair<RequestAppKey, List<Pair<RequestGroupKey, List<AmberBunkerRequest>>>>>,
    batchRequests: List<AmberBunkerRequest>,
    appInfos: SnapshotStateMap<RequestAppKey, RequestAppInfo>,
    batchAccounts: BatchAccounts,
    choices: BunkerGroupChoices,
    onGroupApprove: (List<AmberBunkerRequest>, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expandedGroups = remember { mutableStateMapOf<AppRequestGroupKey, Boolean>() }
    LazyColumn(
        modifier,
    ) {
        apps.forEach { (appKey, groups) ->
            if (apps.size > 1) {
                item(key = "app-header:${appKey.itemKey()}") {
                    val info = appInfoOf(appInfos, batchRequests, appKey)
                    RequestAppHeader(
                        name = info.name,
                        icon = info.icon,
                        count = groups.sumOf { it.second.size },
                    )
                }
            }
            bunkerGroupItems(appKey, groups, batchAccounts, choices, onGroupApprove, expandedGroups)
        }
    }
}

private fun LazyListScope.bunkerGroupItems(
    appKey: RequestAppKey,
    groups: List<Pair<RequestGroupKey, List<AmberBunkerRequest>>>,
    batchAccounts: BatchAccounts,
    choices: BunkerGroupChoices,
    onGroupApprove: (List<AmberBunkerRequest>, Boolean) -> Unit,
    expandedGroups: SnapshotStateMap<AppRequestGroupKey, Boolean>,
) {
    groups.forEach { (requestGroupKey, groupItems) ->
        val groupKey = AppRequestGroupKey(appKey, requestGroupKey)
        val expanded = groups.size == 1 || (expandedGroups[groupKey] ?: false)
        if (groups.size > 1) {
            item(key = "group-header:${groupKey.itemKey()}") {
                val groupApproved = groupItems.all { MultiEventScreenIntents.checkedStates[it.queueKey] ?: true }
                RequestGroupHeader(
                    label = requestGroupKey.toLabel(),
                    count = groupItems.size,
                    approved = groupApproved,
                    expanded = expanded,
                    onApproveChanged = { approve -> onGroupApprove(groupItems, approve) },
                    onExpandToggle = {
                        expandedGroups[groupKey] = !(expandedGroups[groupKey] ?: false)
                    },
                )
            }
        }
        if (requestGroupKey.hasGroupOptions()) {
            item(key = "group-options:${groupKey.itemKey()}") {
                RequestGroupOptions(
                    groupKey = requestGroupKey,
                    rememberType = choices.rememberType(groupKey),
                    onRememberTypeChanged = { choices.setRememberType(groupKey, it) },
                    relayAuthScope = choices.relayAuthScope(groupKey),
                    onRelayAuthScopeChanged = { choices.setRelayAuthScope(groupKey, it) },
                    decryptTypeScope = choices.decryptScope(groupKey),
                    onDecryptTypeScopeChanged = { choices.setDecryptScope(groupKey, it) },
                )
            }
        }
        if (expanded) {
            items(groupItems, key = { it.queueKey }) { bunkerRequest ->
                BunkerRequestCard(
                    bunkerRequest = bunkerRequest,
                    signingAs = batchAccounts.forCard(bunkerRequest.currentAccount),
                    approved = MultiEventScreenIntents.checkedStates[bunkerRequest.queueKey] ?: true,
                    onApproveChanged = {
                        MultiEventScreenIntents.checkedStates[bunkerRequest.queueKey] = it
                    },
                )
            }
        }
    }
}

@Composable
private fun RequestAppHeader(
    name: String,
    icon: String,
    count: Int,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp),
    ) {
        AppAvatar(
            iconUrl = icon,
            name = name,
            size = 32.dp,
        )
        Text(
            text = "$name ($count)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
    }
}

@Composable
private fun BunkerRequestCard(
    bunkerRequest: AmberBunkerRequest,
    signingAs: Account?,
    approved: Boolean,
    onApproveChanged: (Boolean) -> Unit,
) {
    val settingsStore = LocalSettingsStore.current
    val type = nip46SignerType(bunkerRequest.request.method)
    var showDetails by remember { mutableStateOf(false) }
    val hasDetails = (type == SignerType.SIGN_EVENT && bunkerRequest.signedEvent != null) ||
        ((type.toString().contains("ENCRYPT") || type.toString().contains("DECRYPT")) && bunkerRequest.encryptedData != null)

    Card(
        Modifier.padding(4.dp),
        colors = CardDefaults.cardColors().copy(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        border = BorderStroke(1.dp, Color.Gray),
    ) {
        Column(
            Modifier.padding(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                ) {
                    Text(text = bunkerRequestLabel(bunkerRequest, type))
                    val preview = bunkerRequestPreview(bunkerRequest, type)
                    if (preview.isNotBlank()) {
                        Text(
                            text = preview,
                            color = Color.Gray,
                            maxLines = 2,
                        )
                    }
                    if (hasDetails) {
                        RawJsonButton(
                            onCLick = { showDetails = true },
                            text = stringResource(Res.string.show_details),
                        )
                    }
                }
            }

            signingAs?.let { SigningAs(it) }

            if (isBlockedByAuthWhitelist(bunkerRequest.signedEvent, settingsStore.settings.authWhitelist)) {
                // Always rejected on confirm, as on the single screen.
                Text(
                    text = stringResource(Res.string.relay_not_in_auth_whitelist),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                AmberToggles(
                    selected = approved,
                    options = listOf(true, false),
                    onSelected = onApproveChanged,
                    label = {
                        if (it) stringResource(Res.string.approve) else stringResource(Res.string.deny)
                    },
                    indicatorColor = {
                        if (it) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    },
                    selectedTextColor = {
                        if (it) Color.Black else MaterialTheme.colorScheme.onError
                    },
                )
            }
        }
    }

    if (showDetails) {
        if (type == SignerType.SIGN_EVENT) {
            EventDetailModal(
                event = bunkerRequest.signedEvent!!,
                onDismiss = { showDetails = false },
            )
        } else {
            EncryptDecryptDetailModal(
                type = type,
                encryptedData = bunkerRequest.encryptedData,
                onDismiss = { showDetails = false },
            )
        }
    }
}

private fun bunkerRequestLabel(bunkerRequest: AmberBunkerRequest, type: SignerType): String {
    if (type == SignerType.CONNECT) {
        return getStringBlocking(Res.string.connect)
    }
    val encryptedData = bunkerRequest.encryptedData
    return if (type.toString().contains("ENCRYPT")) {
        when (encryptedData) {
            is EventEncryptedDataKind -> {
                val p = Permission("sign_event", encryptedData.event.kind)
                getStringBlocking(Res.string.encrypt_with, p.localizedBlocking(), type.toString().split("_").first())
            }
            is TagArrayEncryptedDataKind -> {
                getStringBlocking(Res.string.encrypt_this_list_of_tags_with, type.toString().split("_").first())
            }
            else -> getStringBlocking(Res.string.encrypt_this_text_with, type.toString().split("_").first())
        }
    } else if (type.toString().contains("DECRYPT")) {
        when (encryptedData) {
            is EventEncryptedDataKind -> {
                val p = Permission("sign_event", encryptedData.event.kind)
                getStringBlocking(Res.string.read_from_encrypted_content, p.localizedBlocking(), type.toString().split("_").first())
            }
            is TagArrayEncryptedDataKind -> {
                getStringBlocking(Res.string.read_this_list_of_tags_from_encrypted_content, type.toString().split("_").first())
            }
            is PrivateZapEncryptedDataKind -> {
                getStringBlocking(Res.string.decrypt_zap_event).replaceFirstChar { it.uppercase() }
            }
            else -> getStringBlocking(Res.string.read_this_text_from_encrypted_content, type.toString().split("_").first())
        }
    } else {
        val permission = if (type == SignerType.SIGN_EVENT) {
            val kind = (bunkerRequest.request as? BunkerRequestSign)?.event?.kind ?: 0
            Permission("sign_event", kind)
        } else {
            Permission(type.toString().lowercase(), null)
        }
        permission.localizedBlocking()
    }
}

private fun bunkerRequestPreview(bunkerRequest: AmberBunkerRequest, type: SignerType): String {
    if (bunkerRequest.request is BunkerRequestSign) {
        val event = bunkerRequest.signedEvent!!
        return if (event.kind == 22242) AmberEvent.relay(event) ?: event.content else event.content
    }
    val encryptedData = bunkerRequest.encryptedData
    return if (type.name.contains("ENCRYPT") && encryptedData is ClearTextEncryptedDataKind) {
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
        encryptedData?.result ?: BunkerRequestUtils.getDataFromBunker(bunkerRequest.request)
    }
}

private const val PREVIEW_LOCAL_KEY = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

private const val PREVIEW_OTHER_LOCAL_KEY = "7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"

private fun previewBunkerRequest(
    request: BunkerRequest,
    signedEvent: Event? = null,
    encryptedData: ClearTextEncryptedDataKind? = null,
    localKey: String = PREVIEW_LOCAL_KEY,
    clientMetadata: BunkerClientMetadata = BunkerClientMetadata(name = "Amethyst", url = "https://amethyst.social"),
) = AmberBunkerRequest(
    request = request,
    localKey = localKey,
    relays = listOf(),
    currentAccount = "",
    nostrConnectSecret = "",
    closeApplication = false,
    name = "",
    signedEvent = signedEvent,
    encryptedData = encryptedData,
    encryptionType = EncryptionType.NIP44,
    isNostrConnectUri = false,
    clientMetadata = clientMetadata,
)

private fun previewSignRequest(id: String, content: String) = previewBunkerRequest(
    request = BunkerRequestSign(
        id = id,
        event = EventTemplate(
            createdAt = 1735689600,
            kind = 1,
            tags = arrayOf(arrayOf("t", "amber")),
            content = content,
        ),
    ),
    signedEvent = Event(
        id = "0".repeat(64),
        pubKey = PREVIEW_LOCAL_KEY,
        createdAt = 1735689600,
        kind = 1,
        tags = arrayOf(arrayOf("t", "amber")),
        content = content,
        sig = "",
    ),
)

@ThemePreviews
@Composable
fun BunkerMultiEventHomeScreenPreview() {
    AmberPreview {
        BunkerMultiEventContent(
            batchRequests = listOf(
                previewBunkerRequest(
                    request = BunkerRequestConnect(
                        id = "preview-connect",
                        remoteKey = PREVIEW_LOCAL_KEY,
                    ),
                ),
                previewSignRequest("preview-sign-1", "Hello Nostr!"),
                previewSignRequest("preview-sign-2", "GM from Amber"),
                previewBunkerRequest(
                    request = BunkerRequestNip44Decrypt(
                        id = "preview-nip44-decrypt",
                        pubKey = PREVIEW_LOCAL_KEY,
                        ciphertext = "encrypted-payload",
                    ),
                    encryptedData = ClearTextEncryptedDataKind("encrypted-payload", "Hello Nostr!"),
                ),
            ),
            accountParam = previewAccount(),
            choices = BunkerGroupChoices(),
            onGroupApprove = { _, _ -> },
            onConfirm = {},
            loadSavedApp = { null },
            modifier = Modifier
                .fillMaxWidth()
                .height(700.dp)
                .padding(16.dp),
        )
    }
}

@ThemePreviews
@Composable
fun BunkerMultiEventHomeScreenMultipleAppsPreview() {
    AmberPreview {
        BunkerMultiEventContent(
            batchRequests = listOf(
                previewSignRequest("preview-sign-1", "Hello Nostr!"),
                previewSignRequest("preview-sign-2", "GM from Amber"),
                previewBunkerRequest(
                    request = BunkerRequestNip44Decrypt(
                        id = "preview-nip44-decrypt",
                        pubKey = PREVIEW_LOCAL_KEY,
                        ciphertext = "encrypted-payload",
                    ),
                    encryptedData = ClearTextEncryptedDataKind("encrypted-payload", "Hello Nostr!"),
                    localKey = PREVIEW_OTHER_LOCAL_KEY,
                    clientMetadata = BunkerClientMetadata(name = "Coracle", url = "https://coracle.social"),
                ),
            ),
            accountParam = previewAccount(),
            choices = BunkerGroupChoices(),
            onGroupApprove = { _, _ -> },
            onConfirm = {},
            loadSavedApp = { null },
            modifier = Modifier
                .fillMaxWidth()
                .height(700.dp)
                .padding(16.dp),
        )
    }
}

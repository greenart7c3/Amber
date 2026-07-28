package com.greenart7c3.nostrsigner.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.BunkerClientMetadata
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.EncryptionType
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.PrivateZapEncryptedDataKind
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.ApplicationNameCache
import com.greenart7c3.nostrsigner.service.BunkerRequestUtils
import com.greenart7c3.nostrsigner.service.EventNotificationConsumer
import com.greenart7c3.nostrsigner.service.MultiEventScreenIntents
import com.greenart7c3.nostrsigner.service.RelayUrlUtils
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.ThemePreviews
import com.greenart7c3.nostrsigner.ui.theme.previewAccount
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.collections.forEach
import kotlin.collections.set
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun BunkerMultiEventHomeScreen(
    modifier: Modifier,
    bunkerRequests: ImmutableList<AmberBunkerRequest>,
    packageName: String?,
    accountParam: Account,
    onLoading: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var localAccount by remember { mutableStateOf("") }
    val key = bunkerRequests.first().localKey
    val groupRememberTypes = remember { mutableStateMapOf<RequestGroupKey, RememberType>() }
    val groupRelayAuthScopes = remember { mutableStateMapOf<RequestGroupKey, RelayAuthScope>() }
    val groupDecryptScopes = remember { mutableStateMapOf<RequestGroupKey, DecryptTypeScope>() }
    var appName by remember { mutableStateOf(ApplicationNameCache["$localAccount-$key"] ?: key.toShortenHex()) }
    var appIcon by remember { mutableStateOf(bunkerRequests.first().clientMetadata?.image ?: "") }

    LaunchedEffect(Unit) {
        MultiEventScreenIntents.checkedStates.clear()
        MultiEventScreenIntents.rememberType = RememberType.NEVER
        // checkedStates now holds the per-request decision: true = Approve, false = Deny.
        bunkerRequests.forEach { MultiEventScreenIntents.checkedStates[it.request.id] = true }
    }

    // Skipped in previews: encrypted storage and the Amber singleton don't
    // exist in the preview renderer.
    if (!LocalInspectionMode.current) {
        LaunchedEffect(Unit) {
            launch(Dispatchers.IO) {
                localAccount = LocalPreferences.loadFromEncryptedStorage(
                    context,
                    bunkerRequests.first().currentAccount,
                )?.npub?.toShortenHex() ?: ""

                val app = Amber.instance.getDatabase(accountParam.npub).dao().getByKey(key)
                if (ApplicationNameCache["$localAccount-$key"] == null) {
                    app?.let {
                        appName = it.application.name
                        ApplicationNameCache["$localAccount-$key"] = it.application.name
                    }
                } else {
                    ApplicationNameCache["$localAccount-$key"]?.let {
                        appName = it
                    }
                }
                app?.application?.icon?.let { if (it.isNotBlank()) appIcon = it }
            }
        }
    }

    Column(
        modifier,
    ) {
        RemoteAppIcon(appIcon, appName)

        Text(
            stringResource(R.string.is_requiring_some_permissions_please_review_them, appName),
            Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        )

        SigningAs(accountParam)

        val groups = remember(bunkerRequests) {
            groupRequests(bunkerRequests) {
                requestGroupKey(
                    type = BunkerRequestUtils.getTypeFromBunker(it.request),
                    eventKind = (it.request as? BunkerRequestSign)?.event?.kind,
                    encryptedData = it.encryptedData,
                    nip44v3Kind = BunkerRequestUtils.getNip44v3Kind(it.request),
                )
            }
        }
        val expandedGroups = remember { mutableStateMapOf<RequestGroupKey, Boolean>() }
        LazyColumn(
            Modifier.weight(1f),
        ) {
            groups.forEach { (groupKey, groupItems) ->
                val expanded = groups.size == 1 || (expandedGroups[groupKey] ?: false)
                if (groups.size > 1) {
                    item(key = "group-header:${groupKey.type.name}:${groupKey.payload?.name ?: ""}:${groupKey.kind ?: ""}") {
                        val groupApproved = groupItems.all { MultiEventScreenIntents.checkedStates[it.request.id] ?: true }
                        RequestGroupHeader(
                            label = groupKey.toLabel(context),
                            count = groupItems.size,
                            approved = groupApproved,
                            expanded = expanded,
                            onApproveChanged = { approve ->
                                MultiEventScreenIntents.checkedStates.putAll(groupItems.associate { it.request.id to approve })
                            },
                            onExpandToggle = {
                                expandedGroups[groupKey] = !(expandedGroups[groupKey] ?: false)
                            },
                        )
                    }
                }
                if (groupKey.hasGroupOptions()) {
                    item(key = "group-options:${groupKey.type.name}:${groupKey.payload?.name ?: ""}:${groupKey.kind ?: ""}") {
                        RequestGroupOptions(
                            groupKey = groupKey,
                            rememberType = groupRememberTypes[groupKey] ?: RememberType.NEVER,
                            onRememberTypeChanged = { groupRememberTypes[groupKey] = it },
                            relayAuthScope = groupRelayAuthScopes[groupKey] ?: RelayAuthScope.SPECIFIC,
                            onRelayAuthScopeChanged = { groupRelayAuthScopes[groupKey] = it },
                            decryptTypeScope = groupDecryptScopes[groupKey] ?: defaultDecryptTypeScope(groupKey.type),
                            onDecryptTypeScopeChanged = { groupDecryptScopes[groupKey] = it },
                        )
                    }
                }
                if (expanded) {
                    items(groupItems, key = { it.request.id }) { bunkerRequest ->
                        BunkerRequestCard(
                            context = context,
                            bunkerRequest = bunkerRequest,
                            approved = MultiEventScreenIntents.checkedStates[bunkerRequest.request.id] ?: true,
                            onApproveChanged = {
                                MultiEventScreenIntents.checkedStates[bunkerRequest.request.id] = it
                            },
                        )
                    }
                }
            }
        }

        AmberButton(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            text = stringResource(R.string.confirm),
            onClick = {
                onLoading(true)
                Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
                    try {
                        reconnectToRelays()
                        val closeApp = bunkerRequests.any { it.closeApplication }
                        BunkerRequestUtils.clearRequests()
                        EventNotificationConsumer(context).notificationManager().cancelAll()
                        finishActivity(closeApp)
                        for (request in bunkerRequests) {
                            val thisAccount =
                                if (request.currentAccount.isNotBlank()) {
                                    LocalPreferences.loadFromEncryptedStorage(
                                        context,
                                        request.currentAccount,
                                    )
                                } else {
                                    accountParam
                                } ?: continue

                            val localKey = request.localKey
                            val dao = Amber.instance.dao(thisAccount.npub)
                            val historyDatabase = Amber.instance.getHistoryDatabase(thisAccount.npub)
                            val savedApplication = dao.getByKey(localKey)

                            val secret = if (request.request is BunkerRequestConnect) {
                                request.request.secret ?: ""
                            } else {
                                ""
                            }

                            val application =
                                savedApplication ?: ApplicationWithPermissions(
                                    application = ApplicationEntity(
                                        localKey,
                                        request.clientMetadata?.name ?: "",
                                        listOf(),
                                        request.clientMetadata?.url ?: "",
                                        request.clientMetadata?.image ?: "",
                                        "",
                                        thisAccount.hexKey,
                                        true,
                                        secret,
                                        secret.isNotBlank(),
                                        thisAccount.signPolicy,
                                        request.closeApplication,
                                        0L,
                                        lastUsed = TimeUtils.now(),
                                    ),
                                    permissions = mutableListOf(),
                                )

                            val isApproved = MultiEventScreenIntents.checkedStates[request.request.id] ?: true
                            val requestType = BunkerRequestUtils.getTypeFromBunker(request.request)
                            val groupKey = requestGroupKey(
                                type = requestType,
                                eventKind = (request.request as? BunkerRequestSign)?.event?.kind,
                                encryptedData = request.encryptedData,
                                nip44v3Kind = BunkerRequestUtils.getNip44v3Kind(request.request),
                            )
                            val rememberType = groupRememberTypes[groupKey] ?: RememberType.NEVER

                            if (request.request is BunkerRequestSign) {
                                val localEvent = request.signedEvent!!

                                if (rememberType != RememberType.NEVER) {
                                    val signRelay = if (localEvent.kind == 22242) {
                                        if ((groupRelayAuthScopes[groupKey] ?: RelayAuthScope.SPECIFIC) == RelayAuthScope.ALL) {
                                            "*"
                                        } else {
                                            RelayUrlUtils.extractHostAndPort(AmberEvent.relay(localEvent))
                                        }
                                    } else {
                                        ""
                                    }
                                    AmberUtils.acceptOrRejectPermission(
                                        application = application,
                                        key = localKey,
                                        signerType = SignerType.SIGN_EVENT,
                                        kind = localEvent.kind,
                                        value = isApproved,
                                        rememberType = rememberType,
                                        account = thisAccount,
                                        relay = signRelay,
                                        encryptedData = request.encryptedData,
                                    )
                                }

                                dao.insertApplicationWithPermissions(application)

                                historyDatabase.dao().addHistory(
                                    listOf(
                                        HistoryEntity(
                                            id = 0,
                                            pkKey = localKey,
                                            type = SignerType.SIGN_EVENT.toString(),
                                            kind = localEvent.kind,
                                            time = TimeUtils.now(),
                                            accepted = isApproved,
                                            content = localEvent.toJson(),
                                        ),
                                    ),
                                    thisAccount.npub,
                                )

                                BunkerRequestUtils.remove(request.request.id)

                                if (isApproved) {
                                    BunkerRequestUtils.sendBunkerResponse(
                                        context,
                                        thisAccount,
                                        request,
                                        BunkerResponse(request.request.id, localEvent.toJson(), null),
                                        application.application.relays,
                                        onLoading = {},
                                        onDone = {},
                                    )
                                } else {
                                    AmberUtils.sendBunkerError(
                                        account = thisAccount,
                                        bunkerRequest = request,
                                        relays = application.application.relays,
                                        context = context,
                                        closeApplication = application.application.closeApplication,
                                        onLoading = {},
                                    )
                                }
                            } else if (request.request is BunkerRequestConnect) {
                                if (savedApplication == null) {
                                    dao.insertApplicationWithPermissions(application)

                                    historyDatabase.dao().addHistory(
                                        listOf(
                                            HistoryEntity(
                                                0,
                                                localKey,
                                                SignerType.CONNECT.toString(),
                                                null,
                                                TimeUtils.now(),
                                                isApproved,
                                                content = "",
                                            ),
                                        ),
                                        thisAccount.npub,
                                    )

                                    BunkerRequestUtils.remove(request.request.id)
                                    if (isApproved) {
                                        BunkerRequestUtils.sendBunkerResponse(
                                            context,
                                            thisAccount,
                                            request,
                                            BunkerResponse(request.request.id, "", null),
                                            application.application.relays,
                                            onLoading = {},
                                            onDone = {},
                                        )
                                    } else {
                                        AmberUtils.sendBunkerError(
                                            account = thisAccount,
                                            bunkerRequest = request,
                                            relays = application.application.relays,
                                            context = context,
                                            closeApplication = application.application.closeApplication,
                                            onLoading = {},
                                        )
                                    }
                                }
                            } else {
                                val type = requestType
                                if (rememberType != RememberType.NEVER) {
                                    val decryptTypeScope = groupDecryptScopes[groupKey] ?: defaultDecryptTypeScope(type)
                                    val permissionKind = if (type == SignerType.NIP44_V3_ENCRYPT || type == SignerType.NIP44_V3_DECRYPT) {
                                        if (decryptTypeScope == DecryptTypeScope.SPECIFIC) BunkerRequestUtils.getNip44v3Kind(request.request) else null
                                    } else {
                                        null
                                    }
                                    AmberUtils.acceptOrRejectPermission(
                                        application,
                                        localKey,
                                        type,
                                        permissionKind,
                                        isApproved,
                                        rememberType,
                                        thisAccount,
                                        encryptedData = request.encryptedData,
                                        decryptTypeScope = decryptTypeScope,
                                    )
                                }

                                dao.insertApplicationWithPermissions(application)

                                historyDatabase.dao().addHistory(
                                    listOf(
                                        HistoryEntity(
                                            0,
                                            localKey,
                                            type.toString(),
                                            null,
                                            TimeUtils.now(),
                                            isApproved,
                                            content = if (type == SignerType.NIP04_DECRYPT || type == SignerType.NIP44_DECRYPT || type == SignerType.DECRYPT_ZAP_EVENT) {
                                                request.encryptedData?.result ?: ""
                                            } else {
                                                request.request.params.getOrElse(1) { "" }
                                            },
                                        ),
                                    ),
                                    thisAccount.npub,
                                )

                                if (isApproved) {
                                    val signature = request.encryptedData?.result
                                    if (signature != null) {
                                        BunkerRequestUtils.remove(request.request.id)
                                        BunkerRequestUtils.sendBunkerResponse(
                                            context,
                                            thisAccount,
                                            request,
                                            BunkerResponse(request.request.id, signature, null),
                                            application.application.relays,
                                            onLoading = {},
                                            onDone = {},
                                        )
                                    }
                                } else {
                                    BunkerRequestUtils.remove(request.request.id)
                                    AmberUtils.sendBunkerError(
                                        account = thisAccount,
                                        bunkerRequest = request,
                                        relays = application.application.relays,
                                        context = context,
                                        closeApplication = application.application.closeApplication,
                                        onLoading = {},
                                    )
                                }
                            }
                        }
                    } finally {
                        onLoading(false)
                    }
                }
            },
        )
    }
}

@Composable
private fun BunkerRequestCard(
    context: Context,
    bunkerRequest: AmberBunkerRequest,
    approved: Boolean,
    onApproveChanged: (Boolean) -> Unit,
) {
    val type = BunkerRequestUtils.getTypeFromBunker(bunkerRequest.request)
    var showDetails by remember { mutableStateOf(false) }
    val hasDetails = (type == SignerType.SIGN_EVENT && bunkerRequest.signedEvent != null) ||
        ((type.toString().contains("ENCRYPT") || type.toString().contains("DECRYPT")) && bunkerRequest.encryptedData != null)
    val permission = if (type == SignerType.SIGN_EVENT) {
        val kind = (bunkerRequest.request as? BunkerRequestSign)?.event?.kind ?: 0
        Permission("sign_event", kind)
    } else {
        Permission(type.toString().toLowerCase(Locale.current), null)
    }

    val label = if (type == SignerType.CONNECT) {
        stringResource(R.string.connect)
    } else {
        val encryptedData = bunkerRequest.encryptedData
        if (type.toString().contains("ENCRYPT")) {
            when (encryptedData) {
                is EventEncryptedDataKind -> {
                    val p = Permission("sign_event", encryptedData.event.kind)
                    stringResource(R.string.encrypt_with, p.toLocalizedString(context), type.toString().split("_").first())
                }
                is TagArrayEncryptedDataKind -> {
                    stringResource(R.string.encrypt_this_list_of_tags_with, type.toString().split("_").first())
                }
                else -> stringResource(R.string.encrypt_this_text_with, type.toString().split("_").first())
            }
        } else if (type.toString().contains("DECRYPT")) {
            when (encryptedData) {
                is EventEncryptedDataKind -> {
                    val p = Permission("sign_event", encryptedData.event.kind)
                    stringResource(R.string.read_from_encrypted_content, p.toLocalizedString(context), type.toString().split("_").first())
                }
                is TagArrayEncryptedDataKind -> {
                    stringResource(R.string.read_this_list_of_tags_from_encrypted_content, type.toString().split("_").first())
                }
                is PrivateZapEncryptedDataKind -> {
                    stringResource(R.string.decrypt_zap_event).capitalize(Locale.current)
                }
                else -> stringResource(R.string.read_this_text_from_encrypted_content, type.toString().split("_").first())
            }
        } else {
            permission.toLocalizedString(context)
        }
    }

    val preview = if (bunkerRequest.request is BunkerRequestSign) {
        val event = bunkerRequest.signedEvent!!
        if (event.kind == 22242) AmberEvent.relay(event) ?: event.content else event.content
    } else {
        val encryptedData = bunkerRequest.encryptedData
        if (type.name.contains("ENCRYPT") && encryptedData is ClearTextEncryptedDataKind) {
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
                    Text(text = label)
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
                            text = stringResource(R.string.show_details),
                        )
                    }
                }
            }

            AmberToggles(
                selected = approved,
                options = listOf(true, false),
                onSelected = onApproveChanged,
                label = {
                    if (it) stringResource(R.string.approve) else stringResource(R.string.deny)
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

private fun finishActivity(closeApp: Boolean) {
    val activity = Amber.instance.getMainActivity()
    activity?.intent = null
    if (closeApp) {
        activity?.finishAndRemoveTask()
    }
}

private suspend fun reconnectToRelays() {
    Amber.instance.checkForNewRelaysAndUpdateAllFilters()
}

private const val PREVIEW_LOCAL_KEY = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

private fun previewBunkerRequest(
    request: BunkerRequest,
    signedEvent: Event? = null,
    encryptedData: ClearTextEncryptedDataKind? = null,
) = AmberBunkerRequest(
    request = request,
    localKey = PREVIEW_LOCAL_KEY,
    relays = listOf(),
    currentAccount = "",
    nostrConnectSecret = "",
    closeApplication = false,
    name = "",
    signedEvent = signedEvent,
    encryptedData = encryptedData,
    encryptionType = EncryptionType.NIP44,
    isNostrConnectUri = false,
    clientMetadata = BunkerClientMetadata(name = "Amethyst", url = "https://amethyst.social"),
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
    // The screen normally resolves the app name from the database, which is
    // skipped in previews — seed the cache so the header shows a real name.
    ApplicationNameCache["-$PREVIEW_LOCAL_KEY"] = "Amethyst"

    AmberPreview {
        BunkerMultiEventHomeScreen(
            modifier = Modifier
                .fillMaxWidth()
                .height(700.dp)
                .padding(16.dp),
            bunkerRequests = persistentListOf(
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
            packageName = null,
            accountParam = previewAccount(),
            onLoading = {},
        )
    }
}

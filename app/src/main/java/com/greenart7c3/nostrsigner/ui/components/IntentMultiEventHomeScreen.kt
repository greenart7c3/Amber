package com.greenart7c3.nostrsigner.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
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
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.PrivateZapEncryptedDataKind
import com.greenart7c3.nostrsigner.models.Result
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.MultiEventScreenIntents
import com.greenart7c3.nostrsigner.service.RelayUrlUtils
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.theme.AmberPreview
import com.greenart7c3.nostrsigner.ui.theme.ThemePreviews
import com.greenart7c3.nostrsigner.ui.theme.previewAccount
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun IntentMultiEventHomeScreen(
    modifier: Modifier,
    intents: ImmutableList<IntentData>,
    packageName: String?,
    accountParam: Account,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val groupRememberTypes = remember { mutableStateMapOf<RequestGroupKey, RememberType>() }
    val groupRelayAuthScopes = remember { mutableStateMapOf<RequestGroupKey, RelayAuthScope>() }
    val groupDecryptScopes = remember { mutableStateMapOf<RequestGroupKey, DecryptTypeScope>() }

    LaunchedEffect(Unit) {
        MultiEventScreenIntents.checkedStates.clear()
        MultiEventScreenIntents.rememberType = RememberType.NEVER
        // checkedStates now holds the per-request decision: true = Approve, false = Deny.
        intents.forEach { MultiEventScreenIntents.checkedStates[it.id] = true }
    }

    Column(
        modifier,
    ) {
        LocalAppIcon(packageName)

        Text(
            stringResource(R.string.is_requiring_some_permissions_please_review_them2),
            Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        )

        SigningAs(accountParam)

        val groups = remember(intents) {
            groupRequests(intents) {
                requestGroupKey(it.type, it.event?.kind, it.encryptedData, it.nip44v3Kind)
            }
        }
        val expandedGroups = remember { mutableStateMapOf<RequestGroupKey, Boolean>() }
        LazyColumn(
            Modifier.weight(1f),
        ) {
            groups.forEach { (groupKey, groupIntents) ->
                val expanded = groups.size == 1 || (expandedGroups[groupKey] ?: false)
                if (groups.size > 1) {
                    item(key = "group-header:${groupKey.type.name}:${groupKey.payload?.name ?: ""}:${groupKey.kind ?: ""}") {
                        val groupApproved = groupIntents.all { MultiEventScreenIntents.checkedStates[it.id] ?: true }
                        RequestGroupHeader(
                            label = groupKey.toLabel(context),
                            count = groupIntents.size,
                            approved = groupApproved,
                            expanded = expanded,
                            onApproveChanged = { approve ->
                                MultiEventScreenIntents.checkedStates.putAll(groupIntents.associate { it.id to approve })
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
                    items(groupIntents, key = { it.id }) { intent ->
                        IntentRequestCard(
                            context = context,
                            intent = intent,
                            approved = MultiEventScreenIntents.checkedStates[intent.id] ?: true,
                            onApproveChanged = {
                                MultiEventScreenIntents.checkedStates[intent.id] = it
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
                        val results = mutableListOf<Result>()
                        var closeApp = true
                        onRemoveIntentData(intents, IntentResultType.REMOVE)
                        val localKey = packageName ?: return@launch
                        val intentsByAccount = intents.groupBy { it.currentAccount.ifBlank { accountParam.npub } }

                        for ((accountNpub, accountIntents) in intentsByAccount) {
                            val thisAccount = if (accountNpub == accountParam.npub) {
                                accountParam
                            } else {
                                LocalPreferences.loadFromEncryptedStorage(context, accountNpub)
                            } ?: continue

                            val dao = Amber.instance.dao(thisAccount.npub)
                            val historyDatabase = Amber.instance.getHistoryDatabase(thisAccount.npub)
                            val application = dao.getByKey(localKey) ?: ApplicationWithPermissions(
                                application = ApplicationEntity(
                                    localKey,
                                    "",
                                    listOf(),
                                    "",
                                    "",
                                    "",
                                    thisAccount.hexKey,
                                    true,
                                    "",
                                    false,
                                    thisAccount.signPolicy,
                                    true,
                                    0L,
                                    lastUsed = TimeUtils.now(),
                                ),
                                permissions = mutableListOf(),
                            )

                            if (!application.application.closeApplication) {
                                closeApp = false
                            }

                            var permissionsChanged = false
                            val historyList = mutableListOf<HistoryEntity>()

                            for (intentData in accountIntents) {
                                val isApproved = MultiEventScreenIntents.checkedStates[intentData.id] ?: true
                                val type = intentData.type
                                val groupKey = requestGroupKey(type, intentData.event?.kind, intentData.encryptedData, intentData.nip44v3Kind)
                                val rememberType = groupRememberTypes[groupKey] ?: RememberType.NEVER

                                if (type == SignerType.SIGN_EVENT) {
                                    val localEvent = intentData.event!!

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
                                        AmberUtils.updatePermission(
                                            application,
                                            localKey,
                                            type,
                                            localEvent.kind,
                                            isApproved,
                                            rememberType,
                                            relay = signRelay,
                                            encryptedData = intentData.encryptedData,
                                        )
                                        permissionsChanged = true
                                    }

                                    historyList.add(
                                        HistoryEntity(
                                            0,
                                            localKey,
                                            type.toString(),
                                            localEvent.kind,
                                            TimeUtils.now(),
                                            isApproved,
                                            content = localEvent.toJson(),
                                        ),
                                    )

                                    if (isApproved) {
                                        val signature = if (localEvent is LnZapRequestEvent &&
                                            localEvent.tags.any { tag ->
                                                tag.any { t -> t == "anon" }
                                            }
                                        ) {
                                            localEvent.toJson()
                                        } else {
                                            localEvent.sig
                                        }
                                        results.add(
                                            Result(
                                                null,
                                                signature = signature,
                                                result = signature,
                                                id = intentData.id,
                                                rejected = null,
                                            ),
                                        )
                                    } else {
                                        results.add(
                                            Result(
                                                null,
                                                signature = null,
                                                result = null,
                                                id = intentData.id,
                                                rejected = true,
                                            ),
                                        )
                                    }
                                } else {
                                    if (rememberType != RememberType.NEVER) {
                                        val decryptTypeScope = groupDecryptScopes[groupKey] ?: defaultDecryptTypeScope(type)
                                        val permissionKind = if (type == SignerType.NIP44_V3_ENCRYPT || type == SignerType.NIP44_V3_DECRYPT) {
                                            if (decryptTypeScope == DecryptTypeScope.SPECIFIC) intentData.nip44v3Kind else null
                                        } else {
                                            null
                                        }
                                        AmberUtils.updatePermission(
                                            application,
                                            localKey,
                                            type,
                                            permissionKind,
                                            isApproved,
                                            rememberType,
                                            encryptedData = intentData.encryptedData,
                                            decryptTypeScope = decryptTypeScope,
                                        )
                                        permissionsChanged = true
                                    }

                                    historyList.add(
                                        HistoryEntity(
                                            0,
                                            localKey,
                                            type.toString(),
                                            null,
                                            TimeUtils.now(),
                                            isApproved,
                                            content = if (type == SignerType.NIP04_DECRYPT || type == SignerType.NIP44_DECRYPT || type == SignerType.DECRYPT_ZAP_EVENT) {
                                                intentData.encryptedData?.result ?: ""
                                            } else {
                                                intentData.data
                                            },
                                        ),
                                    )

                                    if (isApproved) {
                                        val signature = intentData.encryptedData?.result
                                        if (signature != null) {
                                            results.add(
                                                Result(
                                                    null,
                                                    signature = signature,
                                                    result = signature,
                                                    id = intentData.id,
                                                    rejected = null,
                                                ),
                                            )
                                        }
                                    } else {
                                        results.add(
                                            Result(
                                                null,
                                                signature = null,
                                                result = null,
                                                id = intentData.id,
                                                rejected = true,
                                            ),
                                        )
                                    }
                                }
                            }

                            if (permissionsChanged || application.application.key.isBlank()) {
                                dao.insertApplicationWithPermissions(application)
                            }
                            historyDatabase.dao().addHistory(historyList, thisAccount.npub)
                        }

                        if (results.isNotEmpty()) {
                            sendResultIntent(results)
                        }

                        finishActivity(closeApp)
                    } finally {
                        onLoading(false)
                    }
                }
            },
        )
    }
}

@Composable
private fun IntentRequestCard(
    context: Context,
    intent: IntentData,
    approved: Boolean,
    onApproveChanged: (Boolean) -> Unit,
) {
    val type = intent.type
    var showDetails by remember { mutableStateOf(false) }
    val hasDetails = (type == SignerType.SIGN_EVENT && intent.event != null) ||
        ((type.toString().contains("ENCRYPT") || type.toString().contains("DECRYPT")) && intent.encryptedData != null)
    val permission = if (type == SignerType.SIGN_EVENT) {
        Permission("sign_event", intent.event!!.kind)
    } else {
        Permission(type.toString().toLowerCase(Locale.current), null)
    }

    var label = if (type == SignerType.CONNECT) {
        stringResource(R.string.connect)
    } else {
        val encryptedData = intent.encryptedData
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
    val unknownKindString = stringResource(R.string.event_kind, permission.kind?.toString() ?: "")
    if (label == unknownKindString && type == SignerType.SIGN_EVENT) {
        val altTag = intent.event?.tags?.firstOrNull { it.size > 1 && it[0] == "alt" }?.getOrNull(1)
        label = altTag ?: label
    }

    val preview = if (type == SignerType.SIGN_EVENT) {
        val event = intent.event!!
        if (event.kind == 22242) AmberEvent.relay(event) ?: event.content else event.content
    } else {
        val encryptedData = intent.encryptedData
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
            encryptedData?.result ?: ""
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
                event = intent.event!!,
                onDismiss = { showDetails = false },
            )
        } else {
            EncryptDecryptDetailModal(
                type = type,
                encryptedData = intent.encryptedData,
                onDismiss = { showDetails = false },
            )
        }
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

@ThemePreviews
@Composable
fun IntentMultiEventHomeScreenPreview() {
    AmberPreview {
        IntentMultiEventHomeScreen(
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
            packageName = "com.vitorpamplona.amethyst",
            accountParam = previewAccount(),
            onRemoveIntentData = { _, _ -> },
            onLoading = {},
        )
    }
}

private fun finishActivity(closeApp: Boolean) {
    val activity = Amber.instance.getMainActivity()
    activity?.intent = null
    if (closeApp) {
        activity?.finishAndRemoveTask()
    }
}

private fun sendResultIntent(
    results: MutableList<Result>,
) {
    val json = Permission.mapper.writeValueAsString(results)
    val intent = Intent()
    intent.putExtra("results", json)
    Amber.instance.getMainActivity()?.setResult(Activity.RESULT_OK, intent)
}

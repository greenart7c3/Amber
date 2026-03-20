package com.greenart7c3.nostrsigner.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
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
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.PrivateZapEncryptedDataKind
import com.greenart7c3.nostrsigner.models.Result
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.ApplicationNameCache
import com.greenart7c3.nostrsigner.service.MultiEventScreenIntents
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.theme.orange
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.collections.forEach
import kotlin.collections.set
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun IntentMultiEventHomeScreen(
    modifier: Modifier,
    intents: List<IntentData>,
    packageName: String?,
    accountParam: Account,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val hasRelayAuthEvents = intents.any { it.type == SignerType.SIGN_EVENT && it.event?.kind == 22242 }
    var localAccount by remember { mutableStateOf("") }
    val key = "$packageName"
    var rememberType by remember { mutableStateOf(RememberType.NEVER) }
    var relayAuthScope by remember { mutableStateOf(RelayAuthScope.SPECIFIC) }

    LaunchedEffect(Unit) {
        MultiEventScreenIntents.checkedStates.clear()
        MultiEventScreenIntents.rememberType = RememberType.NEVER
        intents.forEach { MultiEventScreenIntents.checkedStates[it.id] = true }
    }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            localAccount = LocalPreferences.loadFromEncryptedStorage(
                context,
                intents.firstOrNull()?.currentAccount ?: "",
            )?.npub?.toShortenHex() ?: ""
        }
    }

    var appName by remember { mutableStateOf(ApplicationNameCache.names["$localAccount-$key"] ?: key.toShortenHex()) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            if (ApplicationNameCache.names["$localAccount-$key"] == null) {
                val app = Amber.instance.getDatabase(accountParam.npub).dao().getByKey(key)
                app?.let {
                    appName = it.application.name
                    ApplicationNameCache.names["$localAccount-$key"] = it.application.name
                }
            } else {
                ApplicationNameCache.names["$localAccount-$key"]?.let {
                    appName = it
                }
            }
        }
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

        val allCheckedState = when {
            intents.all { MultiEventScreenIntents.checkedStates[it.id] ?: true } -> ToggleableState.On
            intents.none { MultiEventScreenIntents.checkedStates[it.id] ?: true } -> ToggleableState.Off
            else -> ToggleableState.Indeterminate
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    val newValue = allCheckedState != ToggleableState.On
                    MultiEventScreenIntents.checkedStates.putAll(intents.associate { it.id to newValue })
                },
        ) {
            TriStateCheckbox(
                state = allCheckedState,
                onClick = {
                    val newValue = allCheckedState != ToggleableState.On
                    MultiEventScreenIntents.checkedStates.putAll(intents.associate { it.id to newValue })
                },
            )
            Text(stringResource(R.string.select_deselect_all))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            intents.forEach { intent ->
                IntentRequestCard(
                    context = context,
                    intent = intent,
                    checked = MultiEventScreenIntents.checkedStates[intent.id] ?: true,
                    onToggleChecked = {
                        val current = MultiEventScreenIntents.checkedStates[intent.id] ?: true
                        MultiEventScreenIntents.checkedStates[intent.id] = !current
                    },
                )
            }
        }

        if (hasRelayAuthEvents) {
            LabeledBorderBox(
                label = stringResource(R.string.relay_auth_scope),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            ) {
                AmberToggles(
                    selectedIndex = if (relayAuthScope == RelayAuthScope.SPECIFIC) 0 else 1,
                    count = 2,
                    segmentWidth = 120.dp,
                ) {
                    ToggleOption(
                        modifier = Modifier.width(120.dp),
                        text = stringResource(R.string.for_this_relay_only),
                        isSelected = relayAuthScope == RelayAuthScope.SPECIFIC,
                        onClick = { relayAuthScope = RelayAuthScope.SPECIFIC },
                    )
                    ToggleOption(
                        modifier = Modifier.width(120.dp),
                        text = stringResource(R.string.for_all_relays),
                        isSelected = relayAuthScope == RelayAuthScope.ALL,
                        onClick = { relayAuthScope = RelayAuthScope.ALL },
                    )
                }
            }
        }

        RememberMyChoice(
            alwaysShow = true,
            shouldRunAcceptOrReject = null,
            onAccept = {},
            onReject = {},
            onChanged = {
                rememberType = it
                MultiEventScreenIntents.rememberType = it
            },
            packageName = packageName,
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AmberButton(
                Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors().copy(
                    containerColor = orange,
                ),
                onClick = {
                    Amber.instance.applicationIOScope.launch {
                        var closeApp = true
                        onRemoveIntentData(intents, IntentResultType.REMOVE)
                        for (intentData in intents) {
                            val thisAccount =
                                if (intentData.currentAccount.isNotBlank()) {
                                    LocalPreferences.loadFromEncryptedStorage(
                                        context,
                                        intentData.currentAccount,
                                    )
                                } else {
                                    accountParam
                                } ?: continue

                            val localKey = packageName ?: continue
                            val database = Amber.instance.getDatabase(thisAccount.npub)
                            val application =
                                database
                                    .dao()
                                    .getByKey(localKey) ?: ApplicationWithPermissions(
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

                            val isChecked = MultiEventScreenIntents.checkedStates[intentData.id] ?: true
                            if (rememberType != RememberType.NEVER && isChecked) {
                                val rejectKind = if (intentData.type == SignerType.SIGN_EVENT) intentData.event?.kind else null
                                val rejectRelay = if (intentData.type == SignerType.SIGN_EVENT && intentData.event?.kind == 22242) {
                                    if (relayAuthScope == RelayAuthScope.ALL) {
                                        "*"
                                    } else {
                                        (
                                            AmberEvent.relay(intentData.event)?.let { url ->
                                                try {
                                                    java.net.URI(url).host ?: url
                                                } catch (e: Exception) {
                                                    url
                                                }
                                            } ?: ""
                                            )
                                    }
                                } else {
                                    ""
                                }
                                AmberUtils.acceptOrRejectPermission(
                                    application,
                                    localKey,
                                    intentData.type,
                                    rejectKind,
                                    false,
                                    rememberType,
                                    thisAccount,
                                    relay = rejectRelay,
                                )
                            }

                            if (!application.application.closeApplication) {
                                closeApp = false
                            }
                        }
                        sendRejectIntent(
                            results = intents.map {
                                Result(
                                    null,
                                    signature = null,
                                    result = null,
                                    id = it.id,
                                    rejected = true,
                                )
                            }.toMutableList(),
                        )
                        finishActivity(closeApp)
                    }
                },
                text = stringResource(R.string.discard_all),
            )

            AmberButton(
                Modifier.weight(1f),
                text = stringResource(R.string.approve_all),
                onClick = {
                    onLoading(true)
                    Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
                        try {
                            val results = mutableListOf<Result>()
                            var closeApp = true

                            onRemoveIntentData(intents, IntentResultType.REMOVE)

                            for (intentData in intents) {
                                val thisAccount =
                                    if (intentData.currentAccount.isNotBlank()) {
                                        LocalPreferences.loadFromEncryptedStorage(
                                            context,
                                            intentData.currentAccount,
                                        )
                                    } else {
                                        accountParam
                                    } ?: continue

                                val localKey = packageName ?: continue

                                val database = Amber.instance.getDatabase(thisAccount.npub)
                                val historyDatabase = Amber.instance.getHistoryDatabase(thisAccount.npub)
                                val savedApplication = database.dao().getByKey(localKey)

                                val application =
                                    savedApplication ?: ApplicationWithPermissions(
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

                                val isChecked = MultiEventScreenIntents.checkedStates[intentData.id] ?: true

                                if (intentData.type == SignerType.SIGN_EVENT) {
                                    val localEvent = intentData.event!!

                                    if (rememberType != RememberType.NEVER && isChecked) {
                                        val signRelay = if (localEvent.kind == 22242) {
                                            if (relayAuthScope == RelayAuthScope.ALL) {
                                                "*"
                                            } else {
                                                (
                                                    AmberEvent.relay(localEvent)?.let { url ->
                                                        try {
                                                            java.net.URI(url).host ?: url
                                                        } catch (e: Exception) {
                                                            url
                                                        }
                                                    } ?: ""
                                                    )
                                            }
                                        } else {
                                            ""
                                        }
                                        AmberUtils.acceptOrRejectPermission(
                                            application,
                                            localKey,
                                            intentData.type,
                                            localEvent.kind,
                                            true,
                                            rememberType,
                                            thisAccount,
                                            relay = signRelay,
                                        )
                                    }

                                    database.dao().insertApplicationWithPermissions(application)

                                    historyDatabase.dao().addHistory(
                                        HistoryEntity(
                                            0,
                                            localKey,
                                            intentData.type.toString(),
                                            localEvent.kind,
                                            TimeUtils.now(),
                                            isChecked,
                                            content = localEvent.toJson(),
                                        ),
                                        thisAccount.npub,
                                    )

                                    if (isChecked) {
                                        results.add(
                                            Result(
                                                null,
                                                signature = if (localEvent is LnZapRequestEvent &&
                                                    localEvent.tags.any { tag ->
                                                        tag.any { t -> t == "anon" }
                                                    }
                                                ) {
                                                    localEvent.toJson()
                                                } else {
                                                    localEvent.sig
                                                },
                                                result = if (localEvent is LnZapRequestEvent &&
                                                    localEvent.tags.any { tag ->
                                                        tag.any { t -> t == "anon" }
                                                    }
                                                ) {
                                                    localEvent.toJson()
                                                } else {
                                                    localEvent.sig
                                                },
                                                id = intentData.id,
                                                rejected = null,
                                            ),
                                        )
                                    }
                                } else if (intentData.type == SignerType.SIGN_MESSAGE) {
                                    if (rememberType != RememberType.NEVER && isChecked) {
                                        AmberUtils.acceptOrRejectPermission(
                                            application,
                                            localKey,
                                            intentData.type,
                                            null,
                                            true,
                                            rememberType,
                                            thisAccount,
                                        )
                                    }

                                    database.dao().insertApplicationWithPermissions(application)
                                    historyDatabase.dao().addHistory(
                                        HistoryEntity(
                                            0,
                                            localKey,
                                            intentData.type.toString(),
                                            null,
                                            TimeUtils.now(),
                                            isChecked,
                                            content = intentData.data,
                                        ),
                                        thisAccount.npub,
                                    )

                                    val signedMessage = thisAccount.signString(intentData.data)
                                    if (isChecked) {
                                        results.add(
                                            Result(
                                                null,
                                                signature = signedMessage,
                                                result = signedMessage,
                                                id = intentData.id,
                                                rejected = null,
                                            ),
                                        )
                                    }
                                } else {
                                    if (rememberType != RememberType.NEVER && isChecked) {
                                        AmberUtils.acceptOrRejectPermission(
                                            application,
                                            localKey,
                                            intentData.type,
                                            null,
                                            true,
                                            rememberType,
                                            thisAccount,
                                        )
                                    }

                                    database.dao().insertApplicationWithPermissions(application)

                                    historyDatabase.dao().addHistory(
                                        HistoryEntity(
                                            0,
                                            localKey,
                                            intentData.type.toString(),
                                            null,
                                            TimeUtils.now(),
                                            isChecked,
                                            content = if (intentData.type == SignerType.NIP04_DECRYPT || intentData.type == SignerType.NIP44_DECRYPT || intentData.type == SignerType.DECRYPT_ZAP_EVENT) {
                                                intentData.encryptedData?.result ?: ""
                                            } else {
                                                intentData.data
                                            },
                                        ),
                                        thisAccount.npub,
                                    )

                                    val signature = intentData.encryptedData?.result ?: continue
                                    if (isChecked) {
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
                                }
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
}

@Composable
private fun IntentRequestCard(
    context: Context,
    intent: IntentData,
    checked: Boolean,
    onToggleChecked: () -> Unit,
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

    val label = if (type == SignerType.CONNECT) {
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleChecked() },
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggleChecked() },
                colors = CheckboxDefaults.colors().copy(
                    uncheckedBorderColor = Color.Gray,
                ),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(top = 8.dp, bottom = 8.dp, end = 8.dp),
            ) {
                Text(
                    text = label,
                    color = if (checked) Color.Unspecified else Color.Gray,
                )
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

private fun finishActivity(closeApp: Boolean) {
    val activity = Amber.instance.getMainActivity()
    activity?.intent = null
    if (closeApp) {
        activity?.finishAndRemoveTask()
    }
}

private fun sendRejectIntent(
    results: MutableList<Result>,
) {
    val json = Permission.mapper.writeValueAsString(results)
    val intent = Intent()
    intent.putExtra("results", json)
    Amber.instance.getMainActivity()?.setResult(Activity.RESULT_OK, intent)
}

private fun sendResultIntent(
    results: MutableList<Result>,
) {
    val json = Permission.mapper.writeValueAsString(results)
    val intent = Intent()
    intent.putExtra("results", json)
    Amber.instance.getMainActivity()?.setResult(Activity.RESULT_OK, intent)
}

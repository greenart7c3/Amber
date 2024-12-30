package com.greenart7c3.nostrsigner.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.gson.GsonBuilder
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.BunkerResponse
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.ApplicationNameCache
import com.greenart7c3.nostrsigner.service.EventNotificationConsumer
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.MultiEventScreenIntents
import com.greenart7c3.nostrsigner.service.getAppCompatActivity
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.IntentResultType
import com.greenart7c3.nostrsigner.ui.Result
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.orange
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun MultiEventHomeScreen(
    paddingValues: PaddingValues,
    intents: List<IntentData>,
    packageName: String?,
    accountParam: Account,
    navController: NavController,
    onRemoveIntentData: (IntentData, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val grouped = intents.groupBy { it.type }.filter { it.key != SignerType.SIGN_EVENT }
    val grouped2 = intents.filter { it.type == SignerType.SIGN_EVENT }.groupBy { it.event?.kind }
    val acceptEventsGroup1 = grouped.map {
        remember {
            mutableStateOf(true)
        }
    }
    val acceptEventsGroup2 = grouped2.map {
        remember {
            mutableStateOf(true)
        }
    }
    var localAccount by remember { mutableStateOf("") }
    val key = intents.firstOrNull()?.bunkerRequest?.localKey ?: "$packageName"
    var rememberMyChoice by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            localAccount = LocalPreferences.loadFromEncryptedStorage(
                context,
                intents.firstOrNull()?.currentAccount ?: "",
            )?.signer?.keyPair?.pubKey?.toNpub()?.toShortenHex() ?: ""
        }
    }

    val appName = ApplicationNameCache.names["$localAccount-$key"] ?: key.toShortenHex()

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(paddingValues),
    ) {
        Text(
            stringResource(R.string.is_requiring_some_permissions_please_review_them, appName),
            Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        )

        grouped.toList().forEachIndexed { index, it ->
            PermissionCard(
                context = context,
                acceptEventsGroup = acceptEventsGroup1,
                index = index,
                item = it,
                onDetailsClick = {
                    MultiEventScreenIntents.intents = it
                    MultiEventScreenIntents.appName = appName
                    navController.navigate(Route.SeeDetails.route)
                },
            )
        }
        grouped2.toList().forEachIndexed { index, it ->
            PermissionCard(
                context = context,
                acceptEventsGroup = acceptEventsGroup2,
                index = index,
                item = it,
                onDetailsClick = {
                    MultiEventScreenIntents.intents = it
                    MultiEventScreenIntents.appName = appName
                    navController.navigate(Route.SeeDetails.route)
                },
            )
        }

        AlwaysApproveSwitch(
            checked = rememberMyChoice,
            onClick = {
                rememberMyChoice = !rememberMyChoice
                intents.forEach {
                    it.rememberMyChoice.value = rememberMyChoice
                }
            },
        )

        AmberButton(
            Modifier.padding(bottom = 40.dp),
            text = stringResource(R.string.approve_selected),
            onClick = {
                onLoading(true)
                NostrSigner.getInstance().applicationIOScope.launch(Dispatchers.IO) {
                    try {
                        val activity = context.getAppCompatActivity()
                        val results = mutableListOf<Result>()
                        reconnectToRelays(intents)
                        var closeApp = true

                        for (intentData in intents) {
                            val localAccount =
                                if (intentData.currentAccount.isNotBlank()) {
                                    LocalPreferences.loadFromEncryptedStorage(
                                        context,
                                        intentData.currentAccount,
                                    )
                                } else {
                                    accountParam
                                } ?: continue

                            val key = intentData.bunkerRequest?.localKey ?: packageName ?: continue

                            val database = NostrSigner.getInstance().getDatabase(localAccount.signer.keyPair.pubKey.toNpub())
                            val savedApplication = database.applicationDao().getByKey(key)

                            val application =
                                savedApplication ?: ApplicationWithPermissions(
                                    application = ApplicationEntity(
                                        key,
                                        "",
                                        listOf(),
                                        "",
                                        "",
                                        "",
                                        localAccount.signer.keyPair.pubKey.toHexKey(),
                                        true,
                                        intentData.bunkerRequest?.secret ?: "",
                                        intentData.bunkerRequest?.secret != null,
                                        localAccount.signPolicy,
                                        intentData.bunkerRequest?.closeApplication ?: true,
                                    ),
                                    permissions = mutableListOf(),
                                )

                            if (!application.application.closeApplication) {
                                closeApp = false
                            }

                            if (intentData.type == SignerType.SIGN_EVENT) {
                                val localEvent = intentData.event!!

                                if (intentData.rememberMyChoice.value && intentData.checked.value) {
                                    AmberUtils.acceptOrRejectPermission(
                                        application,
                                        key,
                                        intentData,
                                        localEvent.kind,
                                        intentData.rememberMyChoice.value,
                                        database,
                                    )
                                }

                                database.applicationDao().insertApplicationWithPermissions(application)

                                database.applicationDao().addHistory(
                                    HistoryEntity(
                                        0,
                                        key,
                                        intentData.type.toString(),
                                        localEvent.kind,
                                        TimeUtils.now(),
                                        intentData.checked.value,
                                    ),
                                )

                                if (intentData.bunkerRequest != null) {
                                    val localIntentData = intentData.copy()
                                    onRemoveIntentData(intentData, IntentResultType.REMOVE)
                                    IntentUtils.remove(intentData.bunkerRequest.id)

                                    if (intentData.checked.value) {
                                        IntentUtils.sendBunkerResponse(
                                            context,
                                            localAccount,
                                            intentData.bunkerRequest,
                                            BunkerResponse(intentData.bunkerRequest.id, localEvent.toJson(), null),
                                            application.application.relays,
                                            onLoading = {},
                                            onDone = {
                                                if (!it) {
                                                    IntentUtils.addRequest(localIntentData.bunkerRequest!!)
                                                }
                                            },
                                        )
                                    } else {
                                        AmberUtils.sendBunkerError(
                                            intentData,
                                            localAccount,
                                            intentData.bunkerRequest,
                                            relays = application.application.relays,
                                            context = context,
                                            closeApplication = application.application.closeApplication,
                                            onRemoveIntentData = onRemoveIntentData,
                                            onLoading = {},
                                        )
                                    }
                                } else {
                                    if (intentData.checked.value) {
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
                                            ),
                                        )
                                    }
                                }
                            } else if (intentData.type == SignerType.SIGN_MESSAGE) {
                                if (intentData.rememberMyChoice.value && intentData.checked.value) {
                                    AmberUtils.acceptOrRejectPermission(
                                        application,
                                        key,
                                        intentData,
                                        null,
                                        intentData.rememberMyChoice.value,
                                        database,
                                    )
                                }

                                database.applicationDao().insertApplicationWithPermissions(application)
                                database.applicationDao().addHistory(
                                    HistoryEntity(
                                        0,
                                        key,
                                        intentData.type.toString(),
                                        null,
                                        TimeUtils.now(),
                                        intentData.checked.value,
                                    ),
                                )

                                val signedMessage = CryptoUtils.signString(intentData.data, localAccount.signer.keyPair.privKey!!).toHexKey()

                                if (intentData.bunkerRequest != null) {
                                    val localIntentData = intentData.copy()
                                    onRemoveIntentData(intentData, IntentResultType.REMOVE)
                                    IntentUtils.remove(intentData.bunkerRequest.id)

                                    if (intentData.checked.value) {
                                        IntentUtils.sendBunkerResponse(
                                            context,
                                            localAccount,
                                            intentData.bunkerRequest,
                                            BunkerResponse(intentData.bunkerRequest.id, signedMessage, null),
                                            application.application.relays,
                                            onLoading = {},
                                            onDone = {
                                                if (!it) {
                                                    IntentUtils.addRequest(localIntentData.bunkerRequest!!)
                                                }
                                            },
                                        )
                                    } else {
                                        AmberUtils.sendBunkerError(
                                            intentData,
                                            localAccount,
                                            intentData.bunkerRequest,
                                            relays = application.application.relays,
                                            context = context,
                                            closeApplication = application.application.closeApplication,
                                            onRemoveIntentData = onRemoveIntentData,
                                            onLoading = {},
                                        )
                                    }
                                } else {
                                    if (intentData.checked.value) {
                                        results.add(
                                            Result(
                                                null,
                                                signature = signedMessage,
                                                result = signedMessage,
                                                id = intentData.id,
                                            ),
                                        )
                                    }
                                }
                            } else if (intentData.type == SignerType.CONNECT) {
                                if (savedApplication != null) {
                                    onRemoveIntentData(intentData, IntentResultType.REMOVE)
                                } else {
                                    database.applicationDao().insertApplicationWithPermissions(application)

                                    database.applicationDao().addHistory(
                                        HistoryEntity(
                                            0,
                                            key,
                                            intentData.type.toString(),
                                            null,
                                            TimeUtils.now(),
                                            intentData.checked.value,
                                        ),
                                    )

                                    if (intentData.bunkerRequest != null) {
                                        val localIntentData = intentData.copy()
                                        onRemoveIntentData(intentData, IntentResultType.REMOVE)
                                        IntentUtils.remove(intentData.bunkerRequest.id)
                                        if (intentData.checked.value) {
                                            IntentUtils.sendBunkerResponse(
                                                context,
                                                localAccount,
                                                intentData.bunkerRequest,
                                                BunkerResponse(intentData.bunkerRequest.id, "", null),
                                                application.application.relays,
                                                onLoading = {},
                                                onDone = {
                                                    if (!it) {
                                                        IntentUtils.addRequest(localIntentData.bunkerRequest!!)
                                                    }
                                                },
                                            )
                                        } else {
                                            AmberUtils.sendBunkerError(
                                                intentData,
                                                localAccount,
                                                intentData.bunkerRequest,
                                                relays = application.application.relays,
                                                context = context,
                                                closeApplication = application.application.closeApplication,
                                                onRemoveIntentData = onRemoveIntentData,
                                                onLoading = {},
                                            )
                                        }
                                    } else {
                                        if (intentData.checked.value) {
                                            results.add(
                                                Result(
                                                    null,
                                                    signature = "",
                                                    result = "",
                                                    id = intentData.id,
                                                ),
                                            )
                                        }
                                    }
                                }
                            } else {
                                if (intentData.rememberMyChoice.value && intentData.checked.value) {
                                    AmberUtils.acceptOrRejectPermission(
                                        application,
                                        key,
                                        intentData,
                                        null,
                                        intentData.rememberMyChoice.value,
                                        database,
                                    )
                                }

                                database.applicationDao().insertApplicationWithPermissions(application)

                                database.applicationDao().addHistory(
                                    HistoryEntity(
                                        0,
                                        key,
                                        intentData.type.toString(),
                                        null,
                                        TimeUtils.now(),
                                        intentData.checked.value,
                                    ),
                                )

                                val signature = intentData.encryptedData ?: continue

                                if (intentData.bunkerRequest != null) {
                                    val localIntentData = intentData.copy()
                                    onRemoveIntentData(intentData, IntentResultType.REMOVE)
                                    IntentUtils.remove(intentData.bunkerRequest.id)
                                    if (intentData.checked.value) {
                                        IntentUtils.sendBunkerResponse(
                                            context,
                                            localAccount,
                                            intentData.bunkerRequest,
                                            BunkerResponse(intentData.bunkerRequest.id, signature, null),
                                            application.application.relays,
                                            onLoading = {},
                                            onDone = {
                                                if (!it) {
                                                    IntentUtils.addRequest(localIntentData.bunkerRequest!!)
                                                }
                                            },
                                        )
                                    } else {
                                        AmberUtils.sendBunkerError(
                                            intentData,
                                            localAccount,
                                            intentData.bunkerRequest,
                                            relays = application.application.relays,
                                            context = context,
                                            closeApplication = application.application.closeApplication,
                                            onRemoveIntentData = onRemoveIntentData,
                                            onLoading = {},
                                        )
                                    }
                                } else {
                                    if (intentData.checked.value) {
                                        results.add(
                                            Result(
                                                null,
                                                signature = signature,
                                                result = signature,
                                                id = intentData.id,
                                            ),
                                        )
                                    }
                                }
                            }
                        }

                        if (results.isNotEmpty()) {
                            sendResultIntent(results, activity)
                        }
                        if (intents.any { it.bunkerRequest != null }) {
                            EventNotificationConsumer(context).notificationManager().cancelAll()
                            finishActivity(activity, closeApp)
                        } else {
                            finishActivity(activity, closeApp)
                        }
                    } finally {
                        onLoading(false)
                    }
                }
            },
        )

        AmberButton(
            Modifier.padding(top = 20.dp, bottom = 40.dp),
            colors = ButtonDefaults.buttonColors().copy(
                containerColor = orange,
            ),
            onClick = {
                NostrSigner.getInstance().applicationIOScope.launch {
                    var closeApp = true

                    for (intentData in intents) {
                        val localAccount =
                            if (intentData.currentAccount.isNotBlank()) {
                                LocalPreferences.loadFromEncryptedStorage(
                                    context,
                                    intentData.currentAccount,
                                )
                            } else {
                                accountParam
                            } ?: continue

                        val key = intentData.bunkerRequest?.localKey ?: packageName ?: continue

                        val database = NostrSigner.getInstance().getDatabase(localAccount.signer.keyPair.pubKey.toNpub())

                        val application =
                            database
                                .applicationDao()
                                .getByKey(key) ?: ApplicationWithPermissions(
                                application = ApplicationEntity(
                                    key,
                                    "",
                                    listOf(),
                                    "",
                                    "",
                                    "",
                                    localAccount.signer.keyPair.pubKey.toHexKey(),
                                    true,
                                    intentData.bunkerRequest?.secret ?: "",
                                    intentData.bunkerRequest?.secret != null,
                                    localAccount.signPolicy,
                                    intentData.bunkerRequest?.closeApplication ?: true,
                                ),
                                permissions = mutableListOf(),
                            )

                        if (!application.application.closeApplication) {
                            closeApp = false
                            break
                        }
                    }

                    val activity = context.getAppCompatActivity()
                    if (intents.any { it.bunkerRequest != null }) {
                        EventNotificationConsumer(context).notificationManager().cancelAll()
                        finishActivity(activity, closeApp)
                    } else {
                        finishActivity(activity, closeApp)
                    }
                }
            },
            text = stringResource(R.string.discard_all_requests),
        )
    }
}

private fun finishActivity(activity: AppCompatActivity?, closeApp: Boolean) {
    activity?.intent = null
    if (closeApp) {
        activity?.finish()
    }
}

private fun sendResultIntent(
    results: MutableList<Result>,
    activity: AppCompatActivity?,
) {
    val gson = GsonBuilder().serializeNulls().create()
    val json = gson.toJson(results)
    val intent = Intent()
    intent.putExtra("results", json)
    activity?.setResult(Activity.RESULT_OK, intent)
}

private suspend fun reconnectToRelays(intents: List<IntentData>) {
    if (!intents.any { it.bunkerRequest != null }) return

    NostrSigner.getInstance().checkForNewRelays()
}

@Composable
fun PermissionCard(
    context: Context,
    acceptEventsGroup: List<MutableState<Boolean>>,
    index: Int,
    item: Pair<Any?, List<IntentData>>,
    onDetailsClick: (List<IntentData>) -> Unit,
) {
    Card(
        Modifier
            .padding(4.dp),
        colors = CardDefaults.cardColors().copy(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        border = BorderStroke(1.dp, Color.Gray),
    ) {
        Column(
            Modifier
                .padding(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        acceptEventsGroup[index].value = !acceptEventsGroup[index].value
                        item.second.forEach { it.checked.value = acceptEventsGroup[index].value }
                    },
            ) {
                Checkbox(
                    checked = acceptEventsGroup[index].value,
                    onCheckedChange = { _ ->
                        acceptEventsGroup[index].value = !acceptEventsGroup[index].value
                        item.second.forEach { it.checked.value = acceptEventsGroup[index].value }
                    },
                    colors = CheckboxDefaults.colors().copy(
                        uncheckedBorderColor = Color.Gray,
                    ),
                )
                val first = item.first
                val permission = if (first is Int) {
                    Permission("sign_event", first)
                } else {
                    Permission(first.toString().toLowerCase(Locale.current), null)
                }

                val message = if (first == SignerType.CONNECT) {
                    stringResource(R.string.connect)
                } else {
                    permission.toLocalizedString(context)
                }

                Text(
                    modifier = Modifier.weight(1f),
                    text = message,
                    color = if (acceptEventsGroup[index].value) Color.Unspecified else Color.Gray,
                )
            }
            if (acceptEventsGroup[index].value) {
                val selected = item.second.filter { it.checked.value }.size
                val total = item.second.size
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDetailsClick(item.second)
                        }
                        .padding(end = 8.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.of_events, selected, total),
                        modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
                        color = Color.Gray,
                        fontSize = 14.sp,
                    )

                    Text(
                        buildAnnotatedString {
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "See_details",
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            textDecoration = TextDecoration.Underline,
                                        ),
                                    ),
                                    linkInteractionListener = {
                                        onDetailsClick(item.second)
                                    },
                                ),
                            ) {
                                append(stringResource(R.string.see_details))
                            }
                        },
                        modifier = Modifier.padding(start = 46.dp, bottom = 8.dp),
                        color = Color.Gray,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun AlwaysApproveSwitch(
    checked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(vertical = 40.dp)
            .clickable {
                onClick()
            },
    ) {
        Switch(
            modifier = Modifier.scale(0.85f),
            checked = checked,
            onCheckedChange = {
                onClick()
            },
        )
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            text = stringResource(R.string.always_approve_these_permissions),
        )
    }
}

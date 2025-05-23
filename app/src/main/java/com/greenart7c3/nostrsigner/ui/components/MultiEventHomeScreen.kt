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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryEntity2
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.ApplicationNameCache
import com.greenart7c3.nostrsigner.service.BunkerRequestUtils
import com.greenart7c3.nostrsigner.service.EventNotificationConsumer
import com.greenart7c3.nostrsigner.service.MultiEventScreenIntents
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.Result
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.orange
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip55AndroidSigner.signString
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun MultiEventHomeScreen(
    modifier: Modifier,
    intents: List<IntentData>,
    packageName: String?,
    accountParam: Account,
    navController: NavController,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
) {
    if (intents.first().bunkerRequest != null) {
        BunkerMultiEventHomeScreen(
            modifier = modifier,
            intents = intents.filter { it.bunkerRequest != null },
            packageName = packageName,
            accountParam = accountParam,
            navController = navController,
            onRemoveIntentData = onRemoveIntentData,
            onLoading = onLoading,
        )
    } else {
        IntentMultiEventHomeScreen(
            modifier = modifier,
            intents = intents.filter { it.bunkerRequest == null },
            packageName = packageName,
            accountParam = accountParam,
            navController = navController,
            onRemoveIntentData = onRemoveIntentData,
            onLoading = onLoading,
        )
    }
}

@Composable
fun IntentMultiEventHomeScreen(
    modifier: Modifier,
    intents: List<IntentData>,
    packageName: String?,
    accountParam: Account,
    navController: NavController,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
) {
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
    val key = "$packageName"
    var rememberType by remember { mutableStateOf(RememberType.NEVER) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            localAccount = LocalPreferences.loadFromEncryptedStorage(
                context,
                intents.firstOrNull()?.currentAccount ?: "",
            )?.npub?.toShortenHex() ?: ""
        }
    }

    val appName = ApplicationNameCache.names["$localAccount-$key"] ?: key.toShortenHex()

    Column(
        modifier,
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

        Row(
            Modifier.padding(vertical = 40.dp),
        ) {
            RememberMyChoice(
                alwaysShow = true,
                shouldRunAcceptOrReject = null,
                onAccept = {},
                onReject = {},
                onChanged = {
                    rememberType = it
                    intents.forEach {
                        it.rememberType.value = rememberType
                    }
                },
                packageName = packageName,
            )
        }

        AmberButton(
            Modifier.padding(bottom = 40.dp),
            text = stringResource(R.string.approve_selected),
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

                            val localKey = intentData.bunkerRequest?.localKey ?: packageName ?: continue

                            val database = Amber.instance.getDatabase(thisAccount.npub)
                            val savedApplication = database.applicationDao().getByKey(localKey)

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
                                    ),
                                    permissions = mutableListOf(),
                                )

                            if (!application.application.closeApplication) {
                                closeApp = false
                            }

                            if (intentData.type == SignerType.SIGN_EVENT) {
                                val localEvent = intentData.event!!

                                if (intentData.rememberType.value != RememberType.NEVER && intentData.checked.value) {
                                    AmberUtils.acceptOrRejectPermission(
                                        application,
                                        localKey,
                                        intentData.type,
                                        localEvent.kind,
                                        true,
                                        intentData.rememberType.value,
                                        thisAccount,
                                    )
                                }

                                database.applicationDao().insertApplicationWithPermissions(application)

                                database.applicationDao().addHistory(
                                    HistoryEntity2(
                                        0,
                                        localKey,
                                        intentData.type.toString(),
                                        localEvent.kind,
                                        TimeUtils.now(),
                                        intentData.checked.value,
                                    ),
                                )

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
                            } else if (intentData.type == SignerType.SIGN_MESSAGE) {
                                if (intentData.rememberType.value != RememberType.NEVER && intentData.checked.value) {
                                    AmberUtils.acceptOrRejectPermission(
                                        application,
                                        localKey,
                                        intentData.type,
                                        null,
                                        true,
                                        intentData.rememberType.value,
                                        thisAccount,
                                    )
                                }

                                database.applicationDao().insertApplicationWithPermissions(application)
                                database.applicationDao().addHistory(
                                    HistoryEntity2(
                                        0,
                                        localKey,
                                        intentData.type.toString(),
                                        null,
                                        TimeUtils.now(),
                                        intentData.checked.value,
                                    ),
                                )

                                val signedMessage = signString(intentData.data, thisAccount.signer.keyPair.privKey!!).toHexKey()
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
                            } else {
                                if (intentData.rememberType.value != RememberType.NEVER && intentData.checked.value) {
                                    AmberUtils.acceptOrRejectPermission(
                                        application,
                                        localKey,
                                        intentData.type,
                                        null,
                                        true,
                                        intentData.rememberType.value,
                                        thisAccount,
                                    )
                                }

                                database.applicationDao().insertApplicationWithPermissions(application)

                                database.applicationDao().addHistory(
                                    HistoryEntity2(
                                        0,
                                        localKey,
                                        intentData.type.toString(),
                                        null,
                                        TimeUtils.now(),
                                        intentData.checked.value,
                                    ),
                                )

                                val signature = intentData.encryptedData ?: continue
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

                        if (results.isNotEmpty()) {
                            sendResultIntent(results)
                        }
                        if (intents.any { it.bunkerRequest != null }) {
                            EventNotificationConsumer(context).notificationManager().cancelAll()
                            finishActivity(closeApp)
                        } else {
                            finishActivity(closeApp)
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
                                .applicationDao()
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
                                    intentData.bunkerRequest?.secret ?: "",
                                    intentData.bunkerRequest?.secret != null,
                                    thisAccount.signPolicy,
                                    intentData.bunkerRequest?.closeApplication != false,
                                ),
                                permissions = mutableListOf(),
                            )

                        if (intentData.rememberType.value != RememberType.NEVER && intentData.checked.value) {
                            AmberUtils.acceptOrRejectPermission(
                                application,
                                localKey,
                                intentData.type,
                                null,
                                false,
                                intentData.rememberType.value,
                                thisAccount,
                            )
                        }

                        if (!application.application.closeApplication) {
                            closeApp = false
                        }
                    }

                    finishActivity(closeApp)
                }
            },
            text = stringResource(R.string.discard_all_requests),
        )
    }
}

@Composable
fun BunkerMultiEventHomeScreen(
    modifier: Modifier,
    intents: List<IntentData>,
    packageName: String?,
    accountParam: Account,
    navController: NavController,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
) {
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
    var rememberType by remember { mutableStateOf(RememberType.NEVER) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            localAccount = LocalPreferences.loadFromEncryptedStorage(
                context,
                intents.firstOrNull()?.currentAccount ?: "",
            )?.npub?.toShortenHex() ?: ""
        }
    }

    val appName = ApplicationNameCache.names["$localAccount-$key"] ?: key.toShortenHex()

    Column(
        modifier,
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

        Row(
            Modifier.padding(vertical = 40.dp),
        ) {
            RememberMyChoice(
                alwaysShow = true,
                shouldRunAcceptOrReject = null,
                onAccept = {},
                onReject = {},
                onChanged = {
                    rememberType = it
                    intents.forEach {
                        it.rememberType.value = rememberType
                    }
                },
                packageName = packageName,
            )
        }

        AmberButton(
            Modifier.padding(bottom = 40.dp),
            text = stringResource(R.string.approve_selected),
            onClick = {
                onLoading(true)
                Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
                    try {
                        reconnectToRelays(intents)
                        var closeApp = true

                        BunkerRequestUtils.clearRequests()
                        onRemoveIntentData(intents, IntentResultType.REMOVE)
                        for (intentData in intents) {
                            intentData.bunkerRequest ?: continue

                            val thisAccount =
                                if (intentData.currentAccount.isNotBlank()) {
                                    LocalPreferences.loadFromEncryptedStorage(
                                        context,
                                        intentData.currentAccount,
                                    )
                                } else {
                                    accountParam
                                } ?: continue

                            val localKey = intentData.bunkerRequest.localKey
                            val database = Amber.instance.getDatabase(thisAccount.npub)
                            val savedApplication = database.applicationDao().getByKey(localKey)

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
                                        intentData.bunkerRequest.secret,
                                        intentData.bunkerRequest.secret.isNotBlank(),
                                        thisAccount.signPolicy,
                                        intentData.bunkerRequest.closeApplication,
                                    ),
                                    permissions = mutableListOf(),
                                )

                            if (!application.application.closeApplication) {
                                closeApp = false
                            }

                            if (intentData.type == SignerType.SIGN_EVENT) {
                                val localEvent = intentData.event!!

                                if (intentData.rememberType.value != RememberType.NEVER && intentData.checked.value) {
                                    AmberUtils.acceptOrRejectPermission(
                                        application,
                                        localKey,
                                        intentData.type,
                                        localEvent.kind,
                                        true,
                                        intentData.rememberType.value,
                                        thisAccount,
                                    )
                                }

                                database.applicationDao().insertApplicationWithPermissions(application)

                                database.applicationDao().addHistory(
                                    HistoryEntity2(
                                        0,
                                        localKey,
                                        intentData.type.toString(),
                                        localEvent.kind,
                                        TimeUtils.now(),
                                        intentData.checked.value,
                                    ),
                                )

                                val localIntentData = intentData.copy()
                                BunkerRequestUtils.remove(intentData.bunkerRequest.id)

                                if (intentData.checked.value) {
                                    BunkerRequestUtils.sendBunkerResponse(
                                        context,
                                        thisAccount,
                                        intentData.bunkerRequest,
                                        BunkerResponse(intentData.bunkerRequest.id, localEvent.toJson(), null),
                                        application.application.relays,
                                        onLoading = {},
                                        onDone = {
                                            if (!it) {
                                                BunkerRequestUtils.addRequest(localIntentData.bunkerRequest!!)
                                            }
                                        },
                                    )
                                } else {
                                    AmberUtils.sendBunkerError(
                                        intentData,
                                        thisAccount,
                                        intentData.bunkerRequest,
                                        relays = application.application.relays,
                                        context = context,
                                        closeApplication = application.application.closeApplication,
                                        onRemoveIntentData = onRemoveIntentData,
                                        onLoading = {},
                                    )
                                }
                            } else if (intentData.type == SignerType.SIGN_MESSAGE) {
                                if (intentData.rememberType.value != RememberType.NEVER && intentData.checked.value) {
                                    AmberUtils.acceptOrRejectPermission(
                                        application,
                                        localKey,
                                        intentData.type,
                                        null,
                                        true,
                                        intentData.rememberType.value,
                                        thisAccount,
                                    )
                                }

                                database.applicationDao().insertApplicationWithPermissions(application)
                                database.applicationDao().addHistory(
                                    HistoryEntity2(
                                        0,
                                        localKey,
                                        intentData.type.toString(),
                                        null,
                                        TimeUtils.now(),
                                        intentData.checked.value,
                                    ),
                                )

                                val signedMessage = signString(intentData.data, thisAccount.signer.keyPair.privKey!!).toHexKey()
                                val localIntentData = intentData.copy()
                                BunkerRequestUtils.remove(intentData.bunkerRequest.id)

                                if (intentData.checked.value) {
                                    BunkerRequestUtils.sendBunkerResponse(
                                        context,
                                        thisAccount,
                                        intentData.bunkerRequest,
                                        BunkerResponse(intentData.bunkerRequest.id, signedMessage, null),
                                        application.application.relays,
                                        onLoading = {},
                                        onDone = {
                                            if (!it) {
                                                BunkerRequestUtils.addRequest(localIntentData.bunkerRequest!!)
                                            }
                                        },
                                    )
                                } else {
                                    AmberUtils.sendBunkerError(
                                        intentData,
                                        thisAccount,
                                        intentData.bunkerRequest,
                                        relays = application.application.relays,
                                        context = context,
                                        closeApplication = application.application.closeApplication,
                                        onRemoveIntentData = onRemoveIntentData,
                                        onLoading = {},
                                    )
                                }
                            } else if (intentData.type == SignerType.CONNECT) {
                                if (savedApplication == null) {
                                    database.applicationDao().insertApplicationWithPermissions(application)

                                    database.applicationDao().addHistory(
                                        HistoryEntity2(
                                            0,
                                            localKey,
                                            intentData.type.toString(),
                                            null,
                                            TimeUtils.now(),
                                            intentData.checked.value,
                                        ),
                                    )

                                    val localIntentData = intentData.copy()
                                    BunkerRequestUtils.remove(intentData.bunkerRequest.id)
                                    if (intentData.checked.value) {
                                        BunkerRequestUtils.sendBunkerResponse(
                                            context,
                                            thisAccount,
                                            intentData.bunkerRequest,
                                            BunkerResponse(intentData.bunkerRequest.id, "", null),
                                            application.application.relays,
                                            onLoading = {},
                                            onDone = {
                                                if (!it) {
                                                    BunkerRequestUtils.addRequest(localIntentData.bunkerRequest!!)
                                                }
                                            },
                                        )
                                    } else {
                                        AmberUtils.sendBunkerError(
                                            intentData,
                                            thisAccount,
                                            intentData.bunkerRequest,
                                            relays = application.application.relays,
                                            context = context,
                                            closeApplication = application.application.closeApplication,
                                            onRemoveIntentData = onRemoveIntentData,
                                            onLoading = {},
                                        )
                                    }
                                }
                            } else {
                                if (intentData.rememberType.value != RememberType.NEVER && intentData.checked.value) {
                                    AmberUtils.acceptOrRejectPermission(
                                        application,
                                        localKey,
                                        intentData.type,
                                        null,
                                        true,
                                        intentData.rememberType.value,
                                        thisAccount,
                                    )
                                }

                                database.applicationDao().insertApplicationWithPermissions(application)

                                database.applicationDao().addHistory(
                                    HistoryEntity2(
                                        0,
                                        localKey,
                                        intentData.type.toString(),
                                        null,
                                        TimeUtils.now(),
                                        intentData.checked.value,
                                    ),
                                )

                                val signature = intentData.encryptedData ?: continue
                                val localIntentData = intentData.copy()
                                BunkerRequestUtils.remove(intentData.bunkerRequest.id)
                                if (intentData.checked.value) {
                                    BunkerRequestUtils.sendBunkerResponse(
                                        context,
                                        thisAccount,
                                        intentData.bunkerRequest,
                                        BunkerResponse(intentData.bunkerRequest.id, signature, null),
                                        application.application.relays,
                                        onLoading = {},
                                        onDone = {
                                            if (!it) {
                                                BunkerRequestUtils.addRequest(localIntentData.bunkerRequest!!)
                                            }
                                        },
                                    )
                                } else {
                                    AmberUtils.sendBunkerError(
                                        intentData,
                                        thisAccount,
                                        intentData.bunkerRequest,
                                        relays = application.application.relays,
                                        context = context,
                                        closeApplication = application.application.closeApplication,
                                        onRemoveIntentData = onRemoveIntentData,
                                        onLoading = {},
                                    )
                                }
                            }
                        }
                        EventNotificationConsumer(context).notificationManager().cancelAll()
                        finishActivity(closeApp)
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
                Amber.instance.applicationIOScope.launch {
                    var closeApp = true
                    BunkerRequestUtils.clearRequests()
                    onRemoveIntentData(intents, IntentResultType.REMOVE)
                    for (intentData in intents) {
                        intentData.bunkerRequest ?: continue

                        val thisAccount =
                            if (intentData.currentAccount.isNotBlank()) {
                                LocalPreferences.loadFromEncryptedStorage(
                                    context,
                                    intentData.currentAccount,
                                )
                            } else {
                                accountParam
                            } ?: continue

                        val localKey = intentData.bunkerRequest.localKey
                        val database = Amber.instance.getDatabase(thisAccount.npub)
                        val application =
                            database
                                .applicationDao()
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
                                    intentData.bunkerRequest.secret,
                                    intentData.bunkerRequest.secret.isNotBlank(),
                                    thisAccount.signPolicy,
                                    intentData.bunkerRequest.closeApplication,
                                ),
                                permissions = mutableListOf(),
                            )

                        if (intentData.rememberType.value != RememberType.NEVER && intentData.checked.value) {
                            AmberUtils.acceptOrRejectPermission(
                                application,
                                localKey,
                                intentData.type,
                                null,
                                false,
                                intentData.rememberType.value,
                                thisAccount,
                            )
                        }

                        if (!application.application.closeApplication) {
                            closeApp = false
                        }
                    }

                    EventNotificationConsumer(context).notificationManager().cancelAll()
                    finishActivity(closeApp)
                }
            },
            text = stringResource(R.string.discard_all_requests),
        )
    }
}

private fun finishActivity(closeApp: Boolean) {
    val activity = Amber.instance.getMainActivity()
    activity?.intent = null
    if (closeApp) {
        activity?.finish()
    }
}

private fun sendResultIntent(
    results: MutableList<Result>,
) {
    val gson = GsonBuilder().serializeNulls().create()
    val json = gson.toJson(results)
    val intent = Intent()
    intent.putExtra("results", json)
    Amber.instance.getMainActivity()?.setResult(Activity.RESULT_OK, intent)
}

private suspend fun reconnectToRelays(intents: List<IntentData>) {
    if (!intents.any { it.bunkerRequest != null }) return

    Amber.instance.checkForNewRelays()
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

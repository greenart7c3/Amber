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
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
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
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
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
    bunkerRequests: List<AmberBunkerRequest>,
    packageName: String?,
    accountParam: Account,
    navController: NavController,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
) {
    if (bunkerRequests.isNotEmpty()) {
        BunkerMultiEventHomeScreen(
            modifier = modifier,
            packageName = packageName,
            accountParam = accountParam,
            navController = navController,
            bunkerRequests = bunkerRequests,
            onLoading = onLoading,
        )
    } else {
        IntentMultiEventHomeScreen(
            modifier = modifier,
            intents = intents,
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
                    intents.forEach { intent ->
                        intent.rememberType.value = rememberType
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

                                database.dao().insertApplicationWithPermissions(application)

                                historyDatabase.dao().addHistory(
                                    HistoryEntity(
                                        0,
                                        localKey,
                                        intentData.type.toString(),
                                        localEvent.kind,
                                        TimeUtils.now(),
                                        intentData.checked.value,
                                    ),
                                    thisAccount.npub,
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
                                            rejected = null,
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

                                database.dao().insertApplicationWithPermissions(application)
                                historyDatabase.dao().addHistory(
                                    HistoryEntity(
                                        0,
                                        localKey,
                                        intentData.type.toString(),
                                        null,
                                        TimeUtils.now(),
                                        intentData.checked.value,
                                    ),
                                    thisAccount.npub,
                                )

                                val signedMessage = signString(intentData.data, thisAccount.signer.keyPair.privKey!!).toHexKey()
                                if (intentData.checked.value) {
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

                                database.dao().insertApplicationWithPermissions(application)

                                historyDatabase.dao().addHistory(
                                    HistoryEntity(
                                        0,
                                        localKey,
                                        intentData.type.toString(),
                                        null,
                                        TimeUtils.now(),
                                        intentData.checked.value,
                                    ),
                                    thisAccount.npub,
                                )

                                val signature = intentData.encryptedData ?: continue
                                if (intentData.checked.value) {
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
            text = stringResource(R.string.discard_all_requests),
        )
    }
}

@Composable
fun BunkerMultiEventHomeScreen(
    modifier: Modifier,
    bunkerRequests: List<AmberBunkerRequest>,
    packageName: String?,
    accountParam: Account,
    navController: NavController,
    onLoading: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val grouped = bunkerRequests.groupBy { BunkerRequestUtils.getTypeFromBunker(it.request) }.filter { it.key != SignerType.SIGN_EVENT }
    val grouped2 = bunkerRequests.filter { it.request is BunkerRequestSign }.groupBy { (it.request as BunkerRequestSign).event.kind }
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
    val key = bunkerRequests.first().localKey
    var rememberType by remember { mutableStateOf(RememberType.NEVER) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            localAccount = LocalPreferences.loadFromEncryptedStorage(
                context,
                bunkerRequests.first().currentAccount,
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
            BunkerPermissionCard(
                context = context,
                acceptEventsGroup = acceptEventsGroup1,
                index = index,
                item = it,
                onDetailsClick = {
                    MultiEventScreenIntents.bunkerRequests = it
                    MultiEventScreenIntents.appName = appName
                    navController.navigate(Route.SeeDetails.route)
                },
            )
        }
        grouped2.toList().forEachIndexed { index, it ->
            BunkerPermissionCard(
                context = context,
                acceptEventsGroup = acceptEventsGroup2,
                index = index,
                item = it,
                onDetailsClick = {
                    MultiEventScreenIntents.bunkerRequests = it
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
                    bunkerRequests.forEach { bunkerRequest ->
                        bunkerRequest.rememberType.value = rememberType
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
                        reconnectToRelays()
                        var closeApp = true

                        BunkerRequestUtils.clearRequests()
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
                            val database = Amber.instance.getDatabase(thisAccount.npub)
                            val historyDatabase = Amber.instance.getHistoryDatabase(thisAccount.npub)
                            val savedApplication = database.dao().getByKey(localKey)

                            val secret = if (request.request is BunkerRequestConnect) {
                                request.request.secret ?: ""
                            } else {
                                ""
                            }

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
                                        secret,
                                        secret.isNotBlank(),
                                        thisAccount.signPolicy,
                                        request.closeApplication,
                                        0L,
                                        lastUsed = TimeUtils.now(),
                                    ),
                                    permissions = mutableListOf(),
                                )

                            if (!application.application.closeApplication) {
                                closeApp = false
                            }

                            if (request.request is BunkerRequestSign) {
                                val localEvent = request.signedEvent!!

                                if (request.rememberType.value != RememberType.NEVER && request.checked.value) {
                                    AmberUtils.acceptOrRejectPermission(
                                        application = application,
                                        key = localKey,
                                        signerType = SignerType.SIGN_EVENT,
                                        kind = localEvent.kind,
                                        value = true,
                                        rememberType = request.rememberType.value,
                                        account = thisAccount,
                                    )
                                }

                                database.dao().insertApplicationWithPermissions(application)

                                historyDatabase.dao().addHistory(
                                    entity = HistoryEntity(
                                        id = 0,
                                        pkKey = localKey,
                                        type = SignerType.SIGN_EVENT.toString(),
                                        kind = localEvent.kind,
                                        time = TimeUtils.now(),
                                        accepted = request.checked.value,
                                    ),
                                    thisAccount.npub,
                                )

                                val localBunkerRequest = request.copy()
                                BunkerRequestUtils.remove(request.request.id)

                                if (request.checked.value) {
                                    BunkerRequestUtils.sendBunkerResponse(
                                        context,
                                        thisAccount,
                                        request,
                                        BunkerResponse(request.request.id, localEvent.toJson(), null),
                                        application.application.relays,
                                        onLoading = {},
                                        onDone = {
                                            if (!it) {
                                                BunkerRequestUtils.addRequest(localBunkerRequest)
                                            }
                                        },
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
                            } else if (request.request.method == "sign_message") {
                                if (request.rememberType.value != RememberType.NEVER && request.checked.value) {
                                    AmberUtils.acceptOrRejectPermission(
                                        application,
                                        localKey,
                                        SignerType.SIGN_MESSAGE,
                                        null,
                                        true,
                                        request.rememberType.value,
                                        thisAccount,
                                    )
                                }

                                database.dao().insertApplicationWithPermissions(application)
                                historyDatabase.dao().addHistory(
                                    HistoryEntity(
                                        0,
                                        localKey,
                                        SignerType.SIGN_MESSAGE.toString(),
                                        null,
                                        TimeUtils.now(),
                                        request.checked.value,
                                    ),
                                    thisAccount.npub,
                                )

                                val signedMessage = signString(request.request.params.first(), thisAccount.signer.keyPair.privKey!!).toHexKey()
                                val localBunkerRequest = request.copy()
                                BunkerRequestUtils.remove(localBunkerRequest.request.id)

                                if (request.checked.value) {
                                    BunkerRequestUtils.sendBunkerResponse(
                                        context,
                                        thisAccount,
                                        request,
                                        BunkerResponse(request.request.id, signedMessage, null),
                                        application.application.relays,
                                        onLoading = {},
                                        onDone = {
                                            if (!it) {
                                                BunkerRequestUtils.addRequest(localBunkerRequest)
                                            }
                                        },
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
                                    database.dao().insertApplicationWithPermissions(application)

                                    historyDatabase.dao().addHistory(
                                        HistoryEntity(
                                            0,
                                            localKey,
                                            SignerType.CONNECT.toString(),
                                            null,
                                            TimeUtils.now(),
                                            request.checked.value,
                                        ),
                                        thisAccount.npub,
                                    )

                                    val localBunkerRequest = request.copy()
                                    BunkerRequestUtils.remove(request.request.id)
                                    if (request.checked.value) {
                                        BunkerRequestUtils.sendBunkerResponse(
                                            context,
                                            thisAccount,
                                            request,
                                            BunkerResponse(request.request.id, "", null),
                                            application.application.relays,
                                            onLoading = {},
                                            onDone = {
                                                if (!it) {
                                                    BunkerRequestUtils.addRequest(localBunkerRequest)
                                                }
                                            },
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
                                val type = BunkerRequestUtils.getTypeFromBunker(request.request)
                                if (request.rememberType.value != RememberType.NEVER && request.checked.value) {
                                    AmberUtils.acceptOrRejectPermission(
                                        application,
                                        localKey,
                                        type,
                                        null,
                                        true,
                                        request.rememberType.value,
                                        thisAccount,
                                    )
                                }

                                database.dao().insertApplicationWithPermissions(application)

                                historyDatabase.dao().addHistory(
                                    HistoryEntity(
                                        0,
                                        localKey,
                                        type.toString(),
                                        null,
                                        TimeUtils.now(),
                                        request.checked.value,
                                    ),
                                    thisAccount.npub,
                                )

                                val signature = request.encryptDecryptResponse ?: continue
                                val localBunkerRequest = request.copy()
                                BunkerRequestUtils.remove(request.request.id)
                                if (request.checked.value) {
                                    BunkerRequestUtils.sendBunkerResponse(
                                        context,
                                        thisAccount,
                                        request,
                                        BunkerResponse(request.request.id, signature, null),
                                        application.application.relays,
                                        onLoading = {},
                                        onDone = {
                                            if (!it) {
                                                BunkerRequestUtils.addRequest(localBunkerRequest)
                                            }
                                        },
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
                        val database = Amber.instance.getDatabase(thisAccount.npub)
                        val secret = if (request.request is BunkerRequestConnect) {
                            request.request.secret ?: ""
                        } else {
                            ""
                        }
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
                                    secret,
                                    secret.isNotBlank(),
                                    thisAccount.signPolicy,
                                    request.closeApplication,
                                    0L,
                                    lastUsed = TimeUtils.now(),
                                ),
                                permissions = mutableListOf(),
                            )

                        if (request.rememberType.value != RememberType.NEVER && request.checked.value) {
                            AmberUtils.acceptOrRejectPermission(
                                application,
                                localKey,
                                BunkerRequestUtils.getTypeFromBunker(request.request),
                                null,
                                false,
                                request.rememberType.value,
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

private suspend fun reconnectToRelays() {
    Amber.instance.checkForNewRelaysAndUpdateAllFilters()
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
fun BunkerPermissionCard(
    context: Context,
    acceptEventsGroup: List<MutableState<Boolean>>,
    index: Int,
    item: Pair<Any?, List<AmberBunkerRequest>>,
    onDetailsClick: (List<AmberBunkerRequest>) -> Unit,
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

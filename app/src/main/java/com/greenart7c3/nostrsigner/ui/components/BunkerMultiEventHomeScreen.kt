package com.greenart7c3.nostrsigner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.PrivateZapEncryptedDataKind
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.ApplicationNameCache
import com.greenart7c3.nostrsigner.service.BunkerRequestUtils
import com.greenart7c3.nostrsigner.service.EventNotificationConsumer
import com.greenart7c3.nostrsigner.service.MultiEventScreenIntents
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.orange
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.collections.forEach
import kotlin.collections.set
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    val groupedEventEncryptedData = bunkerRequests.filter { it.encryptedData is EventEncryptedDataKind }.groupBy { BunkerRequestUtils.getTypeFromBunker(it.request) }
    val groupedTextEncryptedData = bunkerRequests.filter { it.encryptedData is ClearTextEncryptedDataKind }.groupBy { BunkerRequestUtils.getTypeFromBunker(it.request) }
    val groupedTagArrayEncryptedData = bunkerRequests.filter { it.encryptedData is TagArrayEncryptedDataKind }.groupBy { BunkerRequestUtils.getTypeFromBunker(it.request) }
    val groupedPrivateZapEncryptedDataKind = bunkerRequests.filter { it.encryptedData is PrivateZapEncryptedDataKind }.groupBy { BunkerRequestUtils.getTypeFromBunker(it.request) }
    val groupedOthers = bunkerRequests.filter { it.encryptedData == null && it.request !is BunkerRequestSign }.groupBy { BunkerRequestUtils.getTypeFromBunker(it.request) }
    val groupedEvents = bunkerRequests.filter { it.request is BunkerRequestSign }.groupBy { (it.request as BunkerRequestSign).event.kind }
    val acceptedGroupedEventEncryptedData = groupedEventEncryptedData.map {
        remember {
            mutableStateOf(true)
        }
    }
    val acceptedGroupedTextEncryptedData = groupedTextEncryptedData.map {
        remember {
            mutableStateOf(true)
        }
    }
    val acceptedGroupedTagArrayEncryptedData = groupedTagArrayEncryptedData.map {
        remember {
            mutableStateOf(true)
        }
    }

    val acceptedGroupedPrivateZapEncryptedDataKind = groupedPrivateZapEncryptedDataKind.map {
        remember {
            mutableStateOf(true)
        }
    }

    val acceptEventsGroupOthers = groupedOthers.map {
        remember {
            mutableStateOf(true)
        }
    }

    val acceptEventsGroup2 = groupedEvents.map {
        remember {
            mutableStateOf(true)
        }
    }
    var localAccount by remember { mutableStateOf("") }
    val key = bunkerRequests.first().localKey
    var rememberType by remember { mutableStateOf(RememberType.NEVER) }
    var appName by remember { mutableStateOf(ApplicationNameCache.names["$localAccount-$key"] ?: key.toShortenHex()) }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            localAccount = LocalPreferences.loadFromEncryptedStorage(
                context,
                bunkerRequests.first().currentAccount,
            )?.npub?.toShortenHex() ?: ""

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
        Text(
            stringResource(R.string.is_requiring_some_permissions_please_review_them, appName),
            Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            groupedEventEncryptedData.toList().forEachIndexed { index, it ->
                BunkerPermissionCard(
                    context = context,
                    acceptEventsGroup = acceptedGroupedEventEncryptedData,
                    index = index,
                    item = it,
                    onDetailsClick = {
                        MultiEventScreenIntents.bunkerRequests = it
                        MultiEventScreenIntents.appName = appName
                        navController.navigate(Route.SeeDetails.route)
                    },
                )
            }
            groupedTextEncryptedData.toList().forEachIndexed { index, it ->
                BunkerPermissionCard(
                    context = context,
                    acceptEventsGroup = acceptedGroupedTextEncryptedData,
                    index = index,
                    item = it,
                    onDetailsClick = {
                        MultiEventScreenIntents.bunkerRequests = it
                        MultiEventScreenIntents.appName = appName
                        navController.navigate(Route.SeeDetails.route)
                    },
                )
            }
            groupedTagArrayEncryptedData.toList().forEachIndexed { index, it ->
                BunkerPermissionCard(
                    context = context,
                    acceptEventsGroup = acceptedGroupedTagArrayEncryptedData,
                    index = index,
                    item = it,
                    onDetailsClick = {
                        MultiEventScreenIntents.bunkerRequests = it
                        MultiEventScreenIntents.appName = appName
                        navController.navigate(Route.SeeDetails.route)
                    },
                )
            }
            groupedPrivateZapEncryptedDataKind.toList().forEachIndexed { index, it ->
                BunkerPermissionCard(
                    context = context,
                    acceptEventsGroup = acceptedGroupedPrivateZapEncryptedDataKind,
                    index = index,
                    item = it,
                    onDetailsClick = {
                        MultiEventScreenIntents.bunkerRequests = it
                        MultiEventScreenIntents.appName = appName
                        navController.navigate(Route.SeeDetails.route)
                    },
                )
            }
            groupedOthers.toList().forEachIndexed { index, it ->
                BunkerPermissionCard(
                    context = context,
                    acceptEventsGroup = acceptEventsGroupOthers,
                    index = index,
                    item = it,
                    onDetailsClick = {
                        MultiEventScreenIntents.bunkerRequests = it
                        MultiEventScreenIntents.appName = appName
                        navController.navigate(Route.SeeDetails.route)
                    },
                )
            }
            groupedEvents.toList().forEachIndexed { index, it ->
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
        }

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
                text = stringResource(R.string.discard_all),
            )

            AmberButton(
                Modifier.weight(1f),
                text = stringResource(R.string.approve_all),
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

                                    val signedMessage = thisAccount.signString(request.request.params.first())
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

                                    val signature = request.encryptedData?.result ?: continue
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
                        } finally {
                            onLoading(false)
                        }
                    }
                },
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

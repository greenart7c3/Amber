package com.greenart7c3.nostrsigner.ui.components

import android.app.Activity
import android.content.Intent
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
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.navigation.Route
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
    navController: NavController,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val groupedEventEncryptedData = intents.filter { it.encryptedData is EventEncryptedDataKind }.groupBy { it.type }
    val groupedTextEncryptedData = intents.filter { it.encryptedData is ClearTextEncryptedDataKind }.groupBy { it.type }
    val groupedTagArrayEncryptedData = intents.filter { it.encryptedData is TagArrayEncryptedDataKind }.groupBy { it.type }
    val groupedPrivateZapEncryptedDataKind = intents.filter { it.encryptedData is PrivateZapEncryptedDataKind }.groupBy { it.type }
    val groupedOthers = intents.filter { it.encryptedData == null && it.type != SignerType.SIGN_EVENT }.groupBy { it.type }
    val groupedEvents = intents.filter { it.type == SignerType.SIGN_EVENT }.groupBy { it.event?.kind }
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
                .padding(bottom = 20.dp),
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            groupedEventEncryptedData.toList().forEachIndexed { index, it ->
                PermissionCard(
                    context = context,
                    acceptEventsGroup = acceptedGroupedEventEncryptedData,
                    index = index,
                    item = it,
                    onDetailsClick = {
                        MultiEventScreenIntents.intents = it
                        MultiEventScreenIntents.appName = appName
                        navController.navigate(Route.SeeDetails.route)
                    },
                )
            }
            groupedTextEncryptedData.toList().forEachIndexed { index, it ->
                PermissionCard(
                    context = context,
                    acceptEventsGroup = acceptedGroupedTextEncryptedData,
                    index = index,
                    item = it,
                    onDetailsClick = {
                        MultiEventScreenIntents.intents = it
                        MultiEventScreenIntents.appName = appName
                        navController.navigate(Route.SeeDetails.route)
                    },
                )
            }
            groupedTagArrayEncryptedData.toList().forEachIndexed { index, it ->
                PermissionCard(
                    context = context,
                    acceptEventsGroup = acceptedGroupedTagArrayEncryptedData,
                    index = index,
                    item = it,
                    onDetailsClick = {
                        MultiEventScreenIntents.intents = it
                        MultiEventScreenIntents.appName = appName
                        navController.navigate(Route.SeeDetails.route)
                    },
                )
            }
            groupedPrivateZapEncryptedDataKind.toList().forEachIndexed { index, it ->
                PermissionCard(
                    context = context,
                    acceptEventsGroup = acceptedGroupedPrivateZapEncryptedDataKind,
                    index = index,
                    item = it,
                    onDetailsClick = {
                        MultiEventScreenIntents.intents = it
                        MultiEventScreenIntents.appName = appName
                        navController.navigate(Route.SeeDetails.route)
                    },
                )
            }
            groupedOthers.toList().forEachIndexed { index, it ->
                PermissionCard(
                    context = context,
                    acceptEventsGroup = acceptEventsGroupOthers,
                    index = index,
                    item = it,
                    onDetailsClick = {
                        MultiEventScreenIntents.intents = it
                        MultiEventScreenIntents.appName = appName
                        navController.navigate(Route.SeeDetails.route)
                    },
                )
            }
            groupedEvents.toList().forEachIndexed { index, it ->
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
        }

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

                                    val signedMessage = thisAccount.signString(intentData.data)
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

                                    val signature = intentData.encryptedData?.result ?: continue
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

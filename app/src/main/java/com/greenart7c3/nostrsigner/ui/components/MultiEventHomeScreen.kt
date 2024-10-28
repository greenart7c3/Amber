package com.greenart7c3.nostrsigner.ui.components

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.greenart7c3.nostrsigner.service.getAppCompatActivity
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.NotificationType
import com.greenart7c3.nostrsigner.ui.Result
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SelectAllButton(
    checked: Boolean,
    onSelected: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clickable {
                onSelected()
            },
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.select_deselect_all),
        )
        Switch(
            checked = checked,
            onCheckedChange = {
                onSelected()
            },
        )
    }
}

@Composable
fun MultiEventHomeScreen(
    intents: List<IntentData>,
    packageName: String?,
    accountParam: Account,
    onLoading: (Boolean) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val grouped = intents.groupBy { it.type }.filter { it.key != SignerType.SIGN_EVENT }
    val grouped2 = intents.filter { it.type == SignerType.SIGN_EVENT }.groupBy { it.event?.kind }
    val rememberMyChoices = grouped.map {
        remember {
            mutableStateOf(false)
        }
    }
    val rememberMyChoices2 = grouped2.map {
        remember {
            mutableStateOf(false)
        }
    }
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
    val showItemsGroup1 = grouped.map {
        remember {
            mutableStateOf(false)
        }
    }
    val showItemsGroup2 = grouped2.map {
        remember {
            mutableStateOf(false)
        }
    }

    Column(
        Modifier.fillMaxSize(),
    ) {
        var selectAll by remember {
            mutableStateOf(true)
        }

        SelectAllButton(
            checked = selectAll,
        ) {
            selectAll = !selectAll
            acceptEventsGroup1.forEach { it.value = selectAll }
            acceptEventsGroup2.forEach { it.value = selectAll }
            intents.forEach {
                it.checked.value = selectAll
            }
        }
        LazyColumn(
            Modifier.fillMaxHeight(0.9f),
        ) {
            grouped.toList().forEachIndexed { index, it ->
                item {
                    Card(
                        Modifier
                            .padding(4.dp),
                    ) {
                        Column(
                            Modifier
                                .padding(4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .clickable {
                                        acceptEventsGroup1[index].value = !acceptEventsGroup1[index].value
                                    },
                            ) {
                                Text(
                                    text = "${it.second.size} - ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                )

                                val permission = Permission(it.first.toString().toLowerCase(Locale.current), null)
                                val message = if (it.first == SignerType.CONNECT) {
                                    stringResource(R.string.connect)
                                } else {
                                    permission.toLocalizedString(context)
                                }
                                Text(
                                    modifier = Modifier.weight(1f),
                                    text = message,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                )

                                Switch(
                                    checked = acceptEventsGroup1[index].value,
                                    onCheckedChange = { _ ->
                                        acceptEventsGroup1[index].value = !acceptEventsGroup1[index].value
                                        it.second.forEach { item ->
                                            item.checked.value = acceptEventsGroup1[index].value
                                        }
                                    },
                                )
                            }

                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp),
                            ) {
                                RememberMyChoice(
                                    shouldRunAcceptOrReject = null,
                                    rememberMyChoices[index].value,
                                    null,
                                    true,
                                    { },
                                    { },
                                ) {
                                    rememberMyChoices[index].value = !rememberMyChoices[index].value
                                    it.second.forEach { item ->
                                        item.rememberMyChoice.value = rememberMyChoices[index].value
                                    }
                                }
                            }

                            AmberButton(
                                onClick = {
                                    showItemsGroup1[index].value = !showItemsGroup1[index].value
                                },
                                content = {
                                    Text(if (showItemsGroup1[index].value) context.getString(R.string.hide_details) else context.getString(R.string.show_details))
                                },
                            )
                        }
                    }
                }

                if (showItemsGroup1[index].value) {
                    it.second.forEach {
                        item {
                            ListItem(it, packageName)
                        }
                    }
                }
            }
            grouped2.toList().forEachIndexed { index, it ->
                item {
                    Card(
                        Modifier
                            .padding(4.dp),
                    ) {
                        Column(
                            Modifier
                                .padding(4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .clickable {
                                        acceptEventsGroup2[index].value = !acceptEventsGroup2[index].value
                                    },
                            ) {
                                Text(
                                    text = "${it.second.size} - ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                )

                                Text(
                                    modifier = Modifier.weight(1f),
                                    text = Permission("sign_event", it.first).toLocalizedString(context),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                )

                                Switch(
                                    checked = acceptEventsGroup2[index].value,
                                    onCheckedChange = { _ ->
                                        acceptEventsGroup2[index].value = !acceptEventsGroup2[index].value
                                        it.second.forEach { item ->
                                            item.checked.value = acceptEventsGroup2[index].value
                                        }
                                    },
                                )
                            }

                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp),
                            ) {
                                RememberMyChoice(
                                    shouldRunAcceptOrReject = null,
                                    rememberMyChoices2[index].value,
                                    null,
                                    true,
                                    { },
                                    { },
                                ) {
                                    rememberMyChoices2[index].value = !rememberMyChoices2[index].value
                                    it.second.forEach { item ->
                                        item.rememberMyChoice.value = rememberMyChoices2[index].value
                                    }
                                }
                            }

                            AmberButton(
                                onClick = {
                                    showItemsGroup2[index].value = !showItemsGroup2[index].value
                                },
                                content = {
                                    Text(if (showItemsGroup2[index].value) context.getString(R.string.hide_details) else context.getString(R.string.show_details))
                                },
                            )
                        }
                    }
                }
                if (showItemsGroup2[index].value) {
                    it.second.forEach {
                        item {
                            ListItem(it, packageName)
                        }
                    }
                }
            }
        }

        AmberButton(
            content = {
                Text(stringResource(R.string.confirm))
            },
            onClick = {
                onLoading(true)
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val activity = context.getAppCompatActivity()
                        val results = mutableListOf<Result>()
                        reconnectToRelays(intents)

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

                            val database = NostrSigner.getInstance().getDatabase(localAccount.keyPair.pubKey.toNpub())

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
                                        localAccount.keyPair.pubKey.toHexKey(),
                                        true,
                                        intentData.bunkerRequest?.secret ?: "",
                                        intentData.bunkerRequest?.secret != null,
                                        localAccount.signPolicy,
                                    ),
                                    permissions = mutableListOf(),
                                )

                            if (intentData.type == SignerType.SIGN_EVENT) {
                                val localEvent = intentData.event!!

                                if (intentData.rememberMyChoice.value) {
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
                                        true,
                                    ),
                                )

                                if (intentData.bunkerRequest != null) {
                                    IntentUtils.sendBunkerResponse(
                                        context,
                                        localAccount,
                                        intentData.bunkerRequest,
                                        BunkerResponse(intentData.bunkerRequest.id, localEvent.toJson(), null),
                                        application.application.relays,
                                        onLoading = {},
                                        onDone = {},
                                    )
                                } else {
                                    results.add(
                                        Result(
                                            null,
                                            signature = if (localEvent is LnZapRequestEvent &&
                                                localEvent.tags.any {
                                                        tag ->
                                                    tag.any { t -> t == "anon" }
                                                }
                                            ) {
                                                localEvent.toJson()
                                            } else {
                                                localEvent.sig
                                            },
                                            result = if (localEvent is LnZapRequestEvent &&
                                                localEvent.tags.any {
                                                        tag ->
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
                                if (intentData.rememberMyChoice.value) {
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
                                        true,
                                    ),
                                )

                                val signedMessage = CryptoUtils.signString(intentData.data, localAccount.keyPair.privKey!!).toHexKey()

                                if (intentData.bunkerRequest != null) {
                                    IntentUtils.sendBunkerResponse(
                                        context,
                                        localAccount,
                                        intentData.bunkerRequest,
                                        BunkerResponse(intentData.bunkerRequest.id, signedMessage, null),
                                        application.application.relays,
                                        onLoading = {},
                                        onDone = {},
                                    )
                                } else {
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
                                if (intentData.rememberMyChoice.value) {
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
                                        true,
                                    ),
                                )

                                val signature = intentData.encryptedData ?: continue

                                if (intentData.bunkerRequest != null) {
                                    IntentUtils.sendBunkerResponse(
                                        context,
                                        localAccount,
                                        intentData.bunkerRequest,
                                        BunkerResponse(intentData.bunkerRequest.id, signature, null),
                                        application.application.relays,
                                        onLoading = {},
                                        onDone = {},
                                    )
                                } else {
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
                            sendResultIntent(results, activity)
                        }
                        if (intents.any { it.bunkerRequest != null }) {
                            EventNotificationConsumer(context).notificationManager().cancelAll()
                            finishActivity(activity)
                        } else {
                            finishActivity(activity)
                        }
                    } finally {
                        onLoading(false)
                    }
                }
            },
        )
    }
}

private fun finishActivity(activity: AppCompatActivity?) {
    activity?.intent = null
    activity?.finish()
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

    NostrSigner.getInstance().checkForNewRelays(NostrSigner.getInstance().settings.notificationType != NotificationType.DIRECT)
}

@Composable
fun ListItem(
    intentData: IntentData,
    packageName: String?,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val key = if (intentData.bunkerRequest != null) {
        intentData.bunkerRequest.localKey
    } else {
        "$packageName"
    }

    var localAccount by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            localAccount = LocalPreferences.loadFromEncryptedStorage(
                context,
                intentData.currentAccount,
            )?.keyPair?.pubKey?.toNpub()?.toShortenHex() ?: ""
        }
    }

    val appName = ApplicationNameCache.names["$localAccount-$key"] ?: key.toShortenHex()

    Card(
        Modifier
            .padding(4.dp)
            .clickable {
                isExpanded = !isExpanded
            },
    ) {
        val name = LocalPreferences.getAccountName(context, intentData.currentAccount)
        Row(
            Modifier
                .fillMaxWidth(),
            Arrangement.Center,
            Alignment.CenterVertically,
        ) {
            Text(
                name.ifBlank { intentData.currentAccount.toShortenHex() },
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
        ) {
            Icon(
                Icons.Default.run {
                    if (isExpanded) {
                        KeyboardArrowDown
                    } else {
                        KeyboardArrowUp
                    }
                },
                contentDescription = "",
                tint = Color.LightGray,
            )
            val text =
                if (intentData.type == SignerType.SIGN_EVENT) {
                    val event = intentData.event!!
                    val permission = Permission("sign_event", event.kind)
                    stringResource(R.string.wants_you_to_sign_a, permission.toLocalizedString(context))
                } else {
                    val permission = Permission(intentData.type.toString().toLowerCase(Locale.current), null)
                    stringResource(R.string.wants_you_to, permission.toLocalizedString(context))
                }
            Text(
                modifier = Modifier.weight(1f),
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(appName)
                    }
                    append(" $text")
                },
                fontSize = 18.sp,
            )

            Switch(
                checked = intentData.checked.value,
                onCheckedChange = { _ ->
                    intentData.checked.value = !intentData.checked.value
                },
            )
        }

        if (isExpanded) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(10.dp),
            ) {
                Text(
                    "Event content",
                    fontWeight = FontWeight.Bold,
                )
                val content =
                    if (intentData.type == SignerType.SIGN_EVENT) {
                        val event = intentData.event!!
                        if (event.kind == 22242) AmberEvent.relay(event) else event.content
                    } else {
                        intentData.data
                    }

                Text(
                    content.take(100),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

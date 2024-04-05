package com.greenart7c3.nostrsigner.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.BunkerRequest
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.getAppCompatActivity
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.BunkerResponse
import com.greenart7c3.nostrsigner.ui.sendResult
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

fun sendBunkerError(account: Account, bunkerRequest: BunkerRequest, context: Context) {
    val relays = bunkerRequest.relays.ifEmpty { listOf("wss://relay.nsec.app") }
    IntentUtils.sendBunkerResponse(
        account,
        bunkerRequest.localKey,
        BunkerResponse(bunkerRequest.id, "", "user rejected"),
        relays
    ) {
        context.getAppCompatActivity()?.intent = null
        context.getAppCompatActivity()?.finish()
    }
}

fun acceptOrRejectPermission(key: String, intentData: IntentData, kind: Int?, value: Boolean, appName: String, account: Account) {
    runBlocking {
        withContext(Dispatchers.IO) {
            val application = LocalPreferences.appDatabase!!
                .applicationDao()
                .getByKey(key) ?: ApplicationWithPermissions(
                application = ApplicationEntity(
                    key,
                    appName,
                    listOf(),
                    "",
                    "",
                    "",
                    account.keyPair.pubKey.toHexKey(),
                    true,
                    intentData.bunkerRequest?.secret ?: ""
                ),
                permissions = mutableListOf()
            )

            application.permissions.add(
                ApplicationPermissionsEntity(
                    null,
                    key,
                    intentData.type.toString(),
                    kind,
                    value
                )
            )

            LocalPreferences.appDatabase!!
                .applicationDao()
                .insertApplicationWithPermissions(application)
        }
    }
}

@Composable
fun SingleEventHomeScreen(
    packageName: String?,
    applicationName: String?,
    intentData: IntentData,
    account: Account
) {
    var applicationEntity by remember {
        mutableStateOf<ApplicationEntity?>(null)
    }
    val key = if (intentData.bunkerRequest != null) {
        intentData.bunkerRequest.localKey
    } else {
        "$packageName"
    }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            applicationEntity = if (intentData.bunkerRequest?.secret != null && intentData.bunkerRequest.secret.isNotBlank()) {
                LocalPreferences.appDatabase!!.applicationDao().getBySecret(intentData.bunkerRequest.secret)?.application
            } else {
                LocalPreferences.appDatabase!!.applicationDao().getByKey(key)?.application
            }
        }
    }

    val appName = applicationEntity?.name?.ifBlank { key.toShortenHex() } ?: packageName ?: intentData.name
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    when (intentData.type) {
        SignerType.GET_PUBLIC_KEY, SignerType.CONNECT -> {
            val permission = runBlocking {
                withContext(Dispatchers.IO) {
                    LocalPreferences.appDatabase!!
                        .applicationDao()
                        .getPermission(
                            key,
                            intentData.type.toString()
                        )
                }
            }
            val remember = remember {
                mutableStateOf(permission?.acceptable == true)
            }
            LoginWithPubKey(
                appName,
                applicationName,
                intentData.permissions,
                { permissions ->
                    val sig = if (intentData.type == SignerType.CONNECT) {
                        "ack"
                    } else {
                        account.keyPair.pubKey.toNpub()
                    }
                    coroutineScope.launch {
                        sendResult(
                            context,
                            packageName,
                            account,
                            key,
                            remember.value,
                            clipboardManager,
                            sig,
                            sig,
                            intentData,
                            null,
                            permissions = permissions,
                            appName = applicationName ?: appName
                        )
                    }
                    return@LoginWithPubKey
                },
                {
                    if (remember.value) {
                        acceptOrRejectPermission(key, intentData, null, false, applicationName ?: appName, account)
                    }

                    if (intentData.bunkerRequest != null) {
                        sendBunkerError(account, intentData.bunkerRequest, context)
                    } else {
                        context.getAppCompatActivity()?.intent = null
                        context.getAppCompatActivity()?.finish()
                    }
                }
            )
        }

        SignerType.NIP04_DECRYPT, SignerType.NIP04_ENCRYPT, SignerType.NIP44_ENCRYPT, SignerType.NIP44_DECRYPT, SignerType.DECRYPT_ZAP_EVENT -> {
            val permission = runBlocking {
                withContext(Dispatchers.IO) {
                    LocalPreferences.appDatabase!!
                        .applicationDao()
                        .getPermission(
                            key,
                            intentData.type.toString()
                        )
                }
            }
            val remember = remember {
                mutableStateOf(permission?.acceptable == true)
            }

            val shouldRunOnAccept = permission?.acceptable == true
            val localPackageName = if (intentData.bunkerRequest != null) {
                intentData.bunkerRequest.localKey
            } else {
                packageName
            }
            EncryptDecryptData(
                intentData.data,
                shouldRunOnAccept,
                remember,
                localPackageName,
                applicationName,
                appName,
                intentData.type,
                {
                    if (intentData.type == SignerType.NIP04_ENCRYPT && intentData.data.contains(
                            "?iv=",
                            ignoreCase = true
                        )
                    ) {
                        coroutineScope.launch {
                            Toast.makeText(
                                context,
                                context.getString(R.string.message_already_encrypted),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@EncryptDecryptData
                    } else {
                        try {
                            coroutineScope.launch(Dispatchers.IO) {
                                val sig = try {
                                    AmberUtils.encryptOrDecryptData(
                                        intentData.data,
                                        intentData.type,
                                        account,
                                        intentData.pubKey
                                    )
                                        ?: "Could not decrypt the message"
                                } catch (e: Exception) {
                                    "Could not decrypt the message"
                                }

                                if (intentData.type == SignerType.NIP04_ENCRYPT && sig == "Could not decrypt the message") {
                                    coroutineScope.launch {
                                        Toast.makeText(
                                            context,
                                            "Error encrypting content",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    return@launch
                                } else {
                                    val result =
                                        if (sig == "Could not decrypt the message" && (intentData.type == SignerType.DECRYPT_ZAP_EVENT)) {
                                            ""
                                        } else {
                                            sig
                                        }

                                    sendResult(
                                        context,
                                        packageName,
                                        account,
                                        key,
                                        remember.value,
                                        clipboardManager,
                                        result,
                                        result,
                                        intentData,
                                        null
                                    )
                                }
                            }

                            return@EncryptDecryptData
                        } catch (e: Exception) {
                            val message = if (intentData.type.toString().contains("ENCRYPT", true)) {
                                context.getString(R.string.encrypt)
                            } else {
                                context.getString(R.string.decrypt)
                            }

                            coroutineScope.launch {
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.error_to_data,
                                        message
                                    ),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return@EncryptDecryptData
                        }
                    }
                },
                {
                    if (remember.value) {
                        acceptOrRejectPermission(key, intentData, null, false, applicationName ?: appName, account)
                    }

                    if (intentData.bunkerRequest != null) {
                        sendBunkerError(account, intentData.bunkerRequest, context)
                    } else {
                        context.getAppCompatActivity()?.intent = null
                        context.getAppCompatActivity()?.finish()
                    }
                },
                {
                    try {
                        AmberUtils.encryptOrDecryptData(
                            intentData.data,
                            intentData.type,
                            account,
                            intentData.pubKey
                        )
                            ?: "Could not decrypt the message"
                    } catch (e: Exception) {
                        "Could not decrypt the message"
                    }
                }
            )
        }

        else -> {
            val event = IntentUtils.getIntent(intentData.data, account.keyPair)
            val permission = runBlocking {
                withContext(Dispatchers.IO) {
                    LocalPreferences.appDatabase!!
                        .applicationDao()
                        .getPermission(
                            key,
                            intentData.type.toString(),
                            event.kind
                        )
                }
            }
            val remember = remember {
                mutableStateOf(permission?.acceptable == true)
            }

            val shouldRunOnAccept = permission?.acceptable == true
            val localPackageName = if (intentData.bunkerRequest != null) {
                intentData.bunkerRequest.localKey
            } else {
                packageName
            }
            EventData(
                shouldRunOnAccept,
                remember,
                localPackageName,
                appName,
                applicationName,
                event,
                intentData.data,
                intentData.type,
                {
                    if (event.pubKey != account.keyPair.pubKey.toHexKey()) {
                        coroutineScope.launch {
                            Toast.makeText(
                                context,
                                context.getString(R.string.event_pubkey_is_not_equal_to_current_logged_in_user),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@EventData
                    }

                    val localEvent = try {
                        Event.fromJson(intentData.data)
                    } catch (e: Exception) {
                        Event.fromJson(event.toJson())
                    }

                    account.signer.sign<Event>(
                        localEvent.createdAt,
                        localEvent.kind,
                        localEvent.tags,
                        localEvent.content
                    ) { signedEvent ->
                        coroutineScope.launch {
                            sendResult(
                                context,
                                packageName,
                                account,
                                key,
                                remember.value,
                                clipboardManager,
                                signedEvent.toJson(),
                                if (localEvent is LnZapRequestEvent && localEvent.tags.any { tag -> tag.any { t -> t == "anon" } }) signedEvent.toJson() else signedEvent.sig,
                                intentData,
                                localEvent.kind
                            )
                        }
                    }
                },
                {
                    if (remember.value) {
                        acceptOrRejectPermission(key, intentData, event.kind, false, applicationName ?: appName, account)
                    }

                    if (intentData.bunkerRequest != null) {
                        sendBunkerError(account, intentData.bunkerRequest, context)
                    } else {
                        context.getAppCompatActivity()?.intent = null
                        context.getAppCompatActivity()?.finish()
                    }
                }
            )
        }
    }
}

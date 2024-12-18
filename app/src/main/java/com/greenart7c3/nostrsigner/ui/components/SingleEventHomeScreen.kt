package com.greenart7c3.nostrsigner.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.models.kindToNip
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.getAppCompatActivity
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.IntentResultType
import com.greenart7c3.nostrsigner.ui.sendResult
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.bechToBytes
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SingleEventHomeScreen(
    paddingValues: PaddingValues,
    packageName: String?,
    applicationName: String?,
    intentData: IntentData,
    account: Account,
    database: AppDatabase,
    onRemoveIntentData: (IntentData, IntentResultType) -> Unit,
    onLoading: (Boolean) -> Unit,
) {
    var applicationEntity by remember {
        mutableStateOf<ApplicationWithPermissions?>(null)
    }
    val key =
        if (intentData.bunkerRequest != null) {
            intentData.bunkerRequest.localKey
        } else {
            "$packageName"
        }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            applicationEntity =
                if (intentData.bunkerRequest?.secret != null && intentData.bunkerRequest.secret.isNotBlank()) {
                    database.applicationDao().getBySecret(intentData.bunkerRequest.secret)
                } else {
                    database.applicationDao().getByKey(key)
                }
        }
    }

    var appName = applicationEntity?.application?.name?.ifBlank { key.toShortenHex() } ?: packageName ?: intentData.name
    appName = appName.ifBlank { key.toShortenHex() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    when (intentData.type) {
        SignerType.GET_PUBLIC_KEY, SignerType.CONNECT -> {
            val permission =
                applicationEntity?.permissions?.firstOrNull {
                    it.pkKey == key && it.type == intentData.type.toString()
                }
            val remember =
                remember {
                    mutableStateOf(permission?.acceptable != null)
                }

            LoginWithPubKey(
                paddingValues,
                remember,
                intentData.bunkerRequest != null && intentData.type == SignerType.GET_PUBLIC_KEY,
                account,
                packageName,
                appName,
                applicationName,
                intentData.permissions,
                { permissions, signPolicy ->
                    val sig =
                        if (intentData.type == SignerType.CONNECT) {
                            intentData.bunkerRequest!!.nostrConnectSecret.ifBlank { "ack" }
                        } else if (intentData.bunkerRequest != null) {
                            account.signer.keyPair.pubKey.toHexKey()
                        } else if (packageName == null) {
                            account.signer.keyPair.pubKey.toHexKey()
                        } else {
                            account.signer.keyPair.pubKey.toNpub()
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
                            appName = applicationName ?: appName,
                            database = database,
                            onLoading = onLoading,
                            signPolicy = signPolicy,
                            onRemoveIntentData = onRemoveIntentData,
                        )
                    }
                    return@LoginWithPubKey
                },
                {
                    if (intentData.bunkerRequest != null) {
                        coroutineScope.launch(Dispatchers.IO) {
                            val defaultRelays = NostrSigner.getInstance().settings.defaultRelays
                            val savedApplication = database.applicationDao().getByKey(key)
                            val relays = savedApplication?.application?.relays?.ifEmpty { defaultRelays } ?: intentData.bunkerRequest.relays.ifEmpty { defaultRelays }

                            NostrSigner.getInstance().checkForNewRelays(
                                newRelays = relays.toSet(),
                            )

                            AmberUtils.sendBunkerError(
                                intentData,
                                account,
                                intentData.bunkerRequest,
                                applicationEntity?.application?.relays ?: intentData.bunkerRequest.relays,
                                context,
                                closeApplication = intentData.bunkerRequest.closeApplication,
                                onLoading = onLoading,
                                onRemoveIntentData = onRemoveIntentData,
                            )
                        }
                    } else {
                        onRemoveIntentData(intentData, IntentResultType.REMOVE)
                        context.getAppCompatActivity()?.intent = null
                        context.getAppCompatActivity()?.finish()
                    }
                },
            )
        }

        SignerType.SIGN_MESSAGE -> {
            val permission =
                applicationEntity?.permissions?.firstOrNull {
                    it.pkKey == key && it.type == intentData.type.toString()
                }
            val remember =
                remember {
                    mutableStateOf(permission?.acceptable != null)
                }

            val localPackageName =
                if (intentData.bunkerRequest != null) {
                    intentData.bunkerRequest.localKey
                } else {
                    packageName
                }
            SignMessage(
                paddingValues,
                intentData.data,
                permission?.acceptable,
                remember,
                localPackageName,
                applicationName,
                appName,
                intentData.type,
                {
                    coroutineScope.launch(Dispatchers.IO) {
                        val result = CryptoUtils.signString(intentData.data, account.signer.keyPair.privKey!!).toHexKey()

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
                            null,
                            database,
                            onLoading,
                            onRemoveIntentData = onRemoveIntentData,
                        )
                    }
                },
                {
                    coroutineScope.launch(Dispatchers.IO) {
                        if (key == "null") {
                            context.getAppCompatActivity()?.intent = null
                            context.getAppCompatActivity()?.finish()
                            return@launch
                        }

                        val savedApplication = database.applicationDao().getByKey(key)
                        val defaultRelays = NostrSigner.getInstance().settings.defaultRelays
                        val relays = savedApplication?.application?.relays?.ifEmpty { defaultRelays } ?: (intentData.bunkerRequest?.relays?.ifEmpty { defaultRelays } ?: defaultRelays)
                        val application =
                            savedApplication ?: ApplicationWithPermissions(
                                application = ApplicationEntity(
                                    key,
                                    appName,
                                    relays,
                                    "",
                                    "",
                                    "",
                                    account.signer.keyPair.pubKey.toHexKey(),
                                    true,
                                    intentData.bunkerRequest?.secret ?: "",
                                    intentData.bunkerRequest?.secret != null,
                                    account.signPolicy,
                                    intentData.bunkerRequest?.closeApplication ?: true,
                                ),
                                permissions = mutableListOf(),
                            )

                        if (remember.value) {
                            AmberUtils.acceptOrRejectPermission(
                                application,
                                key,
                                intentData,
                                null,
                                false,
                                database,
                            )
                        }

                        if (intentData.bunkerRequest != null) {
                            NostrSigner.getInstance().checkForNewRelays(
                                newRelays = relays.toSet(),
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
                                false,
                            ),
                        )

                        if (intentData.bunkerRequest != null) {
                            AmberUtils.sendBunkerError(
                                intentData,
                                account,
                                intentData.bunkerRequest,
                                relays,
                                context,
                                closeApplication = application.application.closeApplication,
                                onLoading = onLoading,
                                onRemoveIntentData = onRemoveIntentData,
                            )
                        } else {
                            onRemoveIntentData(intentData, IntentResultType.REMOVE)
                            context.getAppCompatActivity()?.intent = null
                            if (application.application.closeApplication) {
                                context.getAppCompatActivity()?.finish()
                            }
                        }
                    }
                },
            )
        }

        SignerType.NIP04_DECRYPT, SignerType.NIP04_ENCRYPT, SignerType.NIP44_ENCRYPT, SignerType.NIP44_DECRYPT, SignerType.DECRYPT_ZAP_EVENT -> {
            val nip = when (intentData.type) {
                SignerType.NIP04_DECRYPT, SignerType.NIP04_ENCRYPT -> 4
                SignerType.NIP44_ENCRYPT, SignerType.NIP44_DECRYPT -> 44
                SignerType.DECRYPT_ZAP_EVENT -> null
                else -> null
            }
            var permission =
                applicationEntity?.permissions?.firstOrNull {
                    it.pkKey == key && it.type == intentData.type.toString()
                }
            if (permission == null && nip != null) {
                permission =
                    applicationEntity?.permissions?.firstOrNull {
                        it.pkKey == key && it.type == "NIP" && it.kind == nip
                    }
            }
            val remember =
                remember {
                    mutableStateOf(permission?.acceptable != null)
                }

            val localPackageName =
                if (intentData.bunkerRequest != null) {
                    intentData.bunkerRequest.localKey
                } else {
                    packageName
                }
            EncryptDecryptData(
                paddingValues,
                intentData.data,
                intentData.encryptedData ?: "",
                permission?.acceptable,
                remember,
                localPackageName,
                applicationName,
                appName,
                intentData.type,
                {
                    coroutineScope.launch(Dispatchers.IO) {
                        val result =
                            if (intentData.encryptedData == "Could not decrypt the message" && (intentData.type == SignerType.DECRYPT_ZAP_EVENT)) {
                                ""
                            } else {
                                intentData.encryptedData ?: ""
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
                            null,
                            database,
                            onRemoveIntentData = onRemoveIntentData,
                            onLoading = onLoading,
                        )
                    }
                },
                {
                    coroutineScope.launch(Dispatchers.IO) {
                        if (key == "null") {
                            return@launch
                        }

                        val defaultRelays = NostrSigner.getInstance().settings.defaultRelays
                        val savedApplication = database.applicationDao().getByKey(key)
                        val relays = savedApplication?.application?.relays?.ifEmpty { defaultRelays } ?: (intentData.bunkerRequest?.relays?.ifEmpty { defaultRelays } ?: defaultRelays)
                        val application =
                            savedApplication ?: ApplicationWithPermissions(
                                application = ApplicationEntity(
                                    key,
                                    appName,
                                    relays,
                                    "",
                                    "",
                                    "",
                                    account.signer.keyPair.pubKey.toHexKey(),
                                    true,
                                    intentData.bunkerRequest?.secret ?: "",
                                    intentData.bunkerRequest?.secret != null,
                                    account.signPolicy,
                                    intentData.bunkerRequest?.closeApplication ?: true,
                                ),
                                permissions = mutableListOf(),
                            )
                        if (remember.value) {
                            AmberUtils.acceptOrRejectPermission(
                                application,
                                key,
                                intentData,
                                null,
                                false,
                                database,
                            )
                        }

                        if (intentData.bunkerRequest != null) {
                            NostrSigner.getInstance().checkForNewRelays(
                                newRelays = relays.toSet(),
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
                                false,
                            ),
                        )

                        if (intentData.bunkerRequest != null) {
                            AmberUtils.sendBunkerError(
                                intentData,
                                account,
                                intentData.bunkerRequest,
                                relays,
                                context,
                                closeApplication = application.application.closeApplication,
                                onRemoveIntentData = onRemoveIntentData,
                                onLoading,
                            )
                        } else {
                            onRemoveIntentData(intentData, IntentResultType.REMOVE)
                            context.getAppCompatActivity()?.intent = null
                            if (application.application.closeApplication) {
                                context.getAppCompatActivity()?.finish()
                            }
                        }
                    }
                },
            )
        }

        else -> {
            val event = intentData.event!!
            val accounts =
                LocalPreferences.allSavedAccounts(context).filter {
                    it.npub.bechToBytes().toHexKey() == event.pubKey
                }

            if (accounts.isEmpty() && !isPrivateEvent(event.kind, event.tags)) {
                Column(
                    Modifier.fillMaxSize(),
                    Arrangement.Center,
                    Alignment.CenterHorizontally,
                ) {
                    Text(
                        Hex.decode(event.pubKey).toNpub().toShortenHex(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Not logged in")
                }
            } else {
                val permission =
                    applicationEntity?.permissions?.firstOrNull {
                        val nip = event.kind.kindToNip()?.toIntOrNull()
                        it.pkKey == key && ((it.type == intentData.type.toString() && it.kind == event.kind) || (nip != null && it.type == "NIP" && it.kind == nip))
                    }
                val remember =
                    remember {
                        mutableStateOf(permission?.acceptable != null)
                    }
                val localPackageName =
                    if (intentData.bunkerRequest != null) {
                        intentData.bunkerRequest.localKey
                    } else {
                        packageName
                    }
                EventData(
                    paddingValues,
                    permission?.acceptable,
                    remember,
                    localPackageName,
                    appName,
                    applicationName,
                    event,
                    event.toJson(),
                    intentData.type,
                    {
                        if (event.pubKey != account.signer.keyPair.pubKey.toHexKey() && !isPrivateEvent(event.kind, event.tags)) {
                            coroutineScope.launch {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.event_pubkey_is_not_equal_to_current_logged_in_user),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                            return@EventData
                        }

                        val eventJson = event.toJson()

                        sendResult(
                            context = context,
                            packageName = packageName,
                            account = account,
                            key = key,
                            rememberChoice = remember.value,
                            clipboardManager = clipboardManager,
                            event = event.toJson(),
                            value = if (event is LnZapRequestEvent &&
                                event.tags.any {
                                        tag ->
                                    tag.any { t -> t == "anon" }
                                }
                            ) {
                                eventJson
                            } else {
                                event.sig
                            },
                            intentData = intentData,
                            kind = event.kind,
                            database = database,
                            onLoading = onLoading,
                            onRemoveIntentData = onRemoveIntentData,
                            signPolicy = null,
                            appName = null,
                            permissions = null,
                        )
                    },
                    {
                        onLoading(true)
                        coroutineScope.launch(Dispatchers.IO) {
                            if (key == "null") {
                                return@launch
                            }

                            val defaultRelays = NostrSigner.getInstance().settings.defaultRelays
                            val savedApplication = database.applicationDao().getByKey(key)
                            val relays = savedApplication?.application?.relays?.ifEmpty { defaultRelays } ?: (intentData.bunkerRequest?.relays?.ifEmpty { defaultRelays } ?: defaultRelays)

                            val application =
                                savedApplication ?: ApplicationWithPermissions(
                                    application = ApplicationEntity(
                                        key,
                                        appName,
                                        relays,
                                        "",
                                        "",
                                        "",
                                        account.signer.keyPair.pubKey.toHexKey(),
                                        true,
                                        intentData.bunkerRequest?.secret ?: "",
                                        intentData.bunkerRequest?.secret != null,
                                        account.signPolicy,
                                        intentData.bunkerRequest?.closeApplication ?: true,
                                    ),
                                    permissions = mutableListOf(),
                                )

                            if (intentData.bunkerRequest != null) {
                                NostrSigner.getInstance().checkForNewRelays(
                                    newRelays = relays.toSet(),
                                )
                            }

                            if (remember.value) {
                                AmberUtils.acceptOrRejectPermission(
                                    application,
                                    key,
                                    intentData,
                                    event.kind,
                                    false,
                                    database,
                                )
                            }

                            database.applicationDao().insertApplicationWithPermissions(application)

                            database.applicationDao().addHistory(
                                HistoryEntity(
                                    0,
                                    key,
                                    intentData.type.toString(),
                                    event.kind,
                                    TimeUtils.now(),
                                    false,
                                ),
                            )

                            if (intentData.bunkerRequest != null) {
                                AmberUtils.sendBunkerError(
                                    intentData,
                                    account,
                                    intentData.bunkerRequest,
                                    relays,
                                    context,
                                    closeApplication = application.application.closeApplication,
                                    onLoading = onLoading,
                                    onRemoveIntentData = onRemoveIntentData,
                                )
                            } else {
                                onRemoveIntentData(intentData, IntentResultType.REMOVE)
                                context.getAppCompatActivity()?.intent = null
                                if (application.application.closeApplication) {
                                    context.getAppCompatActivity()?.finish()
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

fun isPrivateEvent(
    kind: Int,
    tags: Array<Array<String>>,
): Boolean {
    return kind == LnZapRequestEvent.KIND && tags.any { t -> t.size > 1 && t[0] == "anon" }
}

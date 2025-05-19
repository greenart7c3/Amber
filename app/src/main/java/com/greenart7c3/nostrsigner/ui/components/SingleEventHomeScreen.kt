package com.greenart7c3.nostrsigner.ui.components

import android.util.Log
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryEntity2
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.kindToNip
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.BunkerRequestUtils
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.getAppCompatActivity
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.RememberType
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip55AndroidSigner.signString
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SingleEventHomeScreen(
    paddingValues: PaddingValues,
    packageName: String?,
    applicationName: String?,
    intentData: IntentData,
    account: Account,
    onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
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
                    Amber.instance.getDatabase(account.npub).applicationDao().getBySecret(intentData.bunkerRequest.secret)
                } else {
                    Amber.instance.getDatabase(account.npub).applicationDao().getByKey(key)
                }
        }
    }

    var appName = applicationEntity?.application?.name?.ifBlank { key.toShortenHex() } ?: packageName ?: intentData.name
    appName = appName.ifBlank { key.toShortenHex() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboard.current

    when (intentData.type) {
        SignerType.GET_PUBLIC_KEY, SignerType.CONNECT -> {
            if (intentData.bunkerRequest != null && intentData.type == SignerType.GET_PUBLIC_KEY) {
                BunkerGetPubKeyScreen(
                    paddingValues,
                    account,
                    appName,
                    { permissions, signPolicy, closeApplication, rememberType ->
                        val result = if (intentData.type == SignerType.CONNECT) {
                            intentData.bunkerRequest.nostrConnectSecret.ifBlank { "ack" }
                        } else {
                            account.hexKey
                        }

                        BunkerRequestUtils.sendResult(
                            context = context,
                            account = account,
                            key = key,
                            event = result,
                            bunkerRequest = intentData.bunkerRequest,
                            kind = null,
                            onLoading = onLoading,
                            permissions = permissions,
                            appName = applicationName ?: appName,
                            signPolicy = signPolicy,
                            shouldCloseApplication = closeApplication,
                            rememberType = rememberType,
                        )
                    },
                    {
                        Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
                            val defaultRelays = Amber.instance.settings.defaultRelays
                            val savedApplication = Amber.instance.getDatabase(account.npub).applicationDao().getByKey(key)
                            val relays = savedApplication?.application?.relays?.ifEmpty { defaultRelays } ?: intentData.bunkerRequest.relays.ifEmpty { defaultRelays }

                            Amber.instance.checkForNewRelays(
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
                    },
                )
            } else if (intentData.bunkerRequest != null) {
                BunkerConnectRequestScreen(
                    applicationEntity?.application?.closeApplication ?: intentData.bunkerRequest.closeApplication,
                    paddingValues,
                    account,
                    appName,
                    intentData.permissions,
                    { permissions, signPolicy, closeApplication, rememberType ->
                        val result = if (intentData.type == SignerType.CONNECT) {
                            intentData.bunkerRequest.nostrConnectSecret.ifBlank { "ack" }
                        } else {
                            account.hexKey
                        }

                        BunkerRequestUtils.sendResult(
                            context = context,
                            account = account,
                            key = key,
                            event = result,
                            bunkerRequest = intentData.bunkerRequest,
                            kind = null,
                            onLoading = onLoading,
                            permissions = permissions,
                            appName = applicationName ?: appName,
                            signPolicy = signPolicy,
                            shouldCloseApplication = closeApplication,
                            rememberType = rememberType,
                        )
                    },
                    {
                        Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
                            val defaultRelays = Amber.instance.settings.defaultRelays
                            val savedApplication = Amber.instance.getDatabase(account.npub).applicationDao().getByKey(key)
                            val relays = savedApplication?.application?.relays?.ifEmpty { defaultRelays } ?: intentData.bunkerRequest.relays.ifEmpty { defaultRelays }

                            Amber.instance.checkForNewRelays(
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
                    },
                )
            } else {
                LoginWithPubKey(
                    applicationEntity?.application?.closeApplication != false,
                    paddingValues,
                    account,
                    packageName,
                    appName,
                    applicationName,
                    intentData.permissions,
                    { permissions, signPolicy, closeApplication, rememberType ->
                        val result = if (packageName == null) {
                            account.hexKey
                        } else {
                            account.npub
                        }

                        IntentUtils.sendResult(
                            context,
                            packageName,
                            account,
                            key,
                            clipboardManager,
                            result,
                            result,
                            intentData,
                            null,
                            permissions = permissions,
                            appName = applicationName ?: appName,
                            onLoading = onLoading,
                            signPolicy = signPolicy,
                            onRemoveIntentData = onRemoveIntentData,
                            shouldCloseApplication = closeApplication,
                            rememberType = rememberType,
                        )
                    },
                    {
                        context.getAppCompatActivity()?.intent = null
                        context.getAppCompatActivity()?.finish()
                        onRemoveIntentData(listOf(intentData), IntentResultType.REMOVE)
                        onLoading(false)
                    },
                )
            }
        }

        SignerType.SIGN_MESSAGE -> {
            val permission =
                applicationEntity?.permissions?.firstOrNull {
                    it.pkKey == key && it.type == intentData.type.toString()
                }

            val localPackageName =
                if (intentData.bunkerRequest != null) {
                    intentData.bunkerRequest.localKey
                } else {
                    packageName
                }
            val acceptUntil = permission?.acceptUntil ?: 0
            val rejectUntil = permission?.rejectUntil ?: 0

            val acceptOrReject = if (rejectUntil == 0L && acceptUntil == 0L) {
                null
            } else if (rejectUntil > TimeUtils.now() && rejectUntil > 0) {
                false
            } else if (acceptUntil > TimeUtils.now() && acceptUntil > 0) {
                true
            } else {
                null
            }

            SignMessage(
                account,
                paddingValues,
                intentData.data,
                acceptOrReject,
                localPackageName,
                applicationName,
                appName,
                intentData.type,
                {
                    Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
                        val result = signString(intentData.data, account.signer.keyPair.privKey!!).toHexKey()

                        if (intentData.bunkerRequest != null) {
                            BunkerRequestUtils.sendResult(
                                context = context,
                                account = account,
                                key = key,
                                event = result,
                                bunkerRequest = intentData.bunkerRequest,
                                kind = null,
                                onLoading = onLoading,
                                permissions = null,
                                appName = applicationName ?: appName,
                                signPolicy = null,
                                shouldCloseApplication = intentData.bunkerRequest.closeApplication,
                                rememberType = it,
                            )
                        } else {
                            IntentUtils.sendResult(
                                context,
                                packageName,
                                account,
                                key,
                                clipboardManager,
                                result,
                                result,
                                intentData,
                                null,
                                onLoading,
                                onRemoveIntentData = onRemoveIntentData,
                                rememberType = it,
                            )
                        }
                    }
                },
                {
                    Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
                        if (key == "null") {
                            context.getAppCompatActivity()?.intent = null
                            context.getAppCompatActivity()?.finish()
                            return@launch
                        }

                        val savedApplication = Amber.instance.getDatabase(account.npub).applicationDao().getByKey(key)
                        val defaultRelays = Amber.instance.settings.defaultRelays
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
                                    account.hexKey,
                                    true,
                                    intentData.bunkerRequest?.secret ?: "",
                                    intentData.bunkerRequest?.secret != null,
                                    account.signPolicy,
                                    intentData.bunkerRequest?.closeApplication != false,
                                ),
                                permissions = mutableListOf(),
                            )

                        if (it != RememberType.NEVER) {
                            AmberUtils.acceptOrRejectPermission(
                                application,
                                key,
                                intentData,
                                null,
                                false,
                                it,
                                account,
                            )
                        }

                        if (intentData.bunkerRequest != null) {
                            Amber.instance.checkForNewRelays(
                                newRelays = relays.toSet(),
                            )
                        }

                        Amber.instance.getDatabase(account.npub).applicationDao().insertApplicationWithPermissions(application)

                        Amber.instance.getDatabase(account.npub).applicationDao().addHistory(
                            HistoryEntity2(
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
                            context.getAppCompatActivity()?.intent = null
                            if (application.application.closeApplication) {
                                context.getAppCompatActivity()?.finish()
                            }
                            onRemoveIntentData(listOf(intentData), IntentResultType.REMOVE)
                            onLoading(false)
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

            val localPackageName =
                if (intentData.bunkerRequest != null) {
                    intentData.bunkerRequest.localKey
                } else {
                    packageName
                }

            val acceptUntil = permission?.acceptUntil ?: 0
            val rejectUntil = permission?.rejectUntil ?: 0

            val acceptOrReject = if (rejectUntil == 0L && acceptUntil == 0L) {
                null
            } else if (rejectUntil > TimeUtils.now() && rejectUntil > 0) {
                false
            } else if (acceptUntil > TimeUtils.now() && acceptUntil > 0) {
                true
            } else {
                null
            }

            EncryptDecryptData(
                account,
                paddingValues,
                intentData.data,
                intentData.encryptedData ?: "",
                acceptOrReject,
                localPackageName,
                applicationName,
                appName,
                intentData.type,
                {
                    val result =
                        if (intentData.encryptedData == "Could not decrypt the message" && (intentData.type == SignerType.DECRYPT_ZAP_EVENT)) {
                            ""
                        } else {
                            intentData.encryptedData ?: ""
                        }

                    if (intentData.bunkerRequest != null) {
                        BunkerRequestUtils.sendResult(
                            context = context,
                            account = account,
                            key = key,
                            event = result,
                            bunkerRequest = intentData.bunkerRequest,
                            kind = null,
                            onLoading = onLoading,
                            permissions = null,
                            appName = applicationName ?: appName,
                            signPolicy = null,
                            shouldCloseApplication = intentData.bunkerRequest.closeApplication,
                            rememberType = it,
                        )
                    } else {
                        IntentUtils.sendResult(
                            context,
                            packageName,
                            account,
                            key,
                            clipboardManager,
                            result,
                            result,
                            intentData,
                            null,
                            onRemoveIntentData = onRemoveIntentData,
                            onLoading = onLoading,
                            rememberType = it,
                        )
                    }
                },
                {
                    Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
                        if (key == "null") {
                            return@launch
                        }

                        val defaultRelays = Amber.instance.settings.defaultRelays
                        val savedApplication = Amber.instance.getDatabase(account.npub).applicationDao().getByKey(key)
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
                                    account.hexKey,
                                    true,
                                    intentData.bunkerRequest?.secret ?: "",
                                    intentData.bunkerRequest?.secret != null,
                                    account.signPolicy,
                                    intentData.bunkerRequest?.closeApplication != false,
                                ),
                                permissions = mutableListOf(),
                            )
                        if (it != RememberType.NEVER) {
                            AmberUtils.acceptOrRejectPermission(
                                application,
                                key,
                                intentData,
                                null,
                                false,
                                it,
                                account,
                            )
                        }

                        if (intentData.bunkerRequest != null) {
                            Amber.instance.checkForNewRelays(
                                newRelays = relays.toSet(),
                            )
                        }

                        Amber.instance.getDatabase(account.npub).applicationDao().insertApplicationWithPermissions(application)

                        Amber.instance.getDatabase(account.npub).applicationDao().addHistory(
                            HistoryEntity2(
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
                            context.getAppCompatActivity()?.intent = null
                            if (application.application.closeApplication) {
                                context.getAppCompatActivity()?.finish()
                            }
                            onRemoveIntentData(listOf(intentData), IntentResultType.REMOVE)
                            onLoading(false)
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
                val localPackageName =
                    if (intentData.bunkerRequest != null) {
                        intentData.bunkerRequest.localKey
                    } else {
                        packageName
                    }

                val acceptUntil = permission?.acceptUntil ?: 0
                val rejectUntil = permission?.rejectUntil ?: 0

                val acceptOrReject = if (rejectUntil == 0L && acceptUntil == 0L) {
                    null
                } else if (rejectUntil > TimeUtils.now() && rejectUntil > 0) {
                    false
                } else if (acceptUntil > TimeUtils.now() && acceptUntil > 0) {
                    true
                } else {
                    null
                }

                EventData(
                    account,
                    paddingValues,
                    acceptOrReject,
                    localPackageName,
                    appName,
                    applicationName,
                    event,
                    event.toJson(),
                    intentData.type,
                    {
                        if (event.pubKey != account.hexKey && !isPrivateEvent(event.kind, event.tags)) {
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

                        if (intentData.bunkerRequest != null) {
                            BunkerRequestUtils.sendResult(
                                context = context,
                                account = account,
                                key = key,
                                event = eventJson,
                                bunkerRequest = intentData.bunkerRequest,
                                kind = event.kind,
                                onLoading = onLoading,
                                permissions = null,
                                appName = applicationName ?: appName,
                                signPolicy = null,
                                shouldCloseApplication = intentData.bunkerRequest.closeApplication,
                                rememberType = it,
                            )
                        } else {
                            IntentUtils.sendResult(
                                context = context,
                                packageName = packageName,
                                account = account,
                                key = key,
                                clipboardManager = clipboardManager,
                                event = event.toJson(),
                                value = if (event is LnZapRequestEvent &&
                                    event.tags.any { tag ->
                                        tag.any { t -> t == "anon" }
                                    }
                                ) {
                                    eventJson
                                } else {
                                    event.sig
                                },
                                intentData = intentData,
                                kind = event.kind,
                                onLoading = onLoading,
                                onRemoveIntentData = onRemoveIntentData,
                                signPolicy = null,
                                appName = null,
                                permissions = null,
                                rememberType = it,
                            )
                        }
                    },
                    {
                        onLoading(true)
                        Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
                            if (key == "null") {
                                return@launch
                            }

                            val defaultRelays = Amber.instance.settings.defaultRelays
                            val savedApplication = Amber.instance.getDatabase(account.npub).applicationDao().getByKey(key)
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
                                        account.hexKey,
                                        true,
                                        intentData.bunkerRequest?.secret ?: "",
                                        intentData.bunkerRequest?.secret != null,
                                        account.signPolicy,
                                        intentData.bunkerRequest?.closeApplication != false,
                                    ),
                                    permissions = mutableListOf(),
                                )

                            if (intentData.bunkerRequest != null) {
                                Amber.instance.checkForNewRelays(
                                    newRelays = relays.toSet(),
                                )
                            }

                            if (it != RememberType.NEVER) {
                                AmberUtils.acceptOrRejectPermission(
                                    application,
                                    key,
                                    intentData,
                                    event.kind,
                                    false,
                                    it,
                                    account,
                                )
                            }

                            Amber.instance.getDatabase(account.npub).applicationDao().insertApplicationWithPermissions(application)

                            Amber.instance.getDatabase(account.npub).applicationDao().addHistory(
                                HistoryEntity2(
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
                                val activity = context.getAppCompatActivity()
                                Log.d("activity", "is activity null ${activity == null}")
                                activity?.intent = null
                                Log.d("activity", "Shold close app ${application.application.closeApplication}")
                                if (application.application.closeApplication) {
                                    activity?.finish()
                                }
                                onRemoveIntentData(listOf(intentData), IntentResultType.REMOVE)
                                onLoading(false)
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

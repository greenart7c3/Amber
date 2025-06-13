package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryEntity2
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.BunkerRequest
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.EncryptionType
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.containsNip
import com.greenart7c3.nostrsigner.relays.AmberListenerSingleton
import com.greenart7c3.nostrsigner.service.IntentUtils.getUnsignedEvent
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.ui.RememberType
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.jackson.EventMapper
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object BunkerRequestUtils {
    val state = Channel<String>(Channel.CONFLATED)
    private val bunkerRequests = ConcurrentHashMap<String, BunkerRequest>()

    fun addRequest(request: BunkerRequest) {
        bunkerRequests[request.id] = request
        state.trySend("")
    }

    fun clearRequests() {
        bunkerRequests.clear()
        state.trySend("")
    }

    fun remove(id: String) {
        bunkerRequests.remove(id)
        state.trySend("")
    }

    fun getBunkerRequests(): Map<String, BunkerRequest> {
        return bunkerRequests
    }

    fun sendBunkerResponse(
        context: Context,
        account: Account,
        bunkerRequest: BunkerRequest,
        bunkerResponse: BunkerResponse,
        relays: List<RelaySetupInfo>,
        onLoading: (Boolean) -> Unit,
        onDone: (Boolean) -> Unit,
    ) {
        if (relays.isEmpty()) {
            onDone(true)
            onLoading(false)
            Amber.instance.applicationIOScope.launch {
                Amber.instance.getDatabase(account.npub).applicationDao().insertLog(
                    LogEntity(
                        id = 0,
                        url = bunkerRequest.localKey,
                        type = "bunker response",
                        message = "No permission created for this client. Please logout from the client and create a new bunker connection",
                        time = System.currentTimeMillis(),
                    ),
                )
            }

            return
        }

        if (Amber.instance.settings.useProxy) {
            val isProxyWorking = Amber.instance.isSocksProxyAlive("127.0.0.1", Amber.instance.settings.proxyPort)
            if (!isProxyWorking) {
                AmberListenerSingleton.accountStateViewModel?.toast(context.getString(R.string.warning), context.getString(R.string.failed_to_connect_to_tor_orbot))
                onDone(false)
                onLoading(false)
                return
            }
        }

        AmberListenerSingleton.getListener()?.let {
            Amber.instance.client.unsubscribe(it)
        }
        AmberListenerSingleton.setListener(
            context,
        )
        Amber.instance.client.subscribe(
            AmberListenerSingleton.getListener()!!,
        )

        Amber.instance.applicationIOScope.launch {
            relays.forEach { relay ->
                Amber.instance.getDatabase(account.npub).applicationDao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url,
                        type = "bunker response",
                        message = EventMapper.mapper.writeValueAsString(bunkerResponse),
                        time = System.currentTimeMillis(),
                    ),
                )
            }
        }

        when (bunkerRequest.encryptionType) {
            EncryptionType.NIP04 -> {
                account.signer.nip04Encrypt(
                    EventMapper.mapper.writeValueAsString(bunkerResponse),
                    bunkerRequest.localKey,
                ) { encryptedContent ->
                    sendBunkerResponse(
                        bunkerRequest = bunkerRequest,
                        account = account,
                        localKey = bunkerRequest.localKey,
                        encryptedContent = encryptedContent,
                        relays = relays.ifEmpty { Amber.instance.getSavedRelays().toList() },
                        onLoading = onLoading,
                        onDone = onDone,
                    )
                }
            }
            EncryptionType.NIP44 -> {
                account.signer.nip44Encrypt(
                    EventMapper.mapper.writeValueAsString(bunkerResponse),
                    bunkerRequest.localKey,
                ) { encryptedContent ->
                    sendBunkerResponse(
                        bunkerRequest = bunkerRequest,
                        account = account,
                        localKey = bunkerRequest.localKey,
                        encryptedContent = encryptedContent,
                        relays = relays.ifEmpty { Amber.instance.getSavedRelays().toList() },
                        onLoading = onLoading,
                        onDone = onDone,
                    )
                }
            }
        }
    }

    private fun sendBunkerResponse(
        bunkerRequest: BunkerRequest? = null,
        account: Account,
        localKey: String,
        encryptedContent: String,
        relays: List<RelaySetupInfo>,
        onLoading: (Boolean) -> Unit,
        onDone: (Boolean) -> Unit,
    ) {
        account.signer.sign<Event>(
            TimeUtils.now(),
            24133,
            arrayOf(arrayOf("p", localKey)),
            encryptedContent,
        ) {
            Amber.instance.applicationIOScope.launch {
                Log.d(Amber.TAG, "Sending response to relays ${relays.map { it.url }} type ${bunkerRequest?.method}")

                if (Amber.instance.client.getAll().any { !it.isConnected() }) {
                    Amber.instance.checkForNewRelays(
                        newRelays = relays.toSet(),
                    )
                }

                var success = false
                var errorCount = 0
                while (!success && errorCount < 3) {
                    success = Amber.instance.client.sendAndWaitForResponse(it, relayList = relays)
                    if (!success) {
                        errorCount++
                        relays.forEach {
                            if (Amber.instance.client.getRelay(it.url)?.isConnected() == false) {
                                Amber.instance.client.getRelay(it.url)?.connect()
                            }
                        }
                        delay(1000)
                    }
                }
                if (success) {
                    Log.d(Amber.TAG, "Success response to relays ${relays.map { it.url }} type ${bunkerRequest?.method}")
                    onDone(true)
                } else {
                    onDone(false)
                    AmberListenerSingleton.showErrorMessage()
                    Log.d(Amber.TAG, "Failed response to relays ${relays.map { it.url }} type ${bunkerRequest?.method}")
                }
                AmberListenerSingleton.getListener()?.let {
                    Amber.instance.client.unsubscribe(it)
                }
                onLoading(false)
            }
        }
    }

    fun getTypeFromBunker(bunkerRequest: BunkerRequest): SignerType {
        return when (bunkerRequest.method) {
            "sign_message" -> SignerType.SIGN_MESSAGE
            "connect" -> SignerType.CONNECT
            "sign_event" -> SignerType.SIGN_EVENT
            "get_public_key" -> SignerType.GET_PUBLIC_KEY
            "nip04_encrypt" -> SignerType.NIP04_ENCRYPT
            "nip04_decrypt" -> SignerType.NIP04_DECRYPT
            "nip44_encrypt" -> SignerType.NIP44_ENCRYPT
            "nip44_decrypt" -> SignerType.NIP44_DECRYPT
            else -> SignerType.INVALID
        }
    }

    fun getDataFromBunker(bunkerRequest: BunkerRequest): String {
        return when (bunkerRequest.method) {
            "sign_message" -> bunkerRequest.params.first()
            "connect" -> "ack"
            "sign_event" -> {
                val amberEvent = AmberEvent.fromJson(bunkerRequest.params.first())
                AmberEvent.toEvent(amberEvent).toJson()
            }
            "nip04_encrypt", "nip04_decrypt", "nip44_encrypt", "nip44_decrypt" -> bunkerRequest.params.getOrElse(1) { "" }
            else -> ""
        }
    }

    fun getIntentDataFromBunkerRequest(
        context: Context,
        intent: Intent,
        bunkerRequest: BunkerRequest,
        route: String?,
        onReady: (IntentData?) -> Unit,
    ) {
        val type = getTypeFromBunker(bunkerRequest)
        if (type == SignerType.INVALID) {
            Log.d(Amber.TAG, "Invalid type ${bunkerRequest.method}")
            onReady(null)
            return
        }
        val data = getDataFromBunker(bunkerRequest)
        val account = LocalPreferences.loadFromEncryptedStorageSync(context, bunkerRequest.currentAccount)!!

        val pubKey =
            if (bunkerRequest.method.endsWith("encrypt") || bunkerRequest.method.endsWith("decrypt")) {
                bunkerRequest.params.first()
            } else {
                ""
            }
        val id = bunkerRequest.id
        val permissions = mutableListOf<Permission>()
        if (type == SignerType.CONNECT && bunkerRequest.params.size > 2) {
            val split = bunkerRequest.params[2].split(",")
            split.forEach {
                if (it.isNotBlank()) {
                    val split2 = it.split(":")
                    val permissionType = split2.first()
                    val kind =
                        try {
                            split2[1].toInt()
                        } catch (_: Exception) {
                            null
                        }

                    permissions.add(
                        Permission(
                            permissionType,
                            kind,
                        ),
                    )
                }
            }
        }

        permissions.removeIf { it.kind == null && (it.type == "sign_event" || it.type == "nip") }
        permissions.removeIf { it.type == "nip" && (it.kind == null || !it.kind.containsNip()) }

        @Suppress("KotlinConstantConditions")
        when (type) {
            SignerType.CONNECT -> {
                onReady(
                    IntentData(
                        data,
                        "",
                        type,
                        pubKey,
                        id,
                        intent.extras?.getString("callbackUrl"),
                        CompressionType.NONE,
                        ReturnType.EVENT,
                        permissions,
                        bunkerRequest.currentAccount,
                        mutableStateOf(true),
                        mutableStateOf(RememberType.NEVER),
                        bunkerRequest,
                        route,
                        null,
                        null,
                    ),
                )
            }

            SignerType.SIGN_EVENT -> {
                val unsignedEvent = getUnsignedEvent(data, account)
                account.signer.sign<Event>(
                    unsignedEvent.createdAt,
                    unsignedEvent.kind,
                    unsignedEvent.tags,
                    unsignedEvent.content,
                ) {
                    onReady(
                        IntentData(
                            data,
                            "",
                            type,
                            pubKey,
                            id,
                            intent.extras?.getString("callbackUrl"),
                            CompressionType.NONE,
                            ReturnType.EVENT,
                            permissions,
                            bunkerRequest.currentAccount,
                            mutableStateOf(true),
                            mutableStateOf(RememberType.NEVER),
                            bunkerRequest,
                            route,
                            it,
                            null,
                        ),
                    )
                }
            }

            SignerType.NIP04_ENCRYPT, SignerType.NIP04_DECRYPT, SignerType.NIP44_ENCRYPT, SignerType.NIP44_DECRYPT, SignerType.DECRYPT_ZAP_EVENT -> {
                val result =
                    try {
                        AmberUtils.encryptOrDecryptData(
                            data,
                            type,
                            account,
                            pubKey,
                        ) ?: "Could not decrypt the message"
                    } catch (e: Exception) {
                        Amber.instance.applicationIOScope.launch {
                            val database = Amber.instance.getDatabase(account.npub)
                            database.applicationDao().insertLog(
                                LogEntity(
                                    0,
                                    bunkerRequest.localKey,
                                    type.toString(),
                                    e.message ?: "Could not decrypt the message",
                                    System.currentTimeMillis(),
                                ),
                            )
                        }
                        "Could not decrypt the message"
                    }

                onReady(
                    IntentData(
                        data,
                        "",
                        type,
                        pubKey,
                        id,
                        intent.extras?.getString("callbackUrl"),
                        CompressionType.NONE,
                        ReturnType.EVENT,
                        permissions,
                        bunkerRequest.currentAccount,
                        mutableStateOf(true),
                        mutableStateOf(RememberType.NEVER),
                        bunkerRequest,
                        route,
                        null,
                        result,
                    ),
                )
            }

            SignerType.GET_PUBLIC_KEY -> {
                onReady(
                    IntentData(
                        account.npub,
                        "",
                        type,
                        pubKey,
                        id,
                        intent.extras?.getString("callbackUrl"),
                        CompressionType.NONE,
                        ReturnType.EVENT,
                        permissions,
                        bunkerRequest.currentAccount,
                        mutableStateOf(true),
                        mutableStateOf(RememberType.NEVER),
                        bunkerRequest,
                        route,
                        null,
                        null,
                    ),
                )
            }

            SignerType.SIGN_MESSAGE -> {
                onReady(
                    IntentData(
                        data,
                        "",
                        type,
                        pubKey,
                        id,
                        intent.extras?.getString("callbackUrl"),
                        CompressionType.NONE,
                        ReturnType.EVENT,
                        permissions,
                        bunkerRequest.currentAccount,
                        mutableStateOf(true),
                        mutableStateOf(RememberType.NEVER),
                        bunkerRequest,
                        route,
                        null,
                        null,
                    ),
                )
            }

            SignerType.INVALID -> {
                onReady(null)
            }
        }
    }

    fun sendResult(
        context: Context,
        account: Account,
        key: String,
        event: String,
        bunkerRequest: BunkerRequest,
        kind: Int?,
        onLoading: (Boolean) -> Unit,
        permissions: List<Permission>? = null,
        appName: String? = null,
        signPolicy: Int? = null,
        shouldCloseApplication: Boolean? = null,
        rememberType: RememberType,
        intentData: IntentData,
        onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
        oldKey: String = "",
    ) {
        onLoading(true)
        Amber.instance.applicationIOScope.launch {
            val database = Amber.instance.getDatabase(account.npub)
            val defaultRelays = Amber.instance.settings.defaultRelays

            if (oldKey.isNotBlank()) {
                database.applicationDao().delete(oldKey)
                database.applicationDao().deleteHistory(oldKey)
            }

            var savedApplication = database.applicationDao().getByKey(key)
            if (savedApplication == null && bunkerRequest.secret.isNotBlank()) {
                savedApplication = database.applicationDao().getByKey(bunkerRequest.secret)
                if (savedApplication != null) {
                    val tempApplication = savedApplication.application.copy(
                        key = key,
                    )
                    val tempApp2 = ApplicationWithPermissions(
                        application = tempApplication,
                        permissions = savedApplication.permissions.map {
                            it.copy()
                        }.toMutableList(),
                    )

                    database.applicationDao().delete(bunkerRequest.secret)
                    database.applicationDao().insertApplicationWithPermissions(tempApp2)
                }
            }
            savedApplication = database.applicationDao().getByKey(key)
            val relays = savedApplication?.application?.relays?.ifEmpty { defaultRelays } ?: bunkerRequest.relays.ifEmpty { defaultRelays }
            Amber.instance.checkForNewRelays(
                newRelays = relays.toSet(),
            )

            val application =
                savedApplication ?: ApplicationWithPermissions(
                    application = ApplicationEntity(
                        key,
                        appName ?: "",
                        relays,
                        "",
                        "",
                        "",
                        account.hexKey,
                        true,
                        bunkerRequest.secret,
                        bunkerRequest.secret.isNotBlank(),
                        account.signPolicy,
                        shouldCloseApplication ?: bunkerRequest.closeApplication,
                    ),
                    permissions = mutableListOf(),
                )
            application.application.isConnected = true

            if (signPolicy != null) {
                AmberUtils.configureSignPolicy(application, signPolicy, key, permissions)
            }

            val type = getTypeFromBunker(bunkerRequest)
            if (rememberType != RememberType.NEVER) {
                AmberUtils.acceptPermission(
                    application = application,
                    key = key,
                    type = type,
                    kind = kind,
                    rememberType = rememberType,
                )
            }

            if (type == SignerType.CONNECT) {
                database.applicationDao().deletePermissions(key)
                application.application.isConnected = true
                shouldCloseApplication?.let {
                    application.application.closeApplication = it
                }
                if (!application.permissions.any { it.type == SignerType.GET_PUBLIC_KEY.toString() }) {
                    application.permissions.add(
                        ApplicationPermissionsEntity(
                            null,
                            key,
                            SignerType.GET_PUBLIC_KEY.toString(),
                            null,
                            true,
                            RememberType.ALWAYS.screenCode,
                            Long.MAX_VALUE / 1000,
                            0,
                        ),
                    )
                }
            }

            val localIntentData = intentData.copy()
            clearRequests()
            onRemoveIntentData(listOf(intentData), IntentResultType.REMOVE)

            // assume that everything worked and try to revert it if it fails
            EventNotificationConsumer(context).notificationManager().cancelAll()
            database.applicationDao().insertApplicationWithPermissions(application)
            database.applicationDao().addHistory(
                HistoryEntity2(
                    0,
                    key,
                    type.toString(),
                    kind,
                    TimeUtils.now(),
                    true,
                ),
            )

            EventNotificationConsumer(context).notificationManager().cancelAll()
            sendBunkerResponse(
                context,
                account,
                bunkerRequest,
                BunkerResponse(bunkerRequest.id, event, null),
                application.application.relays.ifEmpty { relays },
                onLoading,
                onDone = {
                    Amber.instance.applicationIOScope.launch {
                        if (!it) {
                            if (rememberType != RememberType.NEVER) {
                                if (type == SignerType.SIGN_EVENT) {
                                    kind?.let {
                                        database.applicationDao().deletePermissions(key, type.toString(), kind)
                                    }
                                } else {
                                    database.applicationDao().deletePermissions(key, type.toString())
                                }
                            }

                            onLoading(false)
                            addRequest(localIntentData.bunkerRequest!!)
                            onRemoveIntentData(listOf(localIntentData), IntentResultType.ADD)
                        } else {
                            val activity = Amber.instance.getMainActivity()
                            activity?.intent = null
                            if (application.application.closeApplication) {
                                activity?.finish()
                            }
                        }
                    }
                },
            )
        }
    }

    fun sendRejection(
        key: String,
        account: Account,
        bunkerRequest: BunkerRequest,
        appName: String,
        rememberType: RememberType,
        signerType: SignerType,
        kind: Int?,
        intentData: IntentData,
        onLoading: (Boolean) -> Unit,
        onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    ) {
        Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
            val savedApplication = Amber.instance.getDatabase(account.npub).applicationDao().getByKey(key)
            val defaultRelays = Amber.instance.settings.defaultRelays
            val relays = savedApplication?.application?.relays?.ifEmpty { defaultRelays } ?: bunkerRequest.relays.ifEmpty { defaultRelays }
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
                        bunkerRequest.secret,
                        bunkerRequest.secret.isNotBlank(),
                        account.signPolicy,
                        bunkerRequest.closeApplication,
                    ),
                    permissions = mutableListOf(),
                )

            if (rememberType != RememberType.NEVER) {
                AmberUtils.acceptOrRejectPermission(
                    application,
                    key,
                    signerType,
                    kind,
                    false,
                    rememberType,
                    account,
                )
            }

            Amber.instance.checkForNewRelays(
                newRelays = relays.toSet(),
            )

            Amber.instance.getDatabase(account.npub).applicationDao().insertApplicationWithPermissions(application)
            Amber.instance.getDatabase(account.npub).applicationDao().addHistory(
                HistoryEntity2(
                    0,
                    key,
                    signerType.toString(),
                    null,
                    TimeUtils.now(),
                    false,
                ),
            )

            AmberUtils.sendBunkerError(
                intentData,
                account,
                bunkerRequest,
                relays,
                Amber.instance,
                closeApplication = application.application.closeApplication,
                onLoading = onLoading,
                onRemoveIntentData = onRemoveIntentData,
            )
        }
    }
}

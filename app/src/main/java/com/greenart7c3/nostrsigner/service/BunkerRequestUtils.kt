package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.util.Log
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.EncryptionType
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.relays.AmberListenerSingleton
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.ui.RememberType
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.sendAndWaitForResponse
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.collections.toSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

object BunkerRequestUtils {
    val state = MutableStateFlow(listOf<AmberBunkerRequest>())

    fun addRequest(request: AmberBunkerRequest) {
        if (state.value.any { it.request.id == request.request.id }) return
        state.tryEmit(state.value + request)
    }

    fun clearRequests() {
        state.tryEmit(emptyList())
    }

    fun remove(id: String) {
        state.tryEmit(state.value.filter { it.request.id != id })
    }

    fun getBunkerRequests(): List<AmberBunkerRequest> = state.value

    suspend fun sendBunkerResponse(
        context: Context,
        account: Account,
        bunkerRequest: AmberBunkerRequest,
        bunkerResponse: BunkerResponse,
        relays: List<NormalizedRelayUrl>,
        onLoading: (Boolean) -> Unit,
        onDone: (Boolean) -> Unit,
    ) {
        if (relays.isEmpty()) {
            onDone(true)
            onLoading(false)
            Amber.instance.getLogDatabase(account.npub).dao().insertLog(
                LogEntity(
                    id = 0,
                    url = bunkerRequest.localKey,
                    type = "bunker response",
                    message = "No permission created for this client. Please logout from the client and create a new bunker connection",
                    time = System.currentTimeMillis(),
                ),
            )
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

        Amber.instance.applicationIOScope.launch {
            relays.forEach { relay ->
                Amber.instance.getLogDatabase(account.npub).dao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url,
                        type = "bunker response",
                        message = JacksonMapper.mapper.writeValueAsString(bunkerResponse),
                        time = System.currentTimeMillis(),
                    ),
                )
            }
        }

        sendBunkerResponse(
            bunkerResponse = bunkerResponse,
            bunkerRequest = bunkerRequest,
            account = account,
            localKey = bunkerRequest.localKey,
            relays = relays.ifEmpty { Amber.instance.getSavedRelays(account).toList() },
            onLoading = onLoading,
            onDone = onDone,
        )
    }

    private suspend fun sendBunkerResponse(
        bunkerResponse: BunkerResponse,
        bunkerRequest: AmberBunkerRequest,
        account: Account,
        localKey: String,
        relays: List<NormalizedRelayUrl>,
        onLoading: (Boolean) -> Unit,
        onDone: (Boolean) -> Unit,
    ) {
        val plainText = JacksonMapper.mapper.writeValueAsString(bunkerResponse)
        var encryptedContent = if (bunkerRequest.encryptionType == EncryptionType.NIP44) {
            account.nip44Encrypt(
                plainText,
                bunkerRequest.localKey,
            )
        } else {
            account.nip04Encrypt(
                plainText,
                bunkerRequest.localKey,
            )
        }

        var signedEvent = account.signSync<Event>(
            TimeUtils.now(),
            NostrConnectEvent.KIND,
            arrayOf(arrayOf("p", localKey)),
            encryptedContent,
        )

        Log.d(Amber.TAG, "Sending response to relays ${relays.map { relay -> relay.url }} type ${bunkerRequest.request.method}")

        val success = retryWithBackoff {
            val result = Amber.instance.client.sendAndWaitForResponse(
                signedEvent,
                relays.toSet(),
            )
            if (!result) {
                encryptedContent =
                    if (bunkerRequest.encryptionType == EncryptionType.NIP44) {
                        account.nip44Encrypt(plainText, bunkerRequest.localKey)
                    } else {
                        account.nip04Encrypt(plainText, bunkerRequest.localKey)
                    }

                signedEvent = account.signSync(
                    TimeUtils.now(),
                    NostrConnectEvent.KIND,
                    arrayOf(arrayOf("p", localKey)),
                    encryptedContent,
                )
            }
            result
        }

        if (success) {
            Log.d(Amber.TAG, "Success response to relays ${relays.map { relay -> relay.url }} type ${bunkerRequest.request.method}")
            onDone(true)
        } else {
            onDone(false)
            AmberListenerSingleton.showErrorMessage()
            Log.d(Amber.TAG, "Failed response to relays ${relays.map { relay -> relay.url }} type ${bunkerRequest.request.method}")
        }
        onLoading(false)
    }

    suspend fun retryWithBackoff(
        maxRetries: Int = 5,
        initialDelayMs: Long = 200L,
        maxDelayMs: Long = 3_200L,
        block: suspend () -> Boolean,
    ): Boolean {
        var currentDelay = initialDelayMs

        repeat(maxRetries) { attempt ->
            delay(currentDelay)
            if (block()) {
                return true
            }

            if (attempt < maxRetries - 1) {
                currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMs)
            }
        }

        return false
    }

    fun getTypeFromBunker(bunkerRequest: BunkerRequest): SignerType = when (bunkerRequest.method) {
        "sign_message" -> SignerType.SIGN_MESSAGE
        "connect" -> SignerType.CONNECT
        "sign_event" -> SignerType.SIGN_EVENT
        "get_public_key" -> SignerType.GET_PUBLIC_KEY
        "nip04_encrypt" -> SignerType.NIP04_ENCRYPT
        "nip04_decrypt" -> SignerType.NIP04_DECRYPT
        "nip44_encrypt" -> SignerType.NIP44_ENCRYPT
        "nip44_decrypt" -> SignerType.NIP44_DECRYPT
        "decrypt_zap_event" -> SignerType.DECRYPT_ZAP_EVENT
        "ping" -> SignerType.PING
        else -> SignerType.INVALID
    }

    fun getDataFromBunker(bunkerRequest: BunkerRequest): String = when (bunkerRequest.method) {
        "sign_message" -> bunkerRequest.params.first()
        "connect" -> "ack"
        "sign_event" -> {
            val amberEvent = AmberEvent.fromJson(bunkerRequest.params.first())
            amberEvent.toEvent().toJson()
        }
        "nip04_encrypt", "nip04_decrypt", "nip44_encrypt", "nip44_decrypt", "decrypt_zap_event" -> bunkerRequest.params.getOrElse(1) { "" }
        "ping" -> "pong"
        else -> ""
    }

    fun sendResult(
        context: Context,
        account: Account,
        key: String,
        response: String,
        bunkerRequest: AmberBunkerRequest,
        kind: Int?,
        onLoading: (Boolean) -> Unit,
        permissions: List<Permission>? = null,
        appName: String? = null,
        signPolicy: Int? = null,
        shouldCloseApplication: Boolean? = null,
        rememberType: RememberType,
        oldKey: String = "",
        deleteAfter: Long = 0L,
    ) {
        onLoading(true)
        Amber.instance.applicationIOScope.launch {
            val database = Amber.instance.getDatabase(account.npub)
            val historyDatabase = Amber.instance.getHistoryDatabase(account.npub)
            val defaultRelays = Amber.instance.settings.defaultRelays

            if (oldKey.isNotBlank()) {
                database.dao().delete(oldKey)
                historyDatabase.dao().deleteHistory(oldKey)
            }

            var savedApplication = database.dao().getByKey(key)
            if (savedApplication == null && bunkerRequest.request is BunkerRequestConnect && bunkerRequest.request.secret?.isNotBlank() == true) {
                savedApplication = database.dao().getByKey(bunkerRequest.request.secret!!)
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

                    database.dao().delete(bunkerRequest.request.secret!!)
                    database.dao().insertApplicationWithPermissions(tempApp2)
                }
            }
            savedApplication = database.dao().getByKey(key)
            if (bunkerRequest.request is BunkerRequestConnect) {
                savedApplication?.application?.deleteAfter = deleteAfter
            }
            val relays = savedApplication?.application?.relays?.ifEmpty { defaultRelays } ?: bunkerRequest.relays.ifEmpty { defaultRelays }
            val secret = if (bunkerRequest.request is BunkerRequestConnect) bunkerRequest.request.secret ?: "" else ""

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
                        secret,
                        secret.isNotBlank(),
                        account.signPolicy,
                        shouldCloseApplication ?: bunkerRequest.closeApplication,
                        deleteAfter = deleteAfter,
                        lastUsed = TimeUtils.now(),
                    ),
                    permissions = mutableListOf(),
                )
            application.application.isConnected = true

            val activity = Amber.instance.getMainActivity()
            activity?.intent = null
            if (application.application.closeApplication) {
                activity?.finishAndRemoveTask()
            }

            delay(500)

            if (signPolicy != null) {
                AmberUtils.configureSignPolicy(application, signPolicy, key, permissions)
            }

            val type = getTypeFromBunker(bunkerRequest.request)
            if (rememberType != RememberType.NEVER) {
                AmberUtils.acceptPermission(
                    application = application,
                    key = key,
                    type = type,
                    kind = kind,
                    rememberType = rememberType,
                )
            }

            var didChangeRelays = false
            if (type == SignerType.CONNECT) {
                database.dao().deletePermissions(key)
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
                // check if application has any relay not in saved relays
                val savedRelays = Amber.instance.getSavedRelays(account)
                if (relays.any { !savedRelays.contains(it) }) {
                    didChangeRelays = true
                }
            }

            val localBunkerRequest = bunkerRequest.copy()
            clearRequests()

            // assume that everything worked and try to revert it if it fails
            EventNotificationConsumer(context).notificationManager().cancelAll()
            database.dao().insertApplicationWithPermissions(application)
            historyDatabase.dao().addHistory(
                HistoryEntity(
                    0,
                    key,
                    type.toString(),
                    kind,
                    TimeUtils.now(),
                    true,
                ),
                account.npub,
            )
            if (didChangeRelays) {
                Amber.instance.checkForNewRelaysAndUpdateAllFilters(true)
            }
            if (!Amber.instance.client.isActive()) {
                Amber.instance.client.connect()
            }

            sendBunkerResponse(
                context,
                account,
                bunkerRequest,
                BunkerResponse(bunkerRequest.request.id, response, null),
                application.application.relays.ifEmpty { relays },
                onLoading,
                onDone = {
                    Amber.instance.applicationIOScope.launch {
                        if (!it) {
                            if (rememberType != RememberType.NEVER) {
                                if (type == SignerType.SIGN_EVENT) {
                                    kind?.let {
                                        database.dao().deletePermissions(key, type.toString(), kind)
                                    }
                                } else {
                                    if (type == SignerType.CONNECT) {
                                        database.dao().delete(application.application)
                                        if (!bunkerRequest.isNostrConnectUri) {
                                            database.dao().insertApplicationWithPermissions(
                                                application.copy(
                                                    application = application.application.copy(
                                                        isConnected = false,
                                                        key = application.application.secret,
                                                    ),
                                                ),
                                            )
                                        }
                                    } else {
                                        database.dao().deletePermissions(key, type.toString())
                                    }
                                }
                            }

                            onLoading(false)
                            addRequest(localBunkerRequest)
                        }
                    }
                },
            )
        }
    }

    fun sendRejection(
        key: String,
        account: Account,
        bunkerRequest: AmberBunkerRequest,
        appName: String,
        rememberType: RememberType,
        signerType: SignerType,
        kind: Int?,
        onLoading: (Boolean) -> Unit,
    ) {
        onLoading(true)
        Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
            val savedApplication = Amber.instance.getDatabase(account.npub).dao().getByKey(key)
            val defaultRelays = Amber.instance.settings.defaultRelays
            val relays = savedApplication?.application?.relays?.ifEmpty { defaultRelays } ?: bunkerRequest.relays.ifEmpty { defaultRelays }

            val secret = if (bunkerRequest.request is BunkerRequestConnect) bunkerRequest.request.secret ?: "" else ""

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
                        secret,
                        secret.isNotBlank(),
                        account.signPolicy,
                        bunkerRequest.closeApplication,
                        deleteAfter = 0L,
                        lastUsed = TimeUtils.now(),
                    ),
                    permissions = mutableListOf(),
                )

            clearRequests()
            EventNotificationConsumer(Amber.instance).notificationManager().cancelAll()
            val activity = Amber.instance.getMainActivity()
            activity?.intent = null
            if (application.application.closeApplication) {
                activity?.finishAndRemoveTask()
            }

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

            if (bunkerRequest.request !is BunkerRequestConnect) {
                Amber.instance.getDatabase(account.npub).dao().insertApplicationWithPermissions(application)
                Amber.instance.getHistoryDatabase(account.npub).dao().addHistory(
                    HistoryEntity(
                        0,
                        key,
                        signerType.toString(),
                        null,
                        TimeUtils.now(),
                        false,
                    ),
                    account.npub,
                )
            }

            AmberUtils.sendBunkerError(
                account,
                bunkerRequest,
                relays,
                Amber.instance,
                closeApplication = application.application.closeApplication,
                onLoading = onLoading,
            )
        }
    }
}

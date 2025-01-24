package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.fasterxml.jackson.databind.ObjectMapper
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.BunkerRequest
import com.greenart7c3.nostrsigner.models.BunkerResponse
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.EncryptionType
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.containsNip
import com.greenart7c3.nostrsigner.relays.AmberListenerSingleton
import com.greenart7c3.nostrsigner.service.IntentUtils.getUnsignedEvent
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.Channel
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
            NostrSigner.getInstance().applicationIOScope.launch {
                NostrSigner.getInstance().getDatabase(account.signer.keyPair.pubKey.toNpub()).applicationDao().insertLog(
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

        AmberListenerSingleton.getListener()?.let {
            NostrSigner.getInstance().client.unsubscribe(it)
        }
        AmberListenerSingleton.setListener(
            context,
            AmberListenerSingleton.accountStateViewModel,
        )
        NostrSigner.getInstance().client.subscribe(
            AmberListenerSingleton.getListener()!!,
        )

        NostrSigner.getInstance().applicationIOScope.launch {
            relays.forEach { relay ->
                NostrSigner.getInstance().getDatabase(account.signer.keyPair.pubKey.toNpub()).applicationDao().insertLog(
                    LogEntity(
                        id = 0,
                        url = relay.url,
                        type = "bunker response",
                        message = ObjectMapper().writeValueAsString(bunkerResponse),
                        time = System.currentTimeMillis(),
                    ),
                )
            }
        }

        when (bunkerRequest.encryptionType) {
            EncryptionType.NIP04 -> {
                account.signer.nip04Encrypt(
                    ObjectMapper().writeValueAsString(bunkerResponse),
                    bunkerRequest.localKey,
                ) { encryptedContent ->
                    sendBunkerResponse(
                        bunkerRequest = bunkerRequest,
                        account = account,
                        localKey = bunkerRequest.localKey,
                        encryptedContent = encryptedContent,
                        relays = relays.ifEmpty { NostrSigner.getInstance().getSavedRelays().toList() },
                        onLoading = onLoading,
                        onDone = onDone,
                    )
                }
            }
            EncryptionType.NIP44 -> {
                account.signer.nip44Encrypt(
                    ObjectMapper().writeValueAsString(bunkerResponse),
                    bunkerRequest.localKey,
                ) { encryptedContent ->
                    sendBunkerResponse(
                        bunkerRequest = bunkerRequest,
                        account = account,
                        localKey = bunkerRequest.localKey,
                        encryptedContent = encryptedContent,
                        relays = relays.ifEmpty { NostrSigner.getInstance().getSavedRelays().toList() },
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
            NostrSigner.getInstance().applicationIOScope.launch {
                Log.d("IntentUtils", "Sending response to relays ${relays.map { it.url }} type ${bunkerRequest?.method}")

                if (NostrSigner.getInstance().client.getAll().any { !it.isConnected() }) {
                    NostrSigner.getInstance().checkForNewRelays(
                        newRelays = relays.toSet(),
                    )
                }

                val success = NostrSigner.getInstance().client.sendAndWaitForResponse(it, relayList = relays)
                if (success) {
                    Log.d("IntentUtils", "Success response to relays ${relays.map { it.url }} type ${bunkerRequest?.method}")
                    onDone(true)
                } else {
                    onDone(false)
                    Log.d("IntentUtils", "Failed response to relays ${relays.map { it.url }} type ${bunkerRequest?.method}")
                }
                AmberListenerSingleton.getListener()?.let {
                    NostrSigner.getInstance().client.unsubscribe(it)
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
            Log.d("IntentUtils", "Invalid type ${bunkerRequest.method}")
            onReady(null)
            return
        }
        val data = getDataFromBunker(bunkerRequest)
        val account = LocalPreferences.loadFromEncryptedStorage(context, bunkerRequest.currentAccount)!!

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
                        mutableStateOf(false),
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
                            mutableStateOf(false),
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
                        mutableStateOf(false),
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
                        account.signer.keyPair.pubKey.toNpub(),
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
                        mutableStateOf(false),
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
                        mutableStateOf(false),
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
}

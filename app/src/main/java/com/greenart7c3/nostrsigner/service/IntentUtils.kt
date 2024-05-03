package com.greenart7c3.nostrsigner.service

import android.content.Intent
import android.net.Uri
import android.provider.Browser
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.BunkerRequest
import com.greenart7c3.nostrsigner.models.BunkerResponse
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.relays.Client
import com.greenart7c3.nostrsigner.relays.Relay
import com.greenart7c3.nostrsigner.relays.RelayPool
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.ui.NotificationType
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.Event
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class BunkerMetadata(
    val name: String,
    val url: String,
    val description: String,
)

object IntentUtils {
    val bunkerRequests = ConcurrentHashMap<String, BunkerRequest>()

    private fun getIntentDataWithoutExtras(
        data: String,
        intent: Intent,
        packageName: String?,
        route: String?,
        account: Account,
        onReady: (IntentData?) -> Unit,
    ) {
        val localData = URLDecoder.decode(data.replace("nostrsigner:", "").split("?").first().replace("+", "%2b"), "utf-8")
        val parameters = data.replace("nostrsigner:", "").split("?").toMutableList()
        parameters.removeFirst()

        if (parameters.isEmpty() || parameters.toString() == "[]") {
            getIntentDataFromIntent(intent, packageName, route, account, onReady)
        } else {
            var type = SignerType.SIGN_EVENT
            var pubKey = ""
            var compressionType = CompressionType.NONE
            var callbackUrl: String? = null
            var returnType = ReturnType.SIGNATURE
            parameters.joinToString("?").split("&").forEach {
                val params = it.split("=").toMutableList()
                val parameter = params.removeFirst()
                val parameterData = params.joinToString("=")
                if (parameter == "type") {
                    type =
                        when (parameterData) {
                            "sign_event" -> SignerType.SIGN_EVENT
                            "get_public_key" -> SignerType.GET_PUBLIC_KEY
                            "nip04_encrypt" -> SignerType.NIP04_ENCRYPT
                            "nip04_decrypt" -> SignerType.NIP04_DECRYPT
                            "nip44_encrypt" -> SignerType.NIP44_ENCRYPT
                            "nip44_decrypt" -> SignerType.NIP44_DECRYPT
                            else -> SignerType.SIGN_EVENT
                        }
                }
                if (parameter.toLowerCase(Locale.current) == "pubkey") {
                    pubKey = parameterData
                }
                if (parameter == "compressionType") {
                    if (parameterData == "gzip") {
                        compressionType = CompressionType.GZIP
                    }
                }
                if (parameter == "callbackUrl") {
                    callbackUrl = parameterData
                }
                if (parameter == "returnType") {
                    if (parameterData == "event") {
                        returnType = ReturnType.EVENT
                    }
                }
            }

            when (type) {
                SignerType.SIGN_EVENT -> {
                    val unsignedEvent = getUnsignedEvent(localData, account)
                    var localAccount = account
                    if (unsignedEvent.pubKey != account.keyPair.pubKey.toHexKey()) {
                        LocalPreferences.loadFromEncryptedStorage(Hex.decode(unsignedEvent.pubKey).toNpub())?.let {
                            localAccount = it
                        }
                    }
                    localAccount.signer.sign<Event>(
                        unsignedEvent.createdAt,
                        unsignedEvent.kind,
                        unsignedEvent.tags,
                        unsignedEvent.content,
                    ) {
                        onReady(
                            IntentData(
                                localData,
                                "",
                                type,
                                pubKey,
                                "",
                                callbackUrl,
                                compressionType,
                                returnType,
                                listOf(),
                                Hex.decode(it.pubKey).toNpub(),
                                mutableStateOf(true),
                                mutableStateOf(false),
                                null,
                                route,
                                it,
                                null,
                            ),
                        )
                    }
                }
                SignerType.NIP04_ENCRYPT, SignerType.NIP04_DECRYPT, SignerType.NIP44_ENCRYPT, SignerType.NIP44_DECRYPT -> {
                    val result =
                        try {
                            AmberUtils.encryptOrDecryptData(
                                localData,
                                type,
                                account,
                                pubKey,
                            ) ?: "Could not decrypt the message"
                        } catch (e: Exception) {
                            "Could not decrypt the message"
                        }

                    onReady(
                        IntentData(
                            localData,
                            "",
                            type,
                            pubKey,
                            "",
                            callbackUrl,
                            compressionType,
                            returnType,
                            listOf(),
                            Hex.decode(pubKey).toNpub(),
                            mutableStateOf(true),
                            mutableStateOf(false),
                            null,
                            route,
                            null,
                            result,
                        ),
                    )
                }
                SignerType.GET_PUBLIC_KEY -> {
                    onReady(
                        IntentData(
                            localData,
                            "",
                            type,
                            pubKey,
                            "",
                            callbackUrl,
                            compressionType,
                            returnType,
                            listOf(),
                            "",
                            mutableStateOf(true),
                            mutableStateOf(false),
                            null,
                            route,
                            null,
                            null,
                        ),
                    )
                }
                else -> onReady(null)
            }
        }
    }

    fun getTypeFromBunker(bunkerRequest: BunkerRequest): SignerType {
        return when (bunkerRequest.method) {
            "connect" -> SignerType.CONNECT
            "sign_event" -> SignerType.SIGN_EVENT
            "get_public_key" -> SignerType.GET_PUBLIC_KEY
            "nip04_encrypt" -> SignerType.NIP04_ENCRYPT
            "nip04_decrypt" -> SignerType.NIP04_DECRYPT
            "nip44_encrypt" -> SignerType.NIP44_ENCRYPT
            "nip44_decrypt" -> SignerType.NIP44_DECRYPT
            else -> SignerType.SIGN_EVENT
        }
    }

    fun getDataFromBunker(bunkerRequest: BunkerRequest): String {
        return when (bunkerRequest.method) {
            "connect" -> "ack"
            "sign_event" -> {
                val amberEvent = AmberEvent.fromJson(bunkerRequest.params.first())
                AmberEvent.toEvent(amberEvent).toJson()
            }
            "nip04_encrypt", "nip04_decrypt", "nip44_encrypt", "nip44_decrypt" -> bunkerRequest.params.getOrElse(1) { "" }
            else -> ""
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun sendBunkerResponse(
        account: Account,
        localKey: String,
        bunkerResponse: BunkerResponse,
        relays: List<Relay>,
        onLoading: (Boolean) -> Unit,
        onSign: (() -> Unit)? = null,
        onDone: () -> Unit,
    ) {
        account.signer.nip04Encrypt(
            ObjectMapper().writeValueAsString(bunkerResponse),
            localKey,
        ) { encryptedContent ->
            account.signer.sign<Event>(
                TimeUtils.now(),
                24133,
                arrayOf(arrayOf("p", localKey)),
                encryptedContent,
            ) {
                if (onSign != null) {
                    onSign()
                }

                GlobalScope.launch(Dispatchers.IO) {
                    if (RelayPool.getAll().isEmpty()) {
                        val database = NostrSigner.instance.getDatabase(account.keyPair.pubKey.toNpub())
                        val savedRelays = mutableListOf<Relay>()
                        database.applicationDao().getAllApplications().forEach {
                            it.application.relays.forEach { url ->
                                if (url.isNotBlank()) {
                                    if (!savedRelays.any { relay -> relay.url == url }) {
                                        savedRelays.add(Relay(url))
                                    }
                                }
                            }
                        }
                        Client.addRelays(relays.toTypedArray())
                        if (LocalPreferences.getNotificationType() == NotificationType.DIRECT) {
                            Client.reconnect(relays.toTypedArray())
                        }
                        delay(1000)
                    }

                    Client.send(it, relayList = relays, onDone = onDone, onLoading = onLoading)
                }
            }
        }
    }

    private fun getIntentDataFromBunkerRequest(
        intent: Intent,
        bunkerRequest: BunkerRequest,
        route: String?,
        onReady: (IntentData?) -> Unit,
    ) {
        val type = getTypeFromBunker(bunkerRequest)
        val data = getDataFromBunker(bunkerRequest)
        val account = LocalPreferences.loadFromEncryptedStorage(bunkerRequest.currentAccount)!!

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
                        account.keyPair.pubKey.toNpub(),
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
        }
    }

    private fun getIntentDataFromIntent(
        intent: Intent,
        packageName: String?,
        route: String?,
        account: Account,
        onReady: (IntentData?) -> Unit,
    ) {
        val type =
            when (intent.extras?.getString("type")) {
                "sign_event" -> SignerType.SIGN_EVENT
                "nip04_encrypt" -> SignerType.NIP04_ENCRYPT
                "nip04_decrypt" -> SignerType.NIP04_DECRYPT
                "nip44_decrypt" -> SignerType.NIP44_DECRYPT
                "nip44_encrypt" -> SignerType.NIP44_ENCRYPT
                "get_public_key" -> SignerType.GET_PUBLIC_KEY
                "decrypt_zap_event" -> SignerType.DECRYPT_ZAP_EVENT
                else -> SignerType.SIGN_EVENT
            }

        val data =
            try {
                if (packageName == null) {
                    URLDecoder.decode(intent.data?.toString()?.replace("+", "%2b") ?: "", "utf-8").replace("nostrsigner:", "")
                } else {
                    intent.data?.toString()?.replace("nostrsigner:", "") ?: ""
                }
            } catch (e: Exception) {
                intent.data?.toString()?.replace("nostrsigner:", "") ?: ""
            }

        val callbackUrl = intent.extras?.getString("callbackUrl") ?: ""
        val name = if (callbackUrl.isNotBlank()) Uri.parse(callbackUrl).host ?: "" else ""
        val pubKey = intent.extras?.getString("pubKey") ?: ""
        val id = intent.extras?.getString("id") ?: ""

        val compressionType = if (intent.extras?.getString("compression") == "gzip") CompressionType.GZIP else CompressionType.NONE
        val returnType = if (intent.extras?.getString("returnType") == "event") ReturnType.EVENT else ReturnType.SIGNATURE
        val listType = object : TypeToken<List<Permission>>() {}.type
        val permissions = Gson().fromJson<List<Permission>?>(intent.extras?.getString("permissions"), listType)
        permissions?.forEach {
            it.checked = true
        }

        when (type) {
            SignerType.SIGN_EVENT -> {
                val unsignedEvent = getUnsignedEvent(data, account)
                var localAccount = account
                if (unsignedEvent.pubKey != account.keyPair.pubKey.toHexKey()) {
                    LocalPreferences.loadFromEncryptedStorage(Hex.decode(unsignedEvent.pubKey).toNpub())?.let {
                        localAccount = it
                    }
                }

                localAccount.signer.sign<Event>(
                    unsignedEvent.createdAt,
                    unsignedEvent.kind,
                    unsignedEvent.tags,
                    unsignedEvent.content,
                ) {
                    onReady(
                        IntentData(
                            data,
                            name,
                            type,
                            pubKey,
                            id,
                            intent.extras?.getString("callbackUrl"),
                            compressionType,
                            returnType,
                            permissions,
                            intent.extras?.getString("current_user") ?: Hex.decode(it.pubKey).toNpub(),
                            mutableStateOf(true),
                            mutableStateOf(false),
                            null,
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
                        name,
                        type,
                        pubKey,
                        id,
                        intent.extras?.getString("callbackUrl"),
                        compressionType,
                        returnType,
                        permissions,
                        intent.extras?.getString("current_user") ?: Hex.decode(pubKey).toNpub(),
                        mutableStateOf(true),
                        mutableStateOf(false),
                        null,
                        route,
                        null,
                        result,
                    ),
                )
            }
            SignerType.GET_PUBLIC_KEY -> {
                onReady(
                    IntentData(
                        data,
                        name,
                        type,
                        pubKey,
                        id,
                        intent.extras?.getString("callbackUrl"),
                        compressionType,
                        returnType,
                        permissions,
                        intent.extras?.getString("current_user") ?: Hex.decode(pubKey).toNpub(),
                        mutableStateOf(true),
                        mutableStateOf(false),
                        null,
                        route,
                        null,
                        null,
                    ),
                )
            }
            else -> onReady(null)
        }
    }

    private fun metaDataFromJson(json: String): BunkerMetadata {
        val objectMapper =
            jacksonObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        return objectMapper.readValue(json, BunkerMetadata::class.java)
    }

    private fun getIntentFromNostrConnect(
        intent: Intent,
        route: String?,
        account: Account,
        onReady: (IntentData?) -> Unit,
    ) {
        try {
            val data = intent.dataString.toString().replace("nostrconnect://", "")
            val split = data.split("?")
            val relays: MutableList<String> = mutableListOf()
            var name = ""
            val pubKey = split.first()
            val parsedData = URLDecoder.decode(split.drop(1).joinToString { it }.replace("+", "%2b"), "utf-8")
            val splitParsedData = parsedData.split("&")
            splitParsedData.forEach {
                val internalSplit = it.split("=")
                if (internalSplit.first() == "relay") {
                    relays.add(internalSplit[1])
                }
                if (internalSplit.first() == "name") {
                    name = internalSplit[1]
                }
                if (internalSplit.first() == "metadata") {
                    val bunkerMetada = metaDataFromJson(internalSplit[1])
                    name = bunkerMetada.name
                }
            }

            onReady(
                IntentData(
                    "ack",
                    name,
                    SignerType.CONNECT,
                    pubKey,
                    "",
                    null,
                    CompressionType.NONE,
                    ReturnType.EVENT,
                    listOf(),
                    "",
                    mutableStateOf(true),
                    mutableStateOf(false),
                    BunkerRequest(
                        UUID.randomUUID().toString().substring(0, 4),
                        "connect",
                        arrayOf(pubKey),
                        pubKey,
                        relays,
                        "",
                        account.keyPair.pubKey.toNpub(),
                    ),
                    route,
                    null,
                    null,
                ),
            )
        } catch (e: Exception) {
            Log.e("nostrconnect", e.message, e)
            onReady(null)
        }
    }

    fun getIntentData(
        intent: Intent,
        packageName: String?,
        route: String?,
        currentLoggedInAccount: Account,
        onReady: (IntentData?) -> Unit,
    ) {
        if (intent.data == null) {
            onReady(
                null,
            )
            return
        }

        val bunkerRequest =
            if (intent.getStringExtra("bunker") != null) {
                BunkerRequest.mapper.readValue(
                    intent.getStringExtra("bunker"),
                    BunkerRequest::class.java,
                )
            } else {
                null
            }

        var localAccount = currentLoggedInAccount
        if (bunkerRequest != null) {
            LocalPreferences.loadFromEncryptedStorage(bunkerRequest.currentAccount)?.let {
                localAccount = it
            }
        } else if (intent.getStringExtra("current_user") != null) {
            LocalPreferences.loadFromEncryptedStorage(intent.getStringExtra("current_user"))?.let {
                localAccount = it
            }
        }

        if (intent.dataString?.startsWith("nostrconnect:") == true) {
            getIntentFromNostrConnect(intent, route, localAccount, onReady)
        } else if (bunkerRequest != null) {
            getIntentDataFromBunkerRequest(intent, bunkerRequest, route, onReady)
        } else if (intent.extras?.getString(Browser.EXTRA_APPLICATION_ID) == null) {
            getIntentDataFromIntent(intent, packageName, route, localAccount, onReady)
        } else {
            getIntentDataWithoutExtras(intent.data?.toString() ?: "", intent, packageName, route, localAccount, onReady)
        }
    }

    private fun getUnsignedEvent(
        data: String,
        account: Account,
    ): Event {
        val event = AmberEvent.fromJson(data)
        if (event.pubKey.isEmpty()) {
            event.pubKey = account.keyPair.pubKey.toHexKey()
        }
        if (event.id.isEmpty()) {
            event.id =
                Event.generateId(
                    event.pubKey,
                    event.createdAt,
                    event.kind,
                    event.tags,
                    event.content,
                ).toHexKey()
        }

        return AmberEvent.toEvent(event)
    }
}

package com.greenart7c3.nostrsigner.service

import android.content.Intent
import android.net.Uri
import android.provider.Browser
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.nostrsigner
import com.greenart7c3.nostrsigner.relays.Client
import com.greenart7c3.nostrsigner.relays.Relay
import com.greenart7c3.nostrsigner.relays.RelayPool
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.ui.BunkerResponse
import com.greenart7c3.nostrsigner.ui.NotificationType
import com.vitorpamplona.quartz.crypto.KeyPair
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.Event
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class BunkerMetada(
    val name: String,
    val url: String,
    val description: String
)

object IntentUtils {
    val bunkerRequests = ConcurrentHashMap<String, BunkerRequest>()

    private fun getIntentDataWithoutExtras(data: String, intent: Intent, packageName: String?, route: String?): IntentData {
        val localData = URLDecoder.decode(data.replace("nostrsigner:", "").split("?").first().replace("+", "%2b"), "utf-8")
        val parameters = data.replace("nostrsigner:", "").split("?").toMutableList()
        parameters.removeFirst()

        if (parameters.isEmpty() || parameters.toString() == "[]") {
            return getIntentDataFromIntent(intent, packageName, route)
        }

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
                type = when (parameterData) {
                    "sign_event" -> SignerType.SIGN_EVENT
                    "get_public_key" -> SignerType.GET_PUBLIC_KEY
                    "nip04_encrypt" -> SignerType.NIP04_ENCRYPT
                    "nip04_decrypt" -> SignerType.NIP04_DECRYPT
                    "nip44_encrypt" -> SignerType.NIP44_ENCRYPT
                    "nip44_decrypt" -> SignerType.NIP44_DECRYPT
                    else -> SignerType.SIGN_EVENT
                }
            }
            if (parameter == "pubkey") {
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

        return IntentData(
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
            route
        )
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
        onDone: () -> Unit
    ) {
        account.signer.nip04Encrypt(
            ObjectMapper().writeValueAsString(bunkerResponse),
            localKey
        ) { encryptedContent ->
            account.signer.sign<Event>(
                TimeUtils.now(),
                24133,
                arrayOf(arrayOf("p", localKey)),
                encryptedContent
            ) {
                if (onSign != null) {
                    onSign()
                }

                GlobalScope.launch(Dispatchers.IO) {
                    if (RelayPool.getAll().isEmpty()) {
                        val database = nostrsigner.instance.getDatabase(account.keyPair.pubKey.toNpub())
                        val savedRelays = mutableListOf<Relay>()
                        database.applicationDao().getAllApplications().forEach {
                            it.application.relays.forEach { url ->
                                if (url.isNotBlank()) {
                                    if (!savedRelays.any { it.url == url }) {
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

    private fun getIntentDataFromBunkerRequest(intent: Intent, bunkerRequest: BunkerRequest, route: String?): IntentData {
        val type = getTypeFromBunker(bunkerRequest)
        val data = getDataFromBunker(bunkerRequest)

        val pubKey = if (bunkerRequest.method.endsWith("encrypt") || bunkerRequest.method.endsWith("decrypt")) {
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
                val kind = try {
                    split2[1].toInt()
                } catch (_: Exception) {
                    null
                }

                permissions.add(
                    Permission(
                        permissionType,
                        kind
                    )
                )
            }
        }

        return IntentData(
            data,
            "",
            type,
            pubKey,
            id,
            intent.extras?.getString("callbackUrl"),
            CompressionType.NONE,
            ReturnType.EVENT,
            permissions,
            intent.extras?.getString("current_user") ?: "",
            mutableStateOf(true),
            mutableStateOf(false),
            bunkerRequest,
            route
        )
    }

    private fun getIntentDataFromIntent(intent: Intent, packageName: String?, route: String?): IntentData {
        val type = when (intent.extras?.getString("type")) {
            "sign_event" -> SignerType.SIGN_EVENT
            "nip04_encrypt" -> SignerType.NIP04_ENCRYPT
            "nip04_decrypt" -> SignerType.NIP04_DECRYPT
            "nip44_decrypt" -> SignerType.NIP44_DECRYPT
            "nip44_encrypt" -> SignerType.NIP44_ENCRYPT
            "get_public_key" -> SignerType.GET_PUBLIC_KEY
            "decrypt_zap_event" -> SignerType.DECRYPT_ZAP_EVENT
            else -> SignerType.SIGN_EVENT
        }

        val data = try {
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

        return IntentData(
            data,
            name,
            type,
            pubKey,
            id,
            intent.extras?.getString("callbackUrl"),
            compressionType,
            returnType,
            permissions,
            intent.extras?.getString("current_user") ?: "",
            mutableStateOf(true),
            mutableStateOf(false),
            null,
            route
        )
    }

    private fun metaDataFromJson(json: String): BunkerMetada {
        val objectMapper = jacksonObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        return objectMapper.readValue(json, BunkerMetada::class.java)
    }

    private fun getIntentFromNostrConnect(intent: Intent, route: String?): IntentData? {
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

            return IntentData(
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
                    ""
                ),
                route
            )
        } catch (e: Exception) {
            Log.e("nostrconnect", e.message, e)
            return null
        }
    }

    fun getIntentData(intent: Intent, packageName: String?, route: String?): IntentData? {
        if (intent.data == null) {
            return null
        }

        val bunkerRequest = if (intent.getStringExtra("bunker") != null) {
            BunkerRequest.mapper.readValue(
                intent.getStringExtra("bunker"),
                BunkerRequest::class.java
            )
        } else {
            null
        }

        return if (intent.dataString?.startsWith("nostrconnect:") == true) {
            return getIntentFromNostrConnect(intent, route)
        } else if (bunkerRequest != null) {
            getIntentDataFromBunkerRequest(intent, bunkerRequest, route)
        } else if (intent.extras?.getString(Browser.EXTRA_APPLICATION_ID) == null) {
            getIntentDataFromIntent(intent, packageName, route)
        } else {
            getIntentDataWithoutExtras(intent.data?.toString() ?: "", intent, packageName, route)
        }
    }
    fun getIntent(data: String, keyPair: KeyPair): Event {
        val event = AmberEvent.fromJson(data)
        if (event.pubKey.isEmpty()) {
            event.pubKey = keyPair.pubKey.toHexKey()
        }
        if (event.id.isEmpty()) {
            event.id = Event.generateId(
                event.pubKey,
                event.createdAt,
                event.kind,
                event.tags,
                event.content
            ).toHexKey()
        }

        return AmberEvent.toEvent(event)
    }
}

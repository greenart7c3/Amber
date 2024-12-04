package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Browser
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.models.containsNip
import com.greenart7c3.nostrsigner.relays.AmberListenerSingleton
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.Client
import com.vitorpamplona.ammolite.relays.RelayPool
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.Event
import fr.acinq.secp256k1.Hex
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

data class BunkerMetadata(
    val name: String,
    val url: String,
    val description: String,
    val perms: String,
) {
    companion object {
        val mapper: ObjectMapper =
            jacksonObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .registerModule(
                    SimpleModule()
                        .addDeserializer(BunkerMetadata::class.java, BunkerMetadataDeserializer()),
                )

        fun fromJson(jsonObject: JsonNode): BunkerMetadata {
            return BunkerMetadata(
                name = jsonObject.get("name")?.asText()?.intern() ?: "",
                url = jsonObject.get("url")?.asText()?.intern() ?: "",
                description = jsonObject.get("description")?.asText()?.intern() ?: "",
                perms = jsonObject.get("perms")?.asText()?.intern() ?: "",
            )
        }

        private class BunkerMetadataDeserializer : StdDeserializer<BunkerMetadata>(BunkerMetadata::class.java) {
            override fun deserialize(
                jp: JsonParser,
                ctxt: DeserializationContext,
            ): BunkerMetadata {
                return fromJson(jp.codec.readTree(jp))
            }
        }
    }
}

object IntentUtils {
    val state = Channel<String>(Channel.CONFLATED)
    private val bunkerRequests = ConcurrentHashMap<String, BunkerRequest>()

    fun addRequest(key: String, request: BunkerRequest) {
        bunkerRequests[key] = request
        state.trySend("")
    }

    fun clearRequests() {
        bunkerRequests.clear()
        state.trySend("")
    }

    fun getBunkerRequests(): Map<String, BunkerRequest> {
        return bunkerRequests
    }

    private fun getIntentDataWithoutExtras(
        context: Context,
        data: String,
        intent: Intent,
        packageName: String?,
        route: String?,
        account: Account,
        onReady: (IntentData?) -> Unit,
    ) {
        val content: String
        if (data.contains("?iv")) {
            val splitData = data.replace("nostrsigner:", "").split("?")
            content = splitData[0] + "?" + splitData[1]
        } else {
            content = ""
        }
        val localData = content.ifEmpty { URLDecoder.decode(data.replace("nostrsigner:", "").split("?").first().replace("+", "%2b"), "utf-8") }
        val parameters = if (content.isEmpty()) data.replace("nostrsigner:", "").split("?").toMutableList() else data.replace(content, "").replace("nostrsigner:", "").split("?").toMutableList()
        parameters.removeAt(0)
        parameters.removeIf { it.isBlank() }

        if (parameters.isEmpty() || parameters.toString() == "[]") {
            getIntentDataFromIntent(context, intent, packageName, route, account, onReady)
        } else {
            var type = SignerType.SIGN_EVENT
            var pubKey = ""
            var compressionType = CompressionType.NONE
            var callbackUrl: String? = null
            var returnType = ReturnType.SIGNATURE
            parameters.joinToString("?").split("&").forEach {
                val params = it.split("=").toMutableList()
                val parameter = params.removeAt(0)
                val parameterData = params.joinToString("=")
                if (parameter == "type") {
                    type =
                        when (parameterData) {
                            "sign_message" -> SignerType.SIGN_MESSAGE
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
                    if (unsignedEvent.pubKey != account.signer.keyPair.pubKey.toHexKey()) {
                        LocalPreferences.loadFromEncryptedStorage(context, Hex.decode(unsignedEvent.pubKey).toNpub())?.let {
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
                SignerType.SIGN_MESSAGE -> {
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
            "sign_message" -> SignerType.SIGN_MESSAGE
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

    fun sendBunkerResponse(
        context: Context,
        account: Account,
        bunkerRequest: BunkerRequest,
        bunkerResponse: BunkerResponse,
        relays: List<RelaySetupInfo>,
        onLoading: (Boolean) -> Unit,
        onDone: () -> Unit,
    ) {
        AmberListenerSingleton.getListener()?.let {
            Client.unsubscribe(it)
        }
        AmberListenerSingleton.setListener(
            context,
            AmberListenerSingleton.accountStateViewModel,
        )
        Client.subscribe(
            AmberListenerSingleton.getListener()!!,
        )

        when (bunkerRequest.encryptionType) {
            EncryptionType.NIP04 -> {
                account.signer.nip04Encrypt(
                    ObjectMapper().writeValueAsString(bunkerResponse),
                    bunkerRequest.localKey,
                ) { encryptedContent ->
                    sendBunkerResponse(
                        account,
                        bunkerRequest.localKey,
                        encryptedContent,
                        relays.ifEmpty { NostrSigner.getInstance().getSavedRelays().toList() },
                        onLoading,
                        onDone,
                    )
                }
            }
            EncryptionType.NIP44 -> {
                account.signer.nip44Encrypt(
                    ObjectMapper().writeValueAsString(bunkerResponse),
                    bunkerRequest.localKey,
                ) { encryptedContent ->
                    sendBunkerResponse(
                        account,
                        bunkerRequest.localKey,
                        encryptedContent,
                        relays.ifEmpty { NostrSigner.getInstance().getSavedRelays().toList() },
                        onLoading,
                        onDone,
                    )
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun sendBunkerResponse(
        account: Account,
        localKey: String,
        encryptedContent: String,
        relays: List<RelaySetupInfo>,
        onLoading: (Boolean) -> Unit,
        onDone: () -> Unit,
    ) {
        account.signer.sign<Event>(
            TimeUtils.now(),
            24133,
            arrayOf(arrayOf("p", localKey)),
            encryptedContent,
        ) {
            GlobalScope.launch(Dispatchers.IO) {
                Log.d("IntentUtils", "Sending response to relays ${relays.map { it.url }}")

                if (RelayPool.getAll().any { !it.isConnected() }) {
                    NostrSigner.getInstance().checkForNewRelays(
                        newRelays = relays.toSet(),
                    )
                }

                val success = Client.sendAndWaitForResponse(it, relayList = relays)
                if (success) {
                    Log.d("IntentUtils", "Success response to relays ${relays.map { it.url }}")
                    onDone()
                } else {
                    Log.d("IntentUtils", "Failed response to relays ${relays.map { it.url }}")
                }
                AmberListenerSingleton.getListener()?.let {
                    Client.unsubscribe(it)
                }
                onLoading(false)
            }
        }
    }

    private fun getIntentDataFromBunkerRequest(
        context: Context,
        intent: Intent,
        bunkerRequest: BunkerRequest,
        route: String?,
        onReady: (IntentData?) -> Unit,
    ) {
        val type = getTypeFromBunker(bunkerRequest)
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
        }
    }

    private fun getIntentDataFromIntent(
        context: Context,
        intent: Intent,
        packageName: String?,
        route: String?,
        account: Account,
        onReady: (IntentData?) -> Unit,
    ) {
        val type =
            when (intent.extras?.getString("type")) {
                "sign_message" -> SignerType.SIGN_MESSAGE
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
        val pubKey = intent.extras?.getString("pubKey") ?: intent.extras?.getString("pubkey") ?: ""
        val id = intent.extras?.getString("id") ?: ""

        val compressionType = if (intent.extras?.getString("compression") == "gzip") CompressionType.GZIP else CompressionType.NONE
        val returnType = if (intent.extras?.getString("returnType") == "event") ReturnType.EVENT else ReturnType.SIGNATURE
        val listType = object : TypeToken<MutableList<Permission>>() {}.type
        val permissions = Gson().fromJson<MutableList<Permission>?>(intent.extras?.getString("permissions"), listType)
        permissions?.forEach {
            it.checked = true
        }
        permissions?.removeIf { it.kind == null && (it.type == "sign_event" || it.type == "nip") }
        permissions?.removeIf { it.type == "nip" && (it.kind == null || !it.kind.containsNip()) }

        when (type) {
            SignerType.SIGN_EVENT -> {
                val unsignedEvent = getUnsignedEvent(data, account)
                var localAccount = account
                if (unsignedEvent.pubKey != account.signer.keyPair.pubKey.toHexKey()) {
                    LocalPreferences.loadFromEncryptedStorage(context, Hex.decode(unsignedEvent.pubKey).toNpub())?.let {
                        localAccount = it
                    }
                }

                localAccount.signer.sign<Event>(
                    unsignedEvent.createdAt,
                    unsignedEvent.kind,
                    unsignedEvent.tags,
                    unsignedEvent.content,
                ) {
                    var npub = intent.getStringExtra("current_user")
                    if (npub != null) {
                        npub = parsePubKey(npub!!)
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
                            npub ?: Hex.decode(it.pubKey).toNpub(),
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

                var npub = intent.getStringExtra("current_user")
                if (npub != null) {
                    npub = parsePubKey(npub!!)
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
                        npub ?: Hex.decode(pubKey).toNpub(),
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
                var npub = intent.getStringExtra("current_user")
                if (npub != null) {
                    npub = parsePubKey(npub!!)
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
                        npub ?: Hex.decode(pubKey).toNpub(),
                        mutableStateOf(true),
                        mutableStateOf(false),
                        null,
                        route,
                        null,
                        null,
                    ),
                )
            }
            SignerType.SIGN_MESSAGE -> {
                var npub = intent.getStringExtra("current_user")
                if (npub != null) {
                    npub = parsePubKey(npub!!)
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
                        npub ?: Hex.decode(pubKey).toNpub(),
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

    fun parsePubKey(key: String): String? {
        if (key.startsWith("npub1")) {
            return key
        }
        return try {
            Hex.decode(key).toNpub()
        } catch (e: Exception) {
            null
        }
    }

    private fun metaDataFromJson(json: String): BunkerMetadata {
        return BunkerMetadata.mapper.readValue(json, BunkerMetadata::class.java)
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
            val relays: MutableList<RelaySetupInfo> = mutableListOf()
            var name = ""
            val pubKey = split.first()
            val parsedData = URLDecoder.decode(split.drop(1).joinToString { it }.replace("+", "%2b"), "utf-8")
            val splitParsedData = parsedData.split("&")
            val permissions = mutableListOf<Permission>()
            var nostrConnectSecret = ""
            splitParsedData.forEach {
                val internalSplit = it.split("=")
                val paramName = internalSplit.first()
                val json = internalSplit.mapIndexedNotNull { index, s ->
                    if (index == 0) null else s
                }.joinToString { data -> data }
                if (paramName == "relay") {
                    var relayUrl = json
                    if (relayUrl.endsWith("/")) relayUrl = relayUrl.dropLast(1)
                    relays.add(
                        RelaySetupInfo(relayUrl, read = true, write = true, feedTypes = COMMON_FEED_TYPES),
                    )
                }
                if (paramName == "name") {
                    name = json
                }

                if (paramName == "secret") {
                    nostrConnectSecret = json
                }

                if (paramName == "perms") {
                    if (json.isNotEmpty()) {
                        val splitPerms = json.split(",")
                        splitPerms.forEach { perm ->
                            val split2 = perm.split(":")
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

                if (paramName == "metadata") {
                    val bunkerMetada = metaDataFromJson(json)
                    name = bunkerMetada.name
                    if (bunkerMetada.perms.isNotEmpty()) {
                        val splitPerms = bunkerMetada.perms.split(",")
                        splitPerms.forEach { perm ->
                            val split2 = perm.split(":")
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
            }

            permissions.removeIf { it.kind == null && (it.type == "sign_event" || it.type == "nip") }
            permissions.removeIf { it.type == "nip" && (it.kind == null || !it.kind.containsNip()) }

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
                    permissions,
                    "",
                    mutableStateOf(true),
                    mutableStateOf(false),
                    BunkerRequest(
                        UUID.randomUUID().toString().substring(0, 4),
                        "connect",
                        arrayOf(pubKey),
                        pubKey,
                        relays.ifEmpty { NostrSigner.getInstance().getSavedRelays().toList() },
                        "",
                        account.signer.keyPair.pubKey.toNpub(),
                        EncryptionType.NIP04,
                        nostrConnectSecret,
                        intent.getBooleanExtra("closeApplication", true),
                    ),
                    route,
                    null,
                    null,
                ),
            )
        } catch (e: Exception) {
            Log.e("nostrconnect", e.message, e)
            NostrSigner.getInstance().getDatabase(account.signer.keyPair.pubKey.toNpub()).applicationDao().insertLog(
                LogEntity(
                    0,
                    "nostrconnect",
                    "nostrconnect",
                    e.message ?: "",
                    System.currentTimeMillis(),
                ),
            )
            onReady(null)
        }
    }

    fun getIntentData(
        context: Context,
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
            LocalPreferences.loadFromEncryptedStorage(context, bunkerRequest.currentAccount)?.let {
                localAccount = it
            }
        } else if (intent.getStringExtra("current_user") != null) {
            var npub = intent.getStringExtra("current_user")
            if (npub != null) {
                npub = parsePubKey(npub)
            }
            LocalPreferences.loadFromEncryptedStorage(context, npub)?.let {
                localAccount = it
            }
        }

        if (intent.dataString?.startsWith("nostrconnect:") == true) {
            getIntentFromNostrConnect(intent, route, localAccount, onReady)
        } else if (bunkerRequest != null) {
            getIntentDataFromBunkerRequest(context, intent, bunkerRequest, route, onReady)
        } else if (intent.extras?.getString(Browser.EXTRA_APPLICATION_ID) == null) {
            getIntentDataFromIntent(context, intent, packageName, route, localAccount, onReady)
        } else {
            getIntentDataWithoutExtras(context, intent.data?.toString() ?: "", intent, packageName, route, localAccount, onReady)
        }
    }

    fun getUnsignedEvent(
        data: String,
        account: Account,
    ): Event {
        val event = AmberEvent.fromJson(data)
        if (event.pubKey.isEmpty()) {
            event.pubKey = account.signer.keyPair.pubKey.toHexKey()
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

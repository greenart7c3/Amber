package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Browser
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
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.BunkerRequest
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.containsNip
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.Event
import fr.acinq.secp256k1.Hex
import java.net.URLDecoder

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
    fun decodeData(data: String, replace: Boolean = true): String {
        if (!replace) return URLDecoder.decode(data.replace("nostrsigner:", ""), "utf-8")
        return URLDecoder.decode(data.replace("nostrsigner:", "").replace("+", "%2b"), "utf-8")
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
        val decoded = decodeData(data)
        val localData = content.ifEmpty { decoded.split("?").first() }
        val parameters = if (content.isEmpty()) decoded.split("?").toMutableList() else decodeData(content, false).split("?").toMutableList()
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
            var appName = ""
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
                if (parameter == "appName") {
                    appName = parameterData
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
                                appName,
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
                            appName,
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
                            appName,
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
                            appName,
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
                decodeData(intent.data?.toString() ?: "", packageName == null)
            } catch (e: Exception) {
                intent.data?.toString()?.replace("nostrsigner:", "") ?: ""
            }

        val callbackUrl = intent.extras?.getString("callbackUrl") ?: ""
        var name = if (callbackUrl.isNotBlank()) Uri.parse(callbackUrl).host ?: "" else ""
        if (name.isBlank()) {
            name = intent.extras?.getString("appName") ?: ""
        }
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
            NostrConnectUtils.getIntentFromNostrConnect(intent, route, localAccount, onReady)
        } else if (bunkerRequest != null) {
            BunkerRequestUtils.getIntentDataFromBunkerRequest(context, intent, bunkerRequest, route, onReady)
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

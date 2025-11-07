package com.greenart7c3.nostrsigner.service

import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Browser
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.core.net.toUri
import com.fasterxml.jackson.module.kotlin.readValue
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.PrivateZapEncryptedDataKind
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.models.containsNip
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.ui.RememberType
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.util.Base64
import java.util.zip.GZIPOutputStream
import kotlin.collections.ifEmpty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object IntentUtils {
    fun decodeData(data: String, replace: Boolean = true, decodeData: Boolean = true): String {
        if (!decodeData) {
            if (!replace) return data.replace("nostrsigner:", "")
            return data.replace("nostrsigner:", "").replace("+", "%2b")
        }
        if (!replace) return URLDecoder.decode(data.replace("nostrsigner:", ""), "utf-8")
        return URLDecoder.decode(data.replace("nostrsigner:", "").replace("+", "%2b"), "utf-8")
    }

    private suspend fun getIntentDataWithoutExtras(
        context: Context,
        data: String,
        intent: Intent,
        packageName: String?,
        route: String?,
        account: Account,
    ): IntentData? {
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
            return getIntentDataFromIntent(context, intent, packageName, route, account)
        } else {
            var type = SignerType.INVALID
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
                            else -> SignerType.INVALID
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

            if (type == SignerType.INVALID) {
                return null
            }

            return when (type) {
                SignerType.SIGN_EVENT -> {
                    val unsignedEvent = getUnsignedEvent(localData, account)
                    var localAccount = account
                    val unsignedEventKey = unsignedEvent.pubKey
                    if (unsignedEvent.pubKey != account.hexKey) {
                        LocalPreferences.loadFromEncryptedStorageSync(context, Hex.decode(unsignedEvent.pubKey).toNpub())?.let {
                            localAccount = it
                        }
                    }
                    val signedEvent = localAccount.signer.sign<Event>(
                        unsignedEvent.createdAt,
                        unsignedEvent.kind,
                        unsignedEvent.tags,
                        unsignedEvent.content,
                    )

                    IntentData(
                        data = localData,
                        name = appName,
                        type = type,
                        pubKey = pubKey,
                        id = "",
                        callBackUrl = callbackUrl,
                        compression = compressionType,
                        returnType = returnType,
                        permissions = listOf(),
                        currentAccount = Hex.decode(signedEvent.pubKey).toNpub(),
                        checked = mutableStateOf(true),
                        rememberType = mutableStateOf(RememberType.NEVER),
                        route = route,
                        event = signedEvent,
                        encryptedData = null,
                        unsignedEventKey = unsignedEventKey,
                    )
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
                            Amber.instance.applicationIOScope.launch {
                                val database = Amber.instance.getLogDatabase(account.npub)
                                database.dao().insertLog(
                                    LogEntity(
                                        0,
                                        packageName ?: "",
                                        type.toString(),
                                        e.message ?: "Could not decrypt the message",
                                        System.currentTimeMillis(),
                                    ),
                                )
                            }
                            "Could not decrypt the message"
                        }

                    val encryptedDataKind = when (type) {
                        else -> {
                            if (type.name.contains("ENCRYPT")) {
                                if (localData.startsWith("{")) {
                                    try {
                                        val event = AmberEvent.fromJson(localData)
                                        if (event.kind == SealedRumorEvent.KIND) {
                                            val decryptedSealData = account.signer.signerSync.decrypt(event.content, account.hexKey)
                                            if (decryptedSealData.startsWith("{")) {
                                                val sealEvent = AmberEvent.fromJson(decryptedSealData)
                                                EventEncryptedDataKind(event, EventEncryptedDataKind(sealEvent, null, decryptedSealData), result)
                                            } else if (decryptedSealData.startsWith("[")) {
                                                val tagArray = JacksonMapper.fromJsonToTagArray(decryptedSealData)
                                                EventEncryptedDataKind(event, TagArrayEncryptedDataKind(tagArray, decryptedSealData), decryptedSealData)
                                            } else {
                                                EventEncryptedDataKind(event, ClearTextEncryptedDataKind(event.content, decryptedSealData), result)
                                            }
                                        } else {
                                            EventEncryptedDataKind(event, null, result)
                                        }
                                    } catch (e: Exception) {
                                        Log.e("IntentUtils", "Error parsing JSON: ${e.message}")
                                        ClearTextEncryptedDataKind(localData, result)
                                    }
                                } else if (localData.startsWith("[")) {
                                    val tagArray = JacksonMapper.fromJsonToTagArray(localData)
                                    TagArrayEncryptedDataKind(tagArray, result)
                                } else {
                                    ClearTextEncryptedDataKind(localData, result)
                                }
                            } else {
                                if (result.startsWith("{")) {
                                    val event = AmberEvent.fromJson(result)
                                    if (event.kind == SealedRumorEvent.KIND) {
                                        val decryptedSealData = account.signer.signerSync.decrypt(event.content, account.hexKey)
                                        if (decryptedSealData.startsWith("{")) {
                                            val sealEvent = AmberEvent.fromJson(decryptedSealData)
                                            EventEncryptedDataKind(event, EventEncryptedDataKind(sealEvent, null, decryptedSealData), result)
                                        } else if (decryptedSealData.startsWith("[")) {
                                            val tagArray = JacksonMapper.fromJsonToTagArray(decryptedSealData)
                                            EventEncryptedDataKind(event, TagArrayEncryptedDataKind(tagArray, decryptedSealData), decryptedSealData)
                                        } else {
                                            EventEncryptedDataKind(event, ClearTextEncryptedDataKind(event.content, decryptedSealData), result)
                                        }
                                    } else {
                                        EventEncryptedDataKind(event, null, result)
                                    }
                                } else if (result.startsWith("[")) {
                                    val tagArray = JacksonMapper.fromJsonToTagArray(result)
                                    TagArrayEncryptedDataKind(tagArray, result)
                                } else {
                                    ClearTextEncryptedDataKind(localData, result)
                                }
                            }
                        }
                    }

                    IntentData(
                        data = localData,
                        name = appName,
                        type = type,
                        pubKey = pubKey,
                        id = "",
                        callBackUrl = callbackUrl,
                        compression = compressionType,
                        returnType = returnType,
                        permissions = listOf(),
                        currentAccount = Hex.decode(pubKey).toNpub(),
                        checked = mutableStateOf(true),
                        rememberType = mutableStateOf(RememberType.NEVER),
                        route = route,
                        event = null,
                        encryptedData = encryptedDataKind,
                    )
                }
                SignerType.GET_PUBLIC_KEY -> {
                    IntentData(
                        data = localData,
                        name = appName,
                        type = type,
                        pubKey = pubKey,
                        id = "",
                        callBackUrl = callbackUrl,
                        compression = compressionType,
                        returnType = returnType,
                        permissions = listOf(),
                        currentAccount = "",
                        checked = mutableStateOf(true),
                        rememberType = mutableStateOf(RememberType.NEVER),
                        route = route,
                        event = null,
                        encryptedData = null,
                    )
                }
                SignerType.SIGN_MESSAGE -> {
                    IntentData(
                        data = localData,
                        name = appName,
                        type = type,
                        pubKey = pubKey,
                        id = "",
                        callBackUrl = callbackUrl,
                        compression = compressionType,
                        returnType = returnType,
                        permissions = listOf(),
                        currentAccount = "",
                        checked = mutableStateOf(true),
                        rememberType = mutableStateOf(RememberType.NEVER),
                        route = route,
                        event = null,
                        encryptedData = null,
                    )
                }
                else -> null
            }
        }
    }

    private suspend fun getIntentDataFromIntent(
        context: Context,
        intent: Intent,
        packageName: String?,
        route: String?,
        account: Account,
    ): IntentData? {
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
                else -> SignerType.INVALID
            }

        if (type == SignerType.INVALID) {
            return null
        }

        val data =
            try {
                decodeData(intent.data?.toString() ?: "", packageName == null, packageName == null)
            } catch (_: Exception) {
                intent.data?.toString()?.replace("nostrsigner:", "") ?: ""
            }

        val callbackUrl = intent.extras?.getString("callbackUrl") ?: ""
        var name = if (callbackUrl.isNotBlank()) callbackUrl.toUri().host ?: "" else ""
        if (name.isBlank()) {
            name = intent.extras?.getString("appName") ?: ""
        }
        val pubKey = intent.extras?.getString("pubKey") ?: intent.extras?.getString("pubkey") ?: ""
        val id = intent.extras?.getString("id") ?: ""

        val compressionType = if (intent.extras?.getString("compression") == "gzip") CompressionType.GZIP else CompressionType.NONE
        val returnType = if (intent.extras?.getString("returnType") == "event") ReturnType.EVENT else ReturnType.SIGNATURE
        val json = intent.extras?.getString("permissions")
        val permissions: MutableList<Permission>? = json?.let {
            try {
                Permission.mapper.readValue<MutableList<Permission>>(it)
            } catch (_: Exception) {
                null
            }
        }
        permissions?.forEach {
            it.checked = true
        }
        permissions?.removeIf { it.kind == null && (it.type == "sign_event" || it.type == "nip") }
        permissions?.removeIf { it.type == "nip" && (it.kind == null || !it.kind.containsNip()) }

        return when (type) {
            SignerType.SIGN_EVENT -> {
                val unsignedEvent = getUnsignedEvent(data, account)
                var localAccount = account
                val unsignedEventKey = unsignedEvent.pubKey
                if (unsignedEvent.pubKey != account.hexKey) {
                    LocalPreferences.loadFromEncryptedStorageSync(context, Hex.decode(unsignedEvent.pubKey).toNpub())?.let {
                        localAccount = it
                    }
                }

                val signed = localAccount.signer.sign<Event>(
                    unsignedEvent.createdAt,
                    unsignedEvent.kind,
                    unsignedEvent.tags,
                    unsignedEvent.content,
                )

                var npub = intent.getStringExtra("current_user")
                if (npub != null) {
                    npub = parsePubKey(npub)
                }

                IntentData(
                    data = data,
                    name = name,
                    type = type,
                    pubKey = pubKey,
                    id = id,
                    callBackUrl = intent.extras?.getString("callbackUrl"),
                    compression = compressionType,
                    returnType = returnType,
                    permissions = permissions?.map { Permission(it.type.trim(), it.kind, it.checked) },
                    currentAccount = npub ?: Hex.decode(signed.pubKey).toNpub(),
                    checked = mutableStateOf(true),
                    rememberType = mutableStateOf(RememberType.NEVER),
                    route = route,
                    event = signed,
                    encryptedData = null,
                    unsignedEventKey = unsignedEventKey,
                )
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
                            val database = Amber.instance.getLogDatabase(account.npub)
                            database.dao().insertLog(
                                LogEntity(
                                    0,
                                    packageName ?: "",
                                    type.toString(),
                                    e.message ?: "Could not decrypt the message",
                                    System.currentTimeMillis(),
                                ),
                            )
                        }
                        "Could not decrypt the message"
                    }

                var npub = intent.getStringExtra("current_user")
                if (npub != null) {
                    npub = parsePubKey(npub)
                }

                val encryptedDataKind = when (type) {
                    SignerType.DECRYPT_ZAP_EVENT -> {
                        PrivateZapEncryptedDataKind(result)
                    }
                    else -> {
                        if (type.name.contains("ENCRYPT")) {
                            if (data.startsWith("{")) {
                                try {
                                    val event = AmberEvent.fromJson(data)
                                    if (event.kind == SealedRumorEvent.KIND) {
                                        val decryptedSealData = account.signer.signerSync.decrypt(event.content, account.hexKey)
                                        if (decryptedSealData.startsWith("{")) {
                                            val sealEvent = AmberEvent.fromJson(decryptedSealData)
                                            EventEncryptedDataKind(event, EventEncryptedDataKind(sealEvent, null, decryptedSealData), result)
                                        } else if (decryptedSealData.startsWith("[")) {
                                            val tagArray = JacksonMapper.fromJsonToTagArray(decryptedSealData)
                                            EventEncryptedDataKind(event, TagArrayEncryptedDataKind(tagArray, decryptedSealData), decryptedSealData)
                                        } else {
                                            EventEncryptedDataKind(event, ClearTextEncryptedDataKind(event.content, decryptedSealData), result)
                                        }
                                    } else {
                                        EventEncryptedDataKind(event, null, result)
                                    }
                                } catch (e: Exception) {
                                    Log.e("IntentUtils", "Error parsing JSON: ${e.message}")
                                    ClearTextEncryptedDataKind(data, result)
                                }
                            } else if (data.startsWith("[")) {
                                val tagArray = JacksonMapper.fromJsonToTagArray(data)
                                TagArrayEncryptedDataKind(tagArray, result)
                            } else {
                                ClearTextEncryptedDataKind(data, result)
                            }
                        } else {
                            if (result.startsWith("{")) {
                                val event = AmberEvent.fromJson(result)
                                if (event.kind == SealedRumorEvent.KIND) {
                                    val decryptedSealData = account.signer.signerSync.decrypt(event.content, account.hexKey)
                                    if (decryptedSealData.startsWith("{")) {
                                        val sealEvent = AmberEvent.fromJson(decryptedSealData)
                                        EventEncryptedDataKind(event, EventEncryptedDataKind(sealEvent, null, decryptedSealData), result)
                                    } else if (decryptedSealData.startsWith("[")) {
                                        val tagArray = JacksonMapper.fromJsonToTagArray(decryptedSealData)
                                        EventEncryptedDataKind(event, TagArrayEncryptedDataKind(tagArray, decryptedSealData), decryptedSealData)
                                    } else {
                                        EventEncryptedDataKind(event, ClearTextEncryptedDataKind(event.content, decryptedSealData), result)
                                    }
                                } else {
                                    EventEncryptedDataKind(event, null, result)
                                }
                            } else if (result.startsWith("[")) {
                                val tagArray = JacksonMapper.fromJsonToTagArray(result)
                                TagArrayEncryptedDataKind(tagArray, result)
                            } else {
                                ClearTextEncryptedDataKind(data, result)
                            }
                        }
                    }
                }

                IntentData(
                    data = data,
                    name = name,
                    type = type,
                    pubKey = pubKey,
                    id = id,
                    callBackUrl = intent.extras?.getString("callbackUrl"),
                    compression = compressionType,
                    returnType = returnType,
                    permissions = permissions?.map { Permission(it.type.trim(), it.kind, it.checked) },
                    currentAccount = npub ?: Hex.decode(pubKey).toNpub(),
                    checked = mutableStateOf(true),
                    rememberType = mutableStateOf(RememberType.NEVER),
                    route = route,
                    event = null,
                    encryptedData = encryptedDataKind,
                )
            }
            SignerType.GET_PUBLIC_KEY -> {
                var npub = intent.getStringExtra("current_user")
                if (npub != null) {
                    npub = parsePubKey(npub)
                }

                IntentData(
                    data = data,
                    name = name,
                    type = type,
                    pubKey = pubKey,
                    id = id,
                    callBackUrl = intent.extras?.getString("callbackUrl"),
                    compression = compressionType,
                    returnType = returnType,
                    permissions = permissions?.map { Permission(it.type.trim(), it.kind, it.checked) },
                    currentAccount = npub ?: Hex.decode(pubKey).toNpub(),
                    checked = mutableStateOf(true),
                    rememberType = mutableStateOf(RememberType.NEVER),
                    route = route,
                    event = null,
                    encryptedData = null,
                )
            }
            SignerType.SIGN_MESSAGE -> {
                var npub = intent.getStringExtra("current_user")
                if (npub != null) {
                    npub = parsePubKey(npub)
                }

                IntentData(
                    data = data,
                    name = name,
                    type = type,
                    pubKey = pubKey,
                    id = id,
                    callBackUrl = intent.extras?.getString("callbackUrl"),
                    compression = compressionType,
                    returnType = returnType,
                    permissions = permissions?.map { Permission(it.type.trim(), it.kind, it.checked) },
                    currentAccount = npub ?: Hex.decode(pubKey).toNpub(),
                    checked = mutableStateOf(true),
                    rememberType = mutableStateOf(RememberType.NEVER),
                    route = route,
                    event = null,
                    encryptedData = null,
                )
            }
            else -> null
        }
    }

    fun parsePubKey(key: String): String? {
        if (key.startsWith("npub1")) {
            return key
        }
        return try {
            Hex.decode(key).toNpub()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getIntentData(
        context: Context,
        intent: Intent,
        packageName: String?,
        route: String?,
        currentLoggedInAccount: Account,
    ): IntentData? {
        try {
            if (intent.data == null) {
                return null
            }

            var localAccount = currentLoggedInAccount
            if (intent.getStringExtra("current_user") != null) {
                var npub = intent.getStringExtra("current_user")
                if (npub != null) {
                    npub = parsePubKey(npub)
                }
                LocalPreferences.loadFromEncryptedStorageSync(context, npub)?.let {
                    localAccount = it
                }
            }

            if (intent.dataString?.startsWith("nostrconnect:") == true) {
                NostrConnectUtils.getIntentFromNostrConnect(intent, localAccount)
            } else if (intent.extras?.getString(Browser.EXTRA_APPLICATION_ID) == null) {
                return getIntentDataFromIntent(context, intent, packageName, route, localAccount)
            } else {
                return getIntentDataWithoutExtras(context, intent.data?.toString() ?: "", intent, packageName, route, localAccount)
            }
        } catch (e: Exception) {
            Amber.instance.applicationIOScope.launch {
                LocalPreferences.allSavedAccounts(Amber.instance).forEach {
                    Amber.instance.getLogDatabase(it.npub).dao().insertLog(
                        LogEntity(
                            id = 0,
                            url = "IntentUtils",
                            type = "Error",
                            message = e.stackTraceToString(),
                            time = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
        return null
    }

    fun getUnsignedEvent(
        data: String,
        account: Account,
    ): Event {
        val event = AmberEvent.fromJson(data)
        if (event.pubKey.isEmpty()) {
            event.pubKey = account.hexKey
        }
        if (event.id.isEmpty()) {
            event.id =
                EventHasher.hashId(
                    event.pubKey,
                    event.createdAt,
                    event.kind,
                    event.tags,
                    event.content,
                )
        }

        return AmberEvent.toEvent(event)
    }

    fun sendResult(
        context: Context,
        packageName: String?,
        account: Account,
        key: String,
        clipboardManager: Clipboard,
        event: String,
        value: String,
        intentData: IntentData,
        kind: Int?,
        onLoading: (Boolean) -> Unit,
        permissions: List<Permission>? = null,
        appName: String? = null,
        signPolicy: Int? = null,
        onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
        shouldCloseApplication: Boolean? = null,
        rememberType: RememberType,
        deleteAfter: Long = 0L,
    ) {
        onLoading(true)
        Amber.instance.applicationIOScope.launch {
            val database = Amber.instance.getDatabase(account.npub)
            val historyDatabase = Amber.instance.getHistoryDatabase(account.npub)
            val defaultRelays = Amber.instance.settings.defaultRelays
            val savedApplication = database.dao().getByKey(key)
            val relays = savedApplication?.application?.relays?.ifEmpty { defaultRelays } ?: defaultRelays
            val localAppName =
                if (packageName != null) {
                    val info = context.packageManager.getApplicationInfo(packageName, 0)
                    context.packageManager.getApplicationLabel(info).toString()
                } else {
                    appName
                }

            val application =
                savedApplication ?: ApplicationWithPermissions(
                    application = ApplicationEntity(
                        key = key,
                        name = appName ?: localAppName ?: "",
                        relays = if (packageName != null) emptyList() else relays,
                        url = "",
                        icon = "",
                        description = "",
                        pubKey = account.hexKey,
                        isConnected = true,
                        secret = "",
                        useSecret = false,
                        signPolicy = account.signPolicy,
                        closeApplication = shouldCloseApplication != false,
                        deleteAfter = deleteAfter,
                        lastUsed = TimeUtils.now(),
                    ),
                    permissions = mutableListOf(),
                )
            application.application.isConnected = true

            if (signPolicy != null) {
                AmberUtils.configureSignPolicy(application, signPolicy, key, permissions)
            }

            if (rememberType != RememberType.NEVER) {
                AmberUtils.acceptPermission(
                    application = application,
                    key = key,
                    type = intentData.type,
                    kind = kind,
                    rememberType = rememberType,
                )
            }

            if (intentData.type == SignerType.GET_PUBLIC_KEY) {
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

            if (packageName != null) {
                database.dao().insertApplicationWithPermissions(application)
                historyDatabase.dao().addHistory(
                    HistoryEntity(
                        0,
                        key,
                        intentData.type.toString(),
                        kind,
                        TimeUtils.now(),
                        true,
                    ),
                    account.npub,
                )

                val intent = Intent()
                intent.putExtra("signature", value)
                intent.putExtra("result", value)
                intent.putExtra("id", intentData.id)
                intent.putExtra("event", event)
                if (intentData.type == SignerType.GET_PUBLIC_KEY) {
                    intent.putExtra("package", BuildConfig.APPLICATION_ID)
                }
                val activity = Amber.instance.getMainActivity()
                activity?.setResult(RESULT_OK, intent)
                onRemoveIntentData(listOf(intentData), IntentResultType.REMOVE)
                activity?.intent = null
                activity?.finish()
            } else if (!intentData.callBackUrl.isNullOrBlank()) {
                if (intentData.returnType == ReturnType.SIGNATURE) {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = (intentData.callBackUrl + Uri.encode(value)).toUri()
                    context.startActivity(intent)
                } else {
                    if (intentData.compression == CompressionType.GZIP) {
                        // Compress the string using GZIP
                        val byteArrayOutputStream = ByteArrayOutputStream()
                        val gzipOutputStream = GZIPOutputStream(byteArrayOutputStream)
                        gzipOutputStream.write(event.toByteArray())
                        gzipOutputStream.close()

                        // Convert the compressed data to Base64
                        val compressedData = byteArrayOutputStream.toByteArray()
                        val encodedString = Base64.getEncoder().encodeToString(compressedData)
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = (intentData.callBackUrl + Uri.encode("Signer1$encodedString")).toUri()
                        context.startActivity(intent)
                    } else {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = (intentData.callBackUrl + Uri.encode(event)).toUri()
                        context.startActivity(intent)
                    }
                }
                onRemoveIntentData(listOf(intentData), IntentResultType.REMOVE)
                val activity = Amber.instance.getMainActivity()
                activity?.intent = null
                activity?.finish()
            } else {
                val result =
                    if (intentData.returnType == ReturnType.SIGNATURE) {
                        value
                    } else {
                        event
                    }
                val message =
                    if (intentData.returnType == ReturnType.SIGNATURE) {
                        context.getString(R.string.signature_copied_to_the_clipboard)
                    } else {
                        context.getString(R.string.event_copied_to_the_clipboard)
                    }

                Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                    clipboardManager.setClipEntry(
                        ClipEntry(
                            ClipData.newPlainText("", result),
                        ),
                    )

                    Toast.makeText(
                        context,
                        message,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                onRemoveIntentData(listOf(intentData), IntentResultType.REMOVE)
                val activity = Amber.instance.getMainActivity()
                activity?.intent = null
                activity?.finish()
            }
        }
    }

    fun sendRejection(
        key: String,
        account: Account,
        intentData: IntentData,
        appName: String,
        rememberType: RememberType,
        kind: Int?,
        onLoading: (Boolean) -> Unit,
        onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    ) {
        Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
            if (key == "null") {
                onLoading(false)
                return@launch
            }

            val defaultRelays = Amber.instance.settings.defaultRelays
            val savedApplication = Amber.instance.getDatabase(account.npub).dao().getByKey(key)
            val relays = savedApplication?.application?.relays?.ifEmpty { defaultRelays } ?: defaultRelays
            val application =
                savedApplication ?: ApplicationWithPermissions(
                    application = ApplicationEntity(
                        key = key,
                        name = appName,
                        relays = relays,
                        url = "",
                        icon = "",
                        description = "",
                        pubKey = account.hexKey,
                        isConnected = true,
                        secret = "",
                        useSecret = false,
                        signPolicy = account.signPolicy,
                        closeApplication = true,
                        deleteAfter = 0L,
                        lastUsed = TimeUtils.now(),
                    ),
                    permissions = mutableListOf(),
                )
            if (rememberType != RememberType.NEVER) {
                AmberUtils.acceptOrRejectPermission(
                    application,
                    key,
                    intentData.type,
                    kind,
                    false,
                    rememberType,
                    account,
                )
            }

            Amber.instance.getDatabase(account.npub).dao().insertApplicationWithPermissions(application)
            Amber.instance.getHistoryDatabase(account.npub).dao().addHistory(
                HistoryEntity(
                    0,
                    key,
                    intentData.type.toString(),
                    kind,
                    TimeUtils.now(),
                    false,
                ),
                account.npub,
            )

            val activity = Amber.instance.getMainActivity()
            activity?.intent = null
            activity?.setResult(RESULT_OK, Intent().also { it.putExtra("rejected", "") })
            if (application.application.closeApplication) {
                activity?.finish()
            }
            onRemoveIntentData(listOf(intentData), IntentResultType.REMOVE)
            onLoading(false)
        }
    }

    fun isRemembered(signPolicy: Int?, permission: ApplicationPermissionsEntity?): Boolean? {
        val rejectUntil = permission?.rejectUntil ?: 0
        val acceptUntil = permission?.acceptUntil ?: 0
        if (signPolicy == 2) {
            return true
        }
        if (rejectUntil == 0L && acceptUntil == 0L) return null
        return if (rejectUntil > TimeUtils.now() && rejectUntil > 0 && permission?.acceptable == false) {
            false
        } else if (acceptUntil > TimeUtils.now() && acceptUntil > 0 && permission?.acceptable == true) {
            true
        } else {
            null
        }
    }
}

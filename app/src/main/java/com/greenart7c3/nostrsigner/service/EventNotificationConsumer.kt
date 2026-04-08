/**
 * Copyright (c) 2024 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.greenart7c3.nostrsigner.service

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.models.EncryptionType
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.service.NotificationUtils.sendNotification
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUsers
import com.vitorpamplona.quartz.nip04Dm.crypto.EncryptedInfo
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Encrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Encrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID
import kotlinx.coroutines.launch

class EventNotificationConsumer(private val applicationContext: Context) {
    private fun saveLog(
        text: String,
        url: String,
        npub: String? = null,
    ) {
        Log.d(Amber.TAG, "$url: $text")
        Amber.instance.applicationIOScope.launch {
            if (npub != null) {
                val dao = Amber.instance.getLogDatabase(npub).dao()
                dao.insertLog(
                    LogEntity(
                        0,
                        url,
                        "bunker",
                        text,
                        System.currentTimeMillis(),
                    ),
                )
            } else {
                val accounts = LocalPreferences.allSavedAccounts(applicationContext)
                accounts.forEach {
                    LocalPreferences.loadFromEncryptedStorage(applicationContext, it.npub)?.let { acc ->
                        val dao = Amber.instance.getLogDatabase(acc.npub).dao()
                        dao.insertLog(
                            LogEntity(
                                0,
                                url,
                                "bunker",
                                text,
                                System.currentTimeMillis(),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun consume(
        event: Event,
        relay: NormalizedRelayUrl,
    ) {
        saveLog("New event ${event.id} from ${event.pubKey}", relay.url)

        if (!notificationManager().areNotificationsEnabled()) {
            saveLog("notifications disabled", relay.url)
            return
        }
        if (event.kind != NostrConnectEvent.KIND) {
            saveLog("Not a bunker request: kind ${event.kind}", relay.url)
            return
        }
        if (!event.verify()) {
            saveLog("Invalid id hash or signature", relay.url)
            return
        }

        NotificationUtils.getOrCreateBunkerChannel(applicationContext)
        NotificationUtils.getOrCreateErrorsChannel(applicationContext)

        val taggedKey = event.taggedUsers().firstOrNull()
        if (taggedKey == null) {
            saveLog("No tagged users found in event ${event.id}", relay.url)
            return
        }

        // First try to match the tagged key to a logged-in account (real pubkey)
        var acc = LocalPreferences.loadFromEncryptedStorageSync(applicationContext, taggedKey.toNPub())
        var connectionPrivKey = ""

        if (acc != null) {
            saveLog("Direct account match for ${acc.npub}", relay.url)
        }

        // If not a direct account match, search for a connection with this local pubkey
        if (acc == null) {
            val accounts = LocalPreferences.allSavedAccounts(applicationContext)
            outer@ for (accountInfo in accounts) {
                val account = LocalPreferences.loadFromEncryptedStorageSync(applicationContext, accountInfo.npub)
                    ?: continue
                val connections =
                    kotlinx.coroutines.runBlocking {
                        Amber.instance.getDatabase(account.npub).dao().getAllWithLocalKey(account.hexKey)
                    }
                val taggedNpub = taggedKey.toNPub()
                for (conn in connections) {
                    if (conn.localKey.isNotEmpty() && conn.localPubKey.hexToByteArray().toNpub() == taggedNpub) {
                        acc = account
                        connectionPrivKey = conn.localKey
                        saveLog("Found connection for ${acc.npub} with local pubkey ${conn.localPubKey}", relay.url)
                        break@outer
                    }
                }
            }
        }

        if (acc == null) {
            saveLog("Tagged account ${taggedKey.toNPub()} not logged in", relay.url)
            return
        }
        notify(event, acc, relay, connectionPrivKey)
    }

    private fun notify(
        event: Event,
        acc: Account,
        relay: NormalizedRelayUrl,
        connectionPrivKey: String = "",
    ) {
        if (event.content.isEmpty()) return

        saveLog("New event ${event.toJson()}", relay.url, acc.npub)

        val encryptionType = if (EncryptedInfo.isNIP04(event.content)) {
            EncryptionType.NIP04
        } else {
            EncryptionType.NIP44
        }

        Amber.instance.applicationIOScope.launch {
            val decrypted = try {
                if (connectionPrivKey.isNotEmpty()) {
                    val connSigner = NostrSignerInternal(KeyPair(privKey = connectionPrivKey.hexToByteArray()))
                    connSigner.decrypt(event.content, event.pubKey)
                } else {
                    acc.decrypt(event.content, event.pubKey)
                }
            } catch (e: Exception) {
                saveLog("Decryption failed for event ${event.id}: ${e.message}", relay.url, acc.npub)
                null
            }

            decrypted?.let {
                notify(event, acc, it, relay, encryptionType, connectionPrivKey)
            }
        }
    }

    private suspend fun notify(
        event: Event,
        acc: Account,
        requestStr: String,
        relay: NormalizedRelayUrl,
        encryptionType: EncryptionType,
        connectionPrivKey: String = "",
    ) {
        val responseRelay = listOf(relay)
        val database = Amber.instance.getDatabase(acc.npub)
        val dao = database.dao()
        val historyDao = Amber.instance.getHistoryDatabase(acc.npub).dao()

        val notification = Amber.instance.notificationCache[event.id]
        if (notification != null) return
        Amber.instance.notificationCache.put(event.id, event.createdAt)

        saveLog("Decrypted request: $requestStr", relay.url, acc.npub)

        val bunkerRequest = JacksonMapper.mapper.readValue(requestStr, BunkerRequest::class.java)

        val signedEvent = if (bunkerRequest is BunkerRequestSign) {
            acc.sign(bunkerRequest.event)
        } else {
            null
        }

        val encryptedDataKind = getEncryptedDataKind(bunkerRequest, acc, relay.url)

        val type = BunkerRequestUtils.getTypeFromBunker(bunkerRequest)
        if (type == SignerType.INVALID) {
            saveLog("Invalid request method ${bunkerRequest.method}", relay.url, acc.npub)
            return
        }

        var request = AmberBunkerRequest(
            request = bunkerRequest,
            localKey = event.pubKey,
            relays = responseRelay,
            currentAccount = acc.npub,
            nostrConnectSecret = "",
            closeApplication = true,
            name = "",
            signedEvent = signedEvent,
            encryptedData = encryptedDataKind,
            encryptionType = encryptionType,
            isNostrConnectUri = false,
            signerPrivKey = connectionPrivKey,
        )

        var amberEvent: AmberEvent? = null

        val pubKey =
            if (bunkerRequest.method.endsWith("encrypt") || bunkerRequest.method.endsWith("decrypt")) {
                bunkerRequest.params.first()
            } else if (bunkerRequest.method == "decrypt_zap_event") {
                bunkerRequest.params[1]
            } else if (bunkerRequest.method == "sign_event") {
                amberEvent = AmberEvent.fromJson(bunkerRequest.params.first())
                amberEvent.pubKey
            } else {
                ""
            }

        val permission = dao.getByKey(event.pubKey)
        if (permission != null && ((permission.application.secret != permission.application.key && permission.application.useSecret) || permission.application.isConnected) && type == SignerType.CONNECT) {
            Amber.instance.applicationIOScope.launch {
                historyDao
                    .addHistory(
                        HistoryEntity(
                            0,
                            permission.application.key,
                            type.toString().toLowerCase(Locale.current),
                            amberEvent?.kind,
                            TimeUtils.now(),
                            true,
                            content = amberEvent?.content ?: "",
                        ),
                        acc.npub,
                    )
            }
            BunkerRequestUtils.sendBunkerResponse(
                context = applicationContext,
                account = acc,
                bunkerRequest = request,
                bunkerResponse = BunkerResponse(bunkerRequest.id, request.nostrConnectSecret.ifBlank { "ack" }, null),
                relays = permission.application.relays,
                onLoading = {},
                onDone = {},
            )
            return
        }

        var applicationWithSecret: ApplicationWithPermissions? = null
        if (bunkerRequest is BunkerRequestConnect) {
            val secret = bunkerRequest.secret ?: UUID.randomUUID().toString()

            applicationWithSecret = dao.getBySecret(secret)
            if (applicationWithSecret == null || secret.isBlank() || applicationWithSecret.application.isConnected || !applicationWithSecret.application.useSecret) {
                val message = if (applicationWithSecret == null) {
                    "invalid secret"
                } else if (secret.isBlank()) {
                    "no secret"
                } else if (applicationWithSecret.application.isConnected) {
                    "already connected"
                } else {
                    "secret not in use"
                }
                saveLog("Connection rejected: $message", relay.url, acc.npub)
                BunkerRequestUtils.sendBunkerResponse(
                    context = applicationContext,
                    account = acc,
                    bunkerRequest = request,
                    bunkerResponse = BunkerResponse(bunkerRequest.id, "", message),
                    relays = applicationWithSecret?.application?.relays ?: responseRelay,
                    onLoading = { },
                    onDone = { },
                )
                return
            }
        }

        val relays = permission?.application?.relays ?: applicationWithSecret?.application?.relays ?: responseRelay
        if (permission == null && applicationWithSecret == null) {
            saveLog("No permission found for ${event.pubKey}", relay.url, acc.npub)
            BunkerRequestUtils.sendBunkerResponse(
                context = applicationContext,
                account = acc,
                bunkerRequest = request,
                bunkerResponse = BunkerResponse(bunkerRequest.id, "", "no permission"),
                relays = relays,
                onLoading = { },
                onDone = { },
            )
            return
        }

        request = AmberBunkerRequest(
            request = bunkerRequest,
            localKey = event.pubKey,
            relays = relays,
            currentAccount = acc.npub,
            nostrConnectSecret = "",
            closeApplication = true,
            name = "",
            signedEvent = signedEvent,
            encryptedData = encryptedDataKind,
            encryptionType = encryptionType,
            isNostrConnectUri = false,
            signerPrivKey = connectionPrivKey,
        )

        if (type == SignerType.SWITCH_RELAYS) {
            permission?.let {
                val defaultRelays = Amber.instance.settings.defaultRelays
                BunkerRequestUtils.sendBunkerResponse(
                    context = applicationContext,
                    account = acc,
                    bunkerRequest = request,
                    bunkerResponse = BunkerResponse(bunkerRequest.id, if (defaultRelays.isEmpty()) null else "[${defaultRelays.joinToString(separator = ",") { "\"${it.url}\"" }}]", null),
                    relays = relays,
                    onLoading = { },
                    onDone = {
                        if (it) {
                            Amber.instance.applicationIOScope.launch {
                                dao.delete(permission.application.key)
                                dao.insertApplicationWithPermissions(
                                    permission.copy(
                                        application = permission.application.copy(
                                            relays = defaultRelays,
                                        ),
                                    ),
                                )
                                Amber.instance.notificationSubscription.updateFilter()
                            }
                        }
                    },
                )
            }
            return
        }

        if (type == SignerType.GET_PUBLIC_KEY) {
            val gpkPermission = dao.getPermission(event.pubKey, "GET_PUBLIC_KEY")
            val signPolicy = dao.getSignPolicy(event.pubKey)
            val isRemembered = IntentUtils.isRemembered(signPolicy, gpkPermission)
            if (isRemembered == null) {
                val message = generateMessage(type, amberEvent, request, event, permission, applicationWithSecret)
                notificationManager().sendNotification(
                    bunkerRequest.id,
                    message,
                    "Bunker",
                    "BunkerID",
                    applicationContext,
                    request,
                )
                return
            }
            if (!isRemembered) {
                Amber.instance.applicationIOScope.launch {
                    historyDao.addHistory(
                        HistoryEntity(
                            0,
                            event.pubKey,
                            "GET_PUBLIC_KEY",
                            null,
                            TimeUtils.now(),
                            false,
                        ),
                        acc.npub,
                    )
                }
                BunkerRequestUtils.sendBunkerResponse(
                    context = applicationContext,
                    account = acc,
                    bunkerRequest = request,
                    bunkerResponse = BunkerResponse(bunkerRequest.id, "", "user rejected"),
                    relays = relays,
                    onLoading = { },
                    onDone = { },
                )
                return
            }
            Amber.instance.applicationIOScope.launch {
                historyDao.addHistory(
                    HistoryEntity(
                        0,
                        event.pubKey,
                        "GET_PUBLIC_KEY",
                        null,
                        TimeUtils.now(),
                        true,
                    ),
                    acc.npub,
                )
            }
            BunkerRequestUtils.sendBunkerResponse(
                context = applicationContext,
                account = acc,
                bunkerRequest = request,
                bunkerResponse = BunkerResponse(bunkerRequest.id, acc.hexKey, null),
                relays = relays,
                onLoading = { },
                onDone = { },
            )
            return
        }

        val data = BunkerRequestUtils.getDataFromBunker(bunkerRequest)
        val projection = if (type == SignerType.PING) {
            arrayOf(acc.npub)
        } else {
            arrayOf(data, pubKey, acc.npub)
        }
        val cursor =
            applicationContext.contentResolver.query(
                "content://${BuildConfig.APPLICATION_ID}.$type".toUri(),
                projection,
                "Amber",
                null,
                event.pubKey,
            )

        cursor.use { localCursor ->
            if (localCursor == null) {
                val message = generateMessage(type, amberEvent, request, event, permission, applicationWithSecret)
                notificationManager()
                    .sendNotification(
                        bunkerRequest.id,
                        message,
                        "Bunker",
                        "BunkerID",
                        applicationContext,
                        request,
                    )
            } else {
                if (localCursor.moveToFirst()) {
                    if (localCursor.getColumnIndex("rejected") > -1) {
                        saveLog("User rejected request ${bunkerRequest.id}", relay.url, acc.npub)
                        BunkerRequestUtils.sendBunkerResponse(
                            context = applicationContext,
                            account = acc,
                            bunkerRequest = request,
                            bunkerResponse = BunkerResponse(bunkerRequest.id, "", "user rejected"),
                            relays = relays,
                            onLoading = { },
                            onDone = { },
                        )
                    } else {
                        var index = localCursor.getColumnIndex("event")
                        if (index == -1) {
                            index = localCursor.getColumnIndex("result")
                        }
                        val result = localCursor.getString(index)

                        BunkerRequestUtils.sendBunkerResponse(
                            context = applicationContext,
                            account = acc,
                            bunkerRequest = request,
                            bunkerResponse = BunkerResponse(bunkerRequest.id, result, null),
                            relays = relays,
                            onLoading = { },
                            onDone = { },
                        )
                    }
                } else {
                    val message = generateMessage(type, amberEvent, request, event, permission, applicationWithSecret)
                    notificationManager()
                        .sendNotification(
                            bunkerRequest.id,
                            message,
                            "Bunker",
                            "BunkerID",
                            applicationContext,
                            request,
                        )
                }
            }
        }
    }

    private fun generateMessage(
        type: SignerType,
        amberEvent: AmberEvent?,
        request: AmberBunkerRequest,
        event: Event,
        permission: ApplicationWithPermissions?,
        applicationWithSecret: ApplicationWithPermissions?,
    ): String {
        var name = event.pubKey.toShortenHex()
        if (permission != null && permission.application.name.isNotBlank()) {
            name = permission.application.name
        } else if (applicationWithSecret != null && applicationWithSecret.application.name.isNotBlank()) {
            name = applicationWithSecret.application.name
        }

        val bunkerPermission = Permission(type.toString().toLowerCase(Locale.current), amberEvent?.kind)
        var message = "$name  ${applicationContext.getString(R.string.requests)} ${bunkerPermission.toLocalizedString(applicationContext)}"
        if (type == SignerType.SIGN_EVENT) {
            message = "$name ${applicationContext.getString(R.string.wants_you_to_sign_a, bunkerPermission.toLocalizedString(applicationContext))}"
        }
        if (type == SignerType.CONNECT) {
            message = "$name ${bunkerPermission.toLocalizedString(applicationContext)}"
        }

        if (type.name.contains("ENCRYPT")) {
            when (request.encryptedData) {
                is EventEncryptedDataKind -> {
                    val permission = Permission("sign_event", request.encryptedData.event.kind)
                    message = "$name ${applicationContext.getString(R.string.wants_to_encrypt_event_kind, permission.toLocalizedString(Amber.instance), type.name.split("_").first())}"
                }

                is TagArrayEncryptedDataKind -> {
                    message = "$name ${applicationContext.getString(R.string.wants_to_encrypt_a_list_of_tags_with, type.name.split("_").first())}"
                }

                else -> message = "$name ${applicationContext.getString(R.string.wants_to_encrypt_a_text_with, type.name.split("_").first())}"
            }
        } else if (type.name.contains("DECRYPT")) {
            when (request.encryptedData) {
                is EventEncryptedDataKind -> {
                    val permission = Permission("sign_event", request.encryptedData.event.kind)
                    message = "$name${applicationContext.getString(R.string.wants_to_read_from_encrypted_content, permission.toLocalizedString(Amber.instance), type.name.split("_").first())}"
                }

                is TagArrayEncryptedDataKind -> {
                    message = "$name ${applicationContext.getString(R.string.wants_to_read_a_list_of_tags_from_encrypted_content, type.name.split("_").first())}"
                }

                else -> message = "$name ${applicationContext.getString(R.string.wants_to_read_a_text_from_encrypted_content, type.name.split("_").first())}"
            }
        }
        return message
    }

    private suspend fun getEncryptedDataKind(
        bunkerRequest: BunkerRequest?,
        acc: Account,
        url: String,
    ): EncryptedDataKind? = if (bunkerRequest is BunkerRequestNip44Decrypt) {
        val result = acc.nip44Decrypt(bunkerRequest.ciphertext, bunkerRequest.pubKey)

        if (result.startsWith("{")) {
            try {
                val event = AmberEvent.fromJson(result)
                if (event.kind == SealedRumorEvent.KIND) {
                    val decryptedSealData = try {
                        acc.decrypt(event.content, acc.hexKey)
                    } catch (_: Exception) {
                        event.content
                    }
                    if (decryptedSealData.startsWith("{")) {
                        val sealEvent = AmberEvent.fromJson(decryptedSealData)
                        EventEncryptedDataKind(event, EventEncryptedDataKind(sealEvent, null, decryptedSealData), result)
                    } else if (decryptedSealData.startsWith("[")) {
                        try {
                            val tagArray = JacksonMapper.fromJsonToTagArray(decryptedSealData)
                            EventEncryptedDataKind(event, TagArrayEncryptedDataKind(tagArray, decryptedSealData), decryptedSealData)
                        } catch (_: Exception) {
                            EventEncryptedDataKind(event, ClearTextEncryptedDataKind(event.content, decryptedSealData), result)
                        }
                    } else {
                        EventEncryptedDataKind(event, ClearTextEncryptedDataKind(event.content, decryptedSealData), result)
                    }
                } else {
                    EventEncryptedDataKind(event, null, result)
                }
            } catch (e: Exception) {
                saveLog("Error parsing JSON: ${e.message}", url, acc.npub)
                ClearTextEncryptedDataKind(bunkerRequest.ciphertext, result)
            }
        } else if (result.startsWith("[")) {
            try {
                val tagArray = JacksonMapper.fromJsonToTagArray(result)
                TagArrayEncryptedDataKind(tagArray, result)
            } catch (_: Exception) {
                ClearTextEncryptedDataKind(bunkerRequest.ciphertext, result)
            }
        } else {
            ClearTextEncryptedDataKind(bunkerRequest.ciphertext, result)
        }
    } else if (bunkerRequest is BunkerRequestNip44Encrypt) {
        val result = acc.nip44Encrypt(bunkerRequest.message, bunkerRequest.pubKey)
        if (bunkerRequest.message.startsWith("{")) {
            try {
                val event = AmberEvent.fromJson(bunkerRequest.message)
                if (event.kind == SealedRumorEvent.KIND) {
                    val decryptedSealData = try {
                        acc.decrypt(event.content, acc.hexKey)
                    } catch (_: Exception) {
                        event.content
                    }
                    if (decryptedSealData.startsWith("{")) {
                        val sealEvent = AmberEvent.fromJson(decryptedSealData)
                        EventEncryptedDataKind(event, EventEncryptedDataKind(sealEvent, null, decryptedSealData), result)
                    } else if (decryptedSealData.startsWith("[")) {
                        try {
                            val tagArray = JacksonMapper.fromJsonToTagArray(decryptedSealData)
                            EventEncryptedDataKind(event, TagArrayEncryptedDataKind(tagArray, decryptedSealData), decryptedSealData)
                        } catch (_: Exception) {
                            EventEncryptedDataKind(event, ClearTextEncryptedDataKind(event.content, decryptedSealData), result)
                        }
                    } else {
                        EventEncryptedDataKind(event, ClearTextEncryptedDataKind(event.content, decryptedSealData), result)
                    }
                } else {
                    EventEncryptedDataKind(event, null, result)
                }
            } catch (e: Exception) {
                saveLog("Error parsing JSON: ${e.message}", url, acc.npub)
                ClearTextEncryptedDataKind(bunkerRequest.message, result)
            }
        } else if (bunkerRequest.message.startsWith("[")) {
            try {
                val tagArray = JacksonMapper.fromJsonToTagArray(bunkerRequest.message)
                TagArrayEncryptedDataKind(tagArray, result)
            } catch (_: Exception) {
                ClearTextEncryptedDataKind(bunkerRequest.message, result)
            }
        } else {
            ClearTextEncryptedDataKind(bunkerRequest.message, result)
        }
    } else if (bunkerRequest is BunkerRequestNip04Decrypt) {
        val result = acc.nip04Decrypt(bunkerRequest.ciphertext, bunkerRequest.pubKey)

        if (result.startsWith("{")) {
            try {
                val event = AmberEvent.fromJson(result)
                if (event.kind == SealedRumorEvent.KIND) {
                    val decryptedSealData = try {
                        acc.decrypt(event.content, acc.hexKey)
                    } catch (_: Exception) {
                        event.content
                    }
                    if (decryptedSealData.startsWith("{")) {
                        val sealEvent = AmberEvent.fromJson(decryptedSealData)
                        EventEncryptedDataKind(event, EventEncryptedDataKind(sealEvent, null, decryptedSealData), result)
                    } else if (decryptedSealData.startsWith("[")) {
                        try {
                            val tagArray = JacksonMapper.fromJsonToTagArray(decryptedSealData)
                            EventEncryptedDataKind(event, TagArrayEncryptedDataKind(tagArray, decryptedSealData), decryptedSealData)
                        } catch (_: Exception) {
                            EventEncryptedDataKind(event, ClearTextEncryptedDataKind(event.content, decryptedSealData), result)
                        }
                    } else {
                        EventEncryptedDataKind(event, ClearTextEncryptedDataKind(event.content, decryptedSealData), result)
                    }
                } else {
                    EventEncryptedDataKind(event, null, result)
                }
            } catch (e: Exception) {
                saveLog("Error parsing JSON: ${e.message}", url, acc.npub)
                ClearTextEncryptedDataKind(bunkerRequest.ciphertext, result)
            }
        } else if (result.startsWith("[")) {
            try {
                val tagArray = JacksonMapper.fromJsonToTagArray(result)
                TagArrayEncryptedDataKind(tagArray, result)
            } catch (_: Exception) {
                ClearTextEncryptedDataKind(bunkerRequest.ciphertext, result)
            }
        } else {
            ClearTextEncryptedDataKind(bunkerRequest.ciphertext, result)
        }
    } else if (bunkerRequest is BunkerRequestNip04Encrypt) {
        val result = acc.nip04Encrypt(bunkerRequest.message, bunkerRequest.pubKey)
        if (bunkerRequest.message.startsWith("{")) {
            try {
                val event = AmberEvent.fromJson(bunkerRequest.message)
                if (event.kind == SealedRumorEvent.KIND) {
                    val decryptedSealData = try {
                        acc.decrypt(event.content, acc.hexKey)
                    } catch (_: Exception) {
                        event.content
                    }
                    if (decryptedSealData.startsWith("{")) {
                        val sealEvent = AmberEvent.fromJson(decryptedSealData)
                        EventEncryptedDataKind(event, EventEncryptedDataKind(sealEvent, null, decryptedSealData), result)
                    } else if (decryptedSealData.startsWith("[")) {
                        try {
                            val tagArray = JacksonMapper.fromJsonToTagArray(decryptedSealData)
                            EventEncryptedDataKind(event, TagArrayEncryptedDataKind(tagArray, decryptedSealData), decryptedSealData)
                        } catch (_: Exception) {
                            EventEncryptedDataKind(event, ClearTextEncryptedDataKind(event.content, decryptedSealData), result)
                        }
                    } else {
                        EventEncryptedDataKind(event, ClearTextEncryptedDataKind(event.content, decryptedSealData), result)
                    }
                } else {
                    EventEncryptedDataKind(event, null, result)
                }
            } catch (e: Exception) {
                saveLog("Error parsing JSON: ${e.message}", url, acc.npub)
                ClearTextEncryptedDataKind(bunkerRequest.message, result)
            }
        } else if (bunkerRequest.message.startsWith("[")) {
            try {
                val tagArray = JacksonMapper.fromJsonToTagArray(bunkerRequest.message)
                TagArrayEncryptedDataKind(tagArray, result)
            } catch (_: Exception) {
                ClearTextEncryptedDataKind(bunkerRequest.message, result)
            }
        } else {
            ClearTextEncryptedDataKind(bunkerRequest.message, result)
        }
    } else {
        null
    }

    fun notificationManager(): NotificationManager = ContextCompat.getSystemService(applicationContext, NotificationManager::class.java) as NotificationManager
}

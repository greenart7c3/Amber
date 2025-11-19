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
import com.greenart7c3.nostrsigner.models.EncryptionType
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.service.NotificationUtils.sendNotification
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUsers
import com.vitorpamplona.quartz.nip04Dm.crypto.EncryptedInfo
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
    private fun saveLog(text: String) {
        Log.d(Amber.TAG, text)
        Amber.instance.applicationIOScope.launch {
            val accounts = LocalPreferences.allSavedAccounts(applicationContext)
            accounts.forEach {
                LocalPreferences.loadFromEncryptedStorage(applicationContext, it.npub)?.let { acc ->
                    val dao = Amber.instance.getLogDatabase(acc.npub).dao()
                    dao.insertLog(
                        LogEntity(
                            0,
                            "nostrsigner",
                            "bunker request",
                            text,
                            System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }
    }

    fun consume(event: Event, relay: NormalizedRelayUrl) {
        saveLog("New event ${event.toJson()}")

        if (!notificationManager().areNotificationsEnabled()) {
            saveLog("notifications disabled")
            return
        }
        if (event.kind != NostrConnectEvent.KIND) {
            saveLog("Not a bunker request")
            return
        }
        if (!event.verify()) {
            saveLog("Invalid id hash or signature")
            return
        }

        NotificationUtils.getOrCreateBunkerChannel(applicationContext)
        NotificationUtils.getOrCreateErrorsChannel(applicationContext)

        val taggedKey = event.taggedUsers().firstOrNull() ?: return
        LocalPreferences.loadFromEncryptedStorageSync(applicationContext, taggedKey.toNPub())?.let { acc ->
            notify(event, acc, relay)
        }
    }

    private fun notify(
        event: Event,
        acc: Account,
        relay: NormalizedRelayUrl,
    ) {
        if (event.content.isEmpty()) return

        val dao = Amber.instance.getLogDatabase(acc.npub).dao()
        Amber.instance.applicationIOScope.launch {
            dao.insertLog(
                LogEntity(
                    0,
                    "nostrsigner",
                    "bunker request json",
                    event.toJson(),
                    System.currentTimeMillis(),
                ),
            )
        }

        val encryptionType = if (EncryptedInfo.isNIP04(event.content)) {
            EncryptionType.NIP04
        } else {
            EncryptionType.NIP44
        }

        Amber.instance.applicationIOScope.launch {
            val decrypted = acc.signer.decrypt(event.content, event.pubKey)
            notify(event, acc, decrypted, relay, encryptionType)
        }
    }

    private suspend fun notify(
        event: Event,
        acc: Account,
        request: String,
        relay: NormalizedRelayUrl,
        encryptionType: EncryptionType,
    ) {
        val responseRelay = listOf(relay)
        val database = Amber.instance.getDatabase(acc.npub)
        val dao = database.dao()
        val logDao = Amber.instance.getLogDatabase(acc.npub).dao()
        val historyDao = Amber.instance.getHistoryDatabase(acc.npub).dao()

        val notification = Amber.instance.notificationCache[event.id]
        if (notification != null) return
        Amber.instance.notificationCache.put(event.id, event.createdAt)

        logDao.insertLog(
            LogEntity(
                0,
                "nostrsigner",
                "bunker request",
                request,
                System.currentTimeMillis(),
            ),
        )

        val bunkerRequest = JacksonMapper.mapper.readValue(request, BunkerRequest::class.java)

        val signedEvent = if (bunkerRequest is BunkerRequestSign) {
            acc.signer.signerSync.sign(bunkerRequest.event)
        } else {
            null
        }

        val encryptedDataKind = if (bunkerRequest is BunkerRequestNip44Decrypt) {
            val result = acc.signer.signerSync.nip44Decrypt(bunkerRequest.ciphertext, bunkerRequest.pubKey)

            if (result.startsWith("{")) {
                val event = AmberEvent.fromJson(result)
                if (event.kind == SealedRumorEvent.KIND) {
                    val decryptedSealData = acc.signer.signerSync.decrypt(event.content, acc.hexKey)
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
                ClearTextEncryptedDataKind(bunkerRequest.ciphertext, result)
            }
        } else if (bunkerRequest is BunkerRequestNip44Encrypt) {
            val result = acc.signer.signerSync.nip44Encrypt(bunkerRequest.message, bunkerRequest.pubKey)
            if (bunkerRequest.message.startsWith("{")) {
                try {
                    val event = AmberEvent.fromJson(bunkerRequest.message)
                    if (event.kind == SealedRumorEvent.KIND) {
                        val decryptedSealData = acc.signer.signerSync.decrypt(event.content, acc.hexKey)
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
                    ClearTextEncryptedDataKind(bunkerRequest.message, result)
                }
            } else if (bunkerRequest.message.startsWith("[")) {
                val tagArray = JacksonMapper.fromJsonToTagArray(bunkerRequest.message)
                TagArrayEncryptedDataKind(tagArray, result)
            } else {
                ClearTextEncryptedDataKind(bunkerRequest.message, result)
            }
        } else if (bunkerRequest is BunkerRequestNip04Decrypt) {
            val result = acc.signer.signerSync.nip04Decrypt(bunkerRequest.ciphertext, bunkerRequest.pubKey)

            if (result.startsWith("{")) {
                val event = AmberEvent.fromJson(result)
                if (event.kind == SealedRumorEvent.KIND) {
                    val decryptedSealData = acc.signer.signerSync.decrypt(event.content, acc.hexKey)
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
                ClearTextEncryptedDataKind(bunkerRequest.ciphertext, result)
            }
        } else if (bunkerRequest is BunkerRequestNip04Encrypt) {
            val result = acc.signer.signerSync.nip04Encrypt(bunkerRequest.message, bunkerRequest.pubKey)
            if (bunkerRequest.message.startsWith("{")) {
                try {
                    val event = AmberEvent.fromJson(bunkerRequest.message)
                    if (event.kind == SealedRumorEvent.KIND) {
                        val decryptedSealData = acc.signer.signerSync.decrypt(event.content, acc.hexKey)
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
                    ClearTextEncryptedDataKind(bunkerRequest.message, result)
                }
            } else if (bunkerRequest.message.startsWith("[")) {
                val tagArray = JacksonMapper.fromJsonToTagArray(bunkerRequest.message)
                TagArrayEncryptedDataKind(tagArray, result)
            } else {
                ClearTextEncryptedDataKind(bunkerRequest.message, result)
            }
        } else {
            null
        }

        val type = BunkerRequestUtils.getTypeFromBunker(bunkerRequest)
        if (type == SignerType.INVALID) {
            Log.d(Amber.TAG, "Invalid request method ${bunkerRequest.method}")
            logDao.insertLog(
                LogEntity(
                    0,
                    "nostrsigner",
                    "bunker request",
                    "Invalid request method ${bunkerRequest.method}",
                    System.currentTimeMillis(),
                ),
            )
            return
        }

        val request = AmberBunkerRequest(
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
        val data = BunkerRequestUtils.getDataFromBunker(bunkerRequest)
        val projection = if (type == SignerType.GET_PUBLIC_KEY || type == SignerType.PING) {
            arrayOf(acc.npub)
        } else {
            arrayOf(data, pubKey, acc.npub)
        }
        val cursor =
            applicationContext.contentResolver.query(
                "content://${BuildConfig.APPLICATION_ID}.$type".toUri(),
                projection,
                null,
                null,
                event.pubKey,
            )
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

        cursor.use { localCursor ->
            if (localCursor == null) {
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

    fun notificationManager(): NotificationManager {
        return ContextCompat.getSystemService(applicationContext, NotificationManager::class.java) as NotificationManager
    }
}

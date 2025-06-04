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
import com.greenart7c3.nostrsigner.database.HistoryEntity2
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.database.NotificationEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.BunkerRequest
import com.greenart7c3.nostrsigner.models.EncryptionType
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.service.NotificationUtils.sendNotification
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.tags.people.taggedUsers
import com.vitorpamplona.quartz.nip01Core.verify
import com.vitorpamplona.quartz.nip04Dm.crypto.EncryptedInfo
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.launch

class EventNotificationConsumer(private val applicationContext: Context) {
    private fun saveLog(text: String) {
        Log.d(Amber.TAG, text)
        Amber.instance.applicationIOScope.launch {
            val accounts = LocalPreferences.allSavedAccounts(applicationContext)
            accounts.forEach {
                LocalPreferences.loadFromEncryptedStorage(applicationContext, it.npub)?.let { acc ->
                    val dao = Amber.instance.getDatabase(acc.npub).applicationDao()
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

    fun consume(event: Event, relay: Relay) {
        saveLog("New event ${event.toJson()}")

        if (!notificationManager().areNotificationsEnabled()) {
            saveLog("notifications disabled")
            return
        }
        if (event.kind != 24133) {
            saveLog("Not a bunker request")
            return
        }
        if (!event.verify()) {
            saveLog("Invalid id hash or signature")
            return
        }

        val taggedKey = event.taggedUsers().firstOrNull() ?: return
        LocalPreferences.loadFromEncryptedStorageSync(applicationContext, taggedKey.toNPub())?.let { acc ->
            notify(event, acc, relay)
        }
    }

    private fun notify(
        event: Event,
        acc: Account,
        relay: Relay,
    ) {
        if (event.content.isEmpty()) return

        val dao = Amber.instance.getDatabase(acc.npub).applicationDao()
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

        if (EncryptedInfo.isNIP04(event.content)) {
            acc.signer.nip04Decrypt(event.content, event.pubKey) {
                Amber.instance.applicationIOScope.launch {
                    notify(event, acc, it, EncryptionType.NIP04, relay)
                }
            }
        } else {
            acc.signer.nip44Decrypt(event.content, event.pubKey) {
                Amber.instance.applicationIOScope.launch {
                    notify(event, acc, it, EncryptionType.NIP44, relay)
                }
            }
        }
    }

    private suspend fun notify(
        event: Event,
        acc: Account,
        request: String,
        encryptionType: EncryptionType,
        relay: Relay,
    ) {
        val responseRelay = listOf(RelaySetupInfo(relay.url, read = true, write = true, feedTypes = COMMON_FEED_TYPES))
        val dao = Amber.instance.getDatabase(acc.npub).applicationDao()
        val notification = dao.getNotification(event.id)
        if (notification != null) return
        dao.insertNotification(NotificationEntity(0, event.id, event.createdAt))

        dao.insertLog(
            LogEntity(
                0,
                "nostrsigner",
                "bunker request",
                request,
                System.currentTimeMillis(),
            ),
        )

        val bunkerRequest = BunkerRequest.mapper.readValue(request, BunkerRequest::class.java)
        bunkerRequest.localKey = event.pubKey
        bunkerRequest.currentAccount = acc.npub
        bunkerRequest.encryptionType = encryptionType

        val type = BunkerRequestUtils.getTypeFromBunker(bunkerRequest)
        val data = BunkerRequestUtils.getDataFromBunker(bunkerRequest)

        var amberEvent: AmberEvent? = null

        val pubKey =
            if (bunkerRequest.method.endsWith("encrypt") || bunkerRequest.method.endsWith("decrypt")) {
                bunkerRequest.params.first()
            } else if (bunkerRequest.method == "sign_event") {
                amberEvent = AmberEvent.fromJson(bunkerRequest.params.first())
                amberEvent.pubKey
            } else {
                ""
            }

        val database = Amber.instance.getDatabase(acc.npub)

        val permission = database.applicationDao().getByKey(bunkerRequest.localKey)
        if (permission != null && ((permission.application.secret != permission.application.key && permission.application.useSecret) || permission.application.isConnected) && type == SignerType.CONNECT) {
            Amber.instance.applicationIOScope.launch {
                database.applicationDao()
                    .addHistory(
                        HistoryEntity2(
                            0,
                            permission.application.key,
                            type.toString().toLowerCase(Locale.current),
                            amberEvent?.kind,
                            TimeUtils.now(),
                            true,
                        ),
                    )
            }
            BunkerRequestUtils.sendBunkerResponse(
                applicationContext,
                acc,
                bunkerRequest,
                BunkerResponse(bunkerRequest.id, bunkerRequest.nostrConnectSecret.ifBlank { "ack" }, null),
                permission.application.relays,
                onLoading = {},
                onDone = {},
            )
            return
        }

        var applicationWithSecret: ApplicationWithPermissions? = null
        if (type == SignerType.CONNECT) {
            val secret =
                try {
                    bunkerRequest.params[1]
                } catch (_: Exception) {
                    ""
                }
            // TODO: make secret not optional when more applications start using it
            if (secret.isNotBlank()) {
                bunkerRequest.secret = secret
                applicationWithSecret = database.applicationDao().getBySecret(secret)
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
                        applicationContext,
                        acc,
                        bunkerRequest,
                        BunkerResponse(bunkerRequest.id, "", message),
                        applicationWithSecret?.application?.relays ?: responseRelay,
                        onLoading = { },
                        onDone = { },
                    )
                    return
                }
            }
        }

        val cursor =
            applicationContext.contentResolver.query(
                "content://${BuildConfig.APPLICATION_ID}.$type".toUri(),
                arrayOf(data, pubKey, acc.npub),
                "1",
                null,
                bunkerRequest.localKey,
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
        val relays = permission?.application?.relays ?: applicationWithSecret?.application?.relays ?: responseRelay

        if (type == SignerType.INVALID) {
            Log.d(Amber.TAG, "Invalid request method ${bunkerRequest.method}")
            dao.insertLog(
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

        if (permission == null && applicationWithSecret == null) {
            BunkerRequestUtils.sendBunkerResponse(
                applicationContext,
                acc,
                bunkerRequest,
                BunkerResponse(bunkerRequest.id, "", "no permission"),
                relays,
                onLoading = { },
                onDone = { },
            )
            return
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
                        bunkerRequest,
                    )
            } else {
                if (localCursor.moveToFirst()) {
                    if (localCursor.getColumnIndex("rejected") > -1) {
                        BunkerRequestUtils.sendBunkerResponse(
                            applicationContext,
                            acc,
                            bunkerRequest,
                            BunkerResponse(bunkerRequest.id, "", "user rejected"),
                            relays,
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
                            applicationContext,
                            acc,
                            bunkerRequest,
                            BunkerResponse(bunkerRequest.id, result, null),
                            relays,
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
                            bunkerRequest,
                        )
                }
            }
        }
    }

    fun notificationManager(): NotificationManager {
        return ContextCompat.getSystemService(applicationContext, NotificationManager::class.java) as NotificationManager
    }
}

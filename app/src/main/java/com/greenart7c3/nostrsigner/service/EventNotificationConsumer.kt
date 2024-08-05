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
import android.net.Uri
import android.util.Log
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import androidx.core.content.ContextCompat
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.database.NotificationEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.BunkerRequest
import com.greenart7c3.nostrsigner.models.BunkerResponse
import com.greenart7c3.nostrsigner.models.EncryptionType
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.service.NotificationUtils.sendNotification
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.ui.NotificationType
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.SealedGossipEvent

class EventNotificationConsumer(private val applicationContext: Context) {

    fun consume(event: Event) {
        if (!notificationManager().areNotificationsEnabled()) return
        if (event.kind() != 24133) return

        // PushNotification Wraps don't include a receiver.
        // Test with all logged in accounts
        LocalPreferences.allSavedAccounts(applicationContext).forEach {
            if (it.hasPrivKey) {
                LocalPreferences.loadFromEncryptedStorage(applicationContext, it.npub)?.let { acc ->
                    notify(event, acc)
                }
            }
        }
    }

    suspend fun consume(event: GiftWrapEvent) {
        if (!notificationManager().areNotificationsEnabled()) return

        // PushNotification Wraps don't include a receiver.
        // Test with all logged in accounts
        LocalPreferences.allSavedAccounts(applicationContext).forEach {
            if (it.hasPrivKey) {
                LocalPreferences.loadFromEncryptedStorage(applicationContext, it.npub)?.let { acc ->
                    if (NostrSigner.getInstance().settings.notificationType == NotificationType.PUSH) {
                        consumeIfMatchesAccount(event, acc)
                    }
                }
            }
        }
    }

    private fun consumeIfMatchesAccount(
        pushWrappedEvent: GiftWrapEvent,
        account: Account,
    ) {
        pushWrappedEvent.cachedGift(account.signer) { notificationEvent ->
            unwrapAndConsume(notificationEvent, account) { innerEvent ->
                if (innerEvent.kind == 24133) {
                    notify(innerEvent, account)
                }
            }
        }
    }

    private fun unwrapAndConsume(
        event: Event,
        account: Account,
        onReady: (Event) -> Unit,
    ) {
        when (event) {
            is GiftWrapEvent -> {
                event.cachedGift(account.signer) { unwrapAndConsume(it, account, onReady) }
            }
            is SealedGossipEvent -> {
                event.cachedGossip(account.signer) {
                    onReady(it)
                }
            }
            else -> {
                onReady(event)
            }
        }
    }

    private fun notify(
        event: Event,
        acc: Account,
    ) {
        if (event.content.isEmpty()) return
        if (IntentUtils.isNip04(event.content)) {
            acc.signer.nip04Decrypt(event.content, event.pubKey) {
                notify(event, acc, it, EncryptionType.NIP04)
            }
        } else {
            acc.signer.nip44Decrypt(event.content, event.pubKey) {
                notify(event, acc, it, EncryptionType.NIP44)
            }
        }
    }

    private fun notify(
        event: Event,
        acc: Account,
        request: String,
        encryptionType: EncryptionType,
    ) {
        if (BuildConfig.DEBUG) {
            Log.d("bunker", event.toJson())
            Log.d("bunker", request)
        }

        val dao = NostrSigner.getInstance().getDatabase(acc.keyPair.pubKey.toNpub()).applicationDao()
        val notification = dao.getNotification(event.id)
        if (notification != null) return
        dao.insertNotification(NotificationEntity(0, event.id(), event.createdAt))

        val bunkerRequest = BunkerRequest.mapper.readValue(request, BunkerRequest::class.java)
        bunkerRequest.localKey = event.pubKey
        bunkerRequest.currentAccount = acc.keyPair.pubKey.toNpub()
        bunkerRequest.encryptionType = encryptionType

        val type = IntentUtils.getTypeFromBunker(bunkerRequest)
        val data = IntentUtils.getDataFromBunker(bunkerRequest)

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

        val database = NostrSigner.getInstance().getDatabase(acc.keyPair.pubKey.toNpub())

        val permission = database.applicationDao().getByKey(bunkerRequest.localKey)
        if (permission != null && permission.application.isConnected && type == SignerType.CONNECT) {
            database.applicationDao()
                .addHistory(
                    HistoryEntity(
                        0,
                        permission.application.key,
                        type.toString().toLowerCase(Locale.current),
                        amberEvent?.kind,
                        System.currentTimeMillis(),
                        true,
                    ),
                )
            IntentUtils.sendBunkerResponse(
                applicationContext,
                acc,
                bunkerRequest,
                BunkerResponse(bunkerRequest.id, "ack", null),
                permission.application.relays,
                onLoading = {},
                onDone = {},
                checkForEmptyRelays = false,
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
                    IntentUtils.sendBunkerResponse(
                        applicationContext,
                        acc,
                        bunkerRequest,
                        BunkerResponse(bunkerRequest.id, "", message),
                        applicationWithSecret?.application?.relays ?: listOf(),
                        onLoading = { },
                        onDone = { },
                        checkForEmptyRelays = false,
                    )
                    return
                }
            } else {
                applicationWithSecret =
                    database.applicationDao().getAllApplications().firstOrNull { localApp ->
                        !localApp.application.useSecret && localApp.application.secret == localApp.application.key
                    }

                bunkerRequest.secret = applicationWithSecret?.application?.secret ?: ""
                bunkerRequest.relays = applicationWithSecret?.application?.relays ?: bunkerRequest.relays
            }
        }

        val cursor =
            applicationContext.contentResolver.query(
                Uri.parse("content://${BuildConfig.APPLICATION_ID}.$type"),
                arrayOf(data, pubKey, acc.keyPair.pubKey.toNpub()),
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
        val relays = permission?.application?.relays ?: applicationWithSecret?.application?.relays ?: listOf()

        if (permission == null && applicationWithSecret == null && !acc.allowNewConnections) {
            IntentUtils.sendBunkerResponse(
                applicationContext,
                acc,
                bunkerRequest,
                BunkerResponse(bunkerRequest.id, "", "no permission"),
                listOf(),
                onLoading = { },
                onDone = {},
                checkForEmptyRelays = false,
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
                        "nostrsigner:",
                        "BunkerID",
                        applicationContext,
                        bunkerRequest,
                    )
            } else {
                if (localCursor.moveToFirst()) {
                    if (localCursor.getColumnIndex("rejected") > -1) {
                        IntentUtils.sendBunkerResponse(
                            applicationContext,
                            acc,
                            bunkerRequest,
                            BunkerResponse(bunkerRequest.id, "", "user rejected"),
                            relays,
                            onLoading = { },
                            onDone = { },
                            checkForEmptyRelays = false,
                        )
                    } else {
                        val index = localCursor.getColumnIndex("event")
                        val result = localCursor.getString(index)

                        IntentUtils.sendBunkerResponse(
                            applicationContext,
                            acc,
                            bunkerRequest,
                            BunkerResponse(bunkerRequest.id, result, null),
                            relays,
                            onLoading = { },
                            onDone = { },
                            checkForEmptyRelays = false,
                        )
                    }
                } else {
                    notificationManager()
                        .sendNotification(
                            bunkerRequest.id,
                            message,
                            "Bunker",
                            "nostrsigner:",
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

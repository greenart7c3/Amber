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
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.nostrsigner
import com.greenart7c3.nostrsigner.service.NotificationUtils.sendNotification
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.ui.BunkerResponse
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.GiftWrapEvent
import com.vitorpamplona.quartz.events.SealedGossipEvent

data class BunkerRequest(
    val id: String,
    val method: String,
    val params: Array<String>,
    var localKey: String,
    var relays: List<String>,
    var secret: String
) {
    fun toJson(): String {
        return mapper.writeValueAsString(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BunkerRequest

        if (id != other.id) return false
        if (method != other.method) return false
        if (!params.contentEquals(other.params)) return false
        if (localKey != other.localKey) return false
        if (relays != other.relays) return false
        if (secret != other.secret) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + method.hashCode()
        result = 31 * result + params.contentHashCode()
        result = 31 * result + localKey.hashCode()
        result = 31 * result + relays.hashCode()
        result = 31 * result + secret.hashCode()
        return result
    }

    companion object {
        val mapper: ObjectMapper =
            jacksonObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .registerModule(
                    SimpleModule()
                        .addDeserializer(BunkerRequest::class.java, BunkerRequestDeserializer())
                )

        fun fromJson(jsonObject: JsonNode): BunkerRequest {
            return BunkerRequest(
                id = jsonObject.get("id").asText().intern(),
                method = jsonObject.get("method").asText().intern(),
                params = jsonObject.get("params").asIterable().toList().map {
                    it.asText().intern()
                }.toTypedArray(),
                localKey = jsonObject.get("localKey")?.asText()?.intern() ?: "",
                relays = jsonObject.get("relays")?.asIterable()?.toList()?.map {
                    it.asText().intern()
                } ?: listOf("wss://relay.nsec.app"),
                secret = jsonObject.get("secret")?.asText()?.intern() ?: ""
            )
        }

        private class BunkerRequestDeserializer : StdDeserializer<BunkerRequest>(BunkerRequest::class.java) {
            override fun deserialize(
                jp: JsonParser,
                ctxt: DeserializationContext
            ): BunkerRequest {
                return fromJson(jp.codec.readTree(jp))
            }
        }
    }
}

class EventNotificationConsumer(private val applicationContext: Context) {
    private val groupKey = "com.greenart7c3.nostrsigner.DM_NOTIFICATION"
    suspend fun consume(event: GiftWrapEvent) {
        if (!notificationManager().areNotificationsEnabled()) return

        // PushNotification Wraps don't include a receiver.
        // Test with all logged in accounts
        LocalPreferences.allSavedAccounts().forEach {
            if (it.hasPrivKey) {
                LocalPreferences.loadFromEncryptedStorage(it.npub)?.let { acc ->
                    consumeIfMatchesAccount(event, acc)
                }
            }
        }
    }

    private suspend fun consumeIfMatchesAccount(
        pushWrappedEvent: GiftWrapEvent,
        account: Account
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
        onReady: (Event) -> Unit
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
        acc: Account
    ) {
        acc.signer.nip04Decrypt(event.content, event.pubKey) {
            Log.d("bunker", event.toJson())
            Log.d("bunker", it)

            val bunkerRequest = BunkerRequest.mapper.readValue(it, BunkerRequest::class.java)
            bunkerRequest.localKey = event.pubKey

            val type = IntentUtils.getTypeFromBunker(bunkerRequest)
            val data = IntentUtils.getDataFromBunker(bunkerRequest)

            var amberEvent: AmberEvent? = null

            val pubKey = if (bunkerRequest.method.endsWith("encrypt") || bunkerRequest.method.endsWith("decrypt")) {
                bunkerRequest.params.first()
            } else if (bunkerRequest.method == "sign_event") {
                amberEvent = AmberEvent.fromJson(bunkerRequest.params.first())
                amberEvent.pubKey
            } else {
                ""
            }

            val database = nostrsigner.instance.getDatabase(acc.keyPair.pubKey.toNpub())

            val permission = database.applicationDao().getByKey(bunkerRequest.localKey)
            val relays = permission?.application?.relays?.ifEmpty { listOf("wss://relay.nsec.app") } ?: bunkerRequest.relays

            if (permission != null && permission.application.isConnected && type == SignerType.CONNECT) {
                IntentUtils.sendBunkerResponse(
                    acc,
                    bunkerRequest.localKey,
                    BunkerResponse(bunkerRequest.id, "ack", null),
                    relays,
                    onLoading = {}
                ) { }
                return@nip04Decrypt
            }

            var applicationWithSecret: ApplicationWithPermissions? = null
            if (type == SignerType.CONNECT) {
                val secret = try {
                    bunkerRequest.params[1]
                } catch (_: Exception) {
                    ""
                }
                // TODO: make secret not optional when more applications start using it
                if (secret.isNotBlank()) {
                    bunkerRequest.secret = secret
                    applicationWithSecret = database.applicationDao().getBySecret(secret)
                    if (applicationWithSecret == null || secret.isBlank()) {
                        IntentUtils.sendBunkerResponse(
                            acc,
                            bunkerRequest.localKey,
                            BunkerResponse(bunkerRequest.id, "", "invalid secret"),
                            relays,
                            onLoading = { }
                        ) { }
                        return@nip04Decrypt
                    }
                } else {
                    applicationWithSecret = database.applicationDao().getAllApplications().firstOrNull { localApp ->
                        !localApp.application.useSecret && localApp.application.secret == localApp.application.key
                    }

                    bunkerRequest.secret = applicationWithSecret?.application?.secret ?: ""
                }
            }

            if (permission == null && applicationWithSecret == null) {
                IntentUtils.sendBunkerResponse(
                    acc,
                    bunkerRequest.localKey,
                    BunkerResponse(bunkerRequest.id, "", "no permission"),
                    relays,
                    onLoading = { }
                ) { }
                return@nip04Decrypt
            }

            val cursor = applicationContext.contentResolver.query(
                Uri.parse("content://${BuildConfig.APPLICATION_ID}.$type"),
                arrayOf(data, pubKey, acc.keyPair.pubKey.toNpub()),
                "1",
                null,
                bunkerRequest.localKey
            )

            var name = event.pubKey.toShortenHex()
            if (permission != null && permission.application.name.isNotBlank()) {
                name = permission.application.name
            } else if (applicationWithSecret != null && applicationWithSecret.application.name.isNotBlank()) {
                name = applicationWithSecret.application.name
            }

            val bunkerPermission = Permission(type.toString().toLowerCase(Locale.current), amberEvent?.kind)
            var message = "$name requests $bunkerPermission"
            if (type == SignerType.SIGN_EVENT) {
                message = "$name wants you to sign a $bunkerPermission"
            }
            if (type == SignerType.CONNECT) {
                message = "$name $bunkerPermission"
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
                            groupKey,
                            applicationContext,
                            bunkerRequest
                        )
                } else {
                    if (localCursor.moveToFirst()) {
                        if (localCursor.getColumnIndex("rejected") > -1) {
                            IntentUtils.sendBunkerResponse(
                                acc,
                                bunkerRequest.localKey,
                                BunkerResponse(bunkerRequest.id, "", "user rejected"),
                                relays,
                                onLoading = { }
                            ) { }
                        } else {
                            val index = localCursor.getColumnIndex("event")
                            val result = localCursor.getString(index)

                            IntentUtils.sendBunkerResponse(
                                acc,
                                bunkerRequest.localKey,
                                BunkerResponse(bunkerRequest.id, result, null),
                                relays,
                                onLoading = { }
                            ) { }
                        }
                    } else {
                        notificationManager()
                            .sendNotification(
                                bunkerRequest.id,
                                message,
                                "Bunker",
                                "nostrsigner:",
                                "BunkerID",
                                groupKey,
                                applicationContext,
                                bunkerRequest
                            )
                    }
                }
            }
        }
    }

    fun notificationManager(): NotificationManager {
        return ContextCompat.getSystemService(applicationContext, NotificationManager::class.java) as NotificationManager
    }
}

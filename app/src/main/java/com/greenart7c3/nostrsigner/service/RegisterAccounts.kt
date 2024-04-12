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

import android.util.Log
import com.greenart7c3.nostrsigner.AccountInfo
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.nostrsigner
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.RelayAuthEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class RegisterAccounts(
    private val accounts: List<AccountInfo>
) {
    private fun recursiveAuthCreation(
        notificationToken: String,
        remainingTos: List<Pair<Account, String>>,
        output: MutableList<RelayAuthEvent>,
        onReady: (List<RelayAuthEvent>) -> Unit
    ) {
        if (remainingTos.isEmpty()) {
            onReady(output)
            return
        }

        val next = remainingTos.first()

        next.first.createAuthEvent(next.second, notificationToken) {
            output.add(it)
            recursiveAuthCreation(notificationToken, remainingTos.filter { next != it }, output, onReady)
        }
    }

    // creates proof that it controls all accounts
    private suspend fun signEventsToProveControlOfAccounts(
        accounts: List<AccountInfo>,
        notificationToken: String,
        onReady: (List<RelayAuthEvent>) -> Unit
    ) {
        val readyToSend: MutableList<Pair<Account, String>> = mutableListOf()
        accounts.forEach {
            val acc = LocalPreferences.loadFromEncryptedStorage(it.npub)
            if (acc != null) {
                val permissions = nostrsigner.instance.databases[acc.keyPair.pubKey.toNpub()]!!.applicationDao().getAll(acc.keyPair.pubKey.toHexKey())
                permissions.forEach { permission ->
                    permission.relays.forEach { relay ->
                        readyToSend.add(
                            Pair(acc, relay)
                        )
                    }
                }
            }
        }

        val listOfAuthEvents = mutableListOf<RelayAuthEvent>()
        recursiveAuthCreation(
            notificationToken,
            readyToSend,
            listOfAuthEvents,
            onReady
        )
    }

    private fun postRegistrationEvent(events: List<RelayAuthEvent>) {
        try {
            val jsonObject =
                """{
                "events": [ ${events.joinToString(", ") { it.toJson() }} ]
            }
            """

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = jsonObject.toRequestBody(mediaType)

            val request =
                Request.Builder()
                    .header("User-Agent", "Amber/${BuildConfig.VERSION_NAME}")
                    .url("https://push.greenart7c3.com/register")
                    .post(body)
                    .build()

            val client = HttpClientManager.getHttpClient()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: java.lang.Exception) {
            if (e is CancellationException) throw e
            val tag = "FirebaseMsgService"

            Log.e(tag, "Unable to register with push server", e)
        }
    }

    suspend fun go(notificationToken: String) =
        withContext(Dispatchers.IO) {
            signEventsToProveControlOfAccounts(accounts, notificationToken) { postRegistrationEvent(it) }

            PushNotificationUtils.hasInit = true
        }
}

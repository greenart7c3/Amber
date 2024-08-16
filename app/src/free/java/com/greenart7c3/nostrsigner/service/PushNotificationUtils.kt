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

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri
import android.util.Log
import com.greenart7c3.nostrsigner.AccountInfo
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.NotificationType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object PushNotificationUtils {
    var hasInit: Boolean = false
    private val pushHandler = PushDistributorHandler
    var accountState: AccountStateViewModel? = null

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun init(accounts: List<AccountInfo>) {
        if (hasInit) {
            return
        }

        val savedDistributor = pushHandler.getSavedDistributor()
        if (savedDistributor.isBlank()) {
            val distributions = pushHandler.getInstalledDistributors()
            if (distributions.isNotEmpty()) {
                distributions.firstOrNull { it.isNotBlank() }?.let {
                    pushHandler.saveDistributor(it)
                }
            } else {
                accounts.forEach {
                    NostrSigner.getInstance().getDatabase(it.npub).applicationDao().insertLog(
                        LogEntity(
                            0,
                            "Push server",
                            "Push server",
                            "No distributors found for push notifications",
                            System.currentTimeMillis(),
                        ),
                    )
                }
                if (!NostrSigner.getInstance().settings.pushServerMessage && NostrSigner.getInstance().settings.notificationType != NotificationType.DIRECT) {
                    accountState?.toast(
                        title = NostrSigner.getInstance().getString(R.string.push_server),
                        message = NostrSigner.getInstance().getString(R.string.no_distributors_found),
                        onOk = {
                            GlobalScope.launch(Dispatchers.IO) {
                                NostrSigner.getInstance().settings = NostrSigner.getInstance().settings.copy(pushServerMessage = true)
                                LocalPreferences.saveSettingsToEncryptedStorage(NostrSigner.getInstance().settings)
                            }

                            val intent = Intent(Intent.ACTION_VIEW)
                            intent.data = Uri.parse("https://ntfy.sh/")
                            intent.flags = FLAG_ACTIVITY_NEW_TASK
                            NostrSigner.getInstance().startActivity(intent)
                        },
                    )
                }
            }
        }
        try {
            if (pushHandler.savedDistributorExists()) {
                val endpoint = pushHandler.getSavedEndpoint()
                if (endpoint.isBlank()) {
                    pushHandler.saveDistributor(pushHandler.getSavedDistributor())
                }

                RegisterAccounts(accounts).go(pushHandler.getSavedEndpoint())
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.d("Amber-OSSPushUtils", "Failed to get endpoint.")
            accounts.forEach {
                NostrSigner.getInstance().getDatabase(it.npub).applicationDao().insertLog(
                    LogEntity(
                        0,
                        "Push server",
                        "Push server",
                        "Failed to get endpoint. ${e.message}",
                        System.currentTimeMillis(),
                    ),
                )
            }
        }
    }
}

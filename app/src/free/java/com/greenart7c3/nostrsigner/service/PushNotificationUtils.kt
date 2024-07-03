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
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.database.LogEntity
import kotlinx.coroutines.CancellationException

object PushNotificationUtils {
    var hasInit: Boolean = false
    private val pushHandler = PushDistributorHandler

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
            }
        }
        try {
            if (pushHandler.savedDistributorExists()) {
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

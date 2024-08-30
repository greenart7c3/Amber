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
import com.google.firebase.messaging.FirebaseMessaging
import com.greenart7c3.nostrsigner.AccountInfo
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.ui.AccountStateViewModel
import com.greenart7c3.nostrsigner.ui.NotificationType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await

object PushNotificationUtils {
    var hasInit: Boolean = false
    var accountState: AccountStateViewModel? = null

    suspend fun init(accounts: List<AccountInfo>) =
        with(Dispatchers.IO) {
            if (hasInit || NostrSigner.getInstance().settings.notificationType == NotificationType.DIRECT) {
                return@with
            }
            // get user notification token provided by firebase
            try {
                RegisterAccounts(accounts).go(FirebaseMessaging.getInstance().token.await())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("Firebase token", "failed to get firebase token", e)
            }
        }
}

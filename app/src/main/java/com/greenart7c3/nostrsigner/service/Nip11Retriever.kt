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
import com.greenart7c3.nostrsigner.checkNotInMainThread
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.vitorpamplona.quartz.nip11RelayInfo.Nip11RelayInformation
import java.io.IOException
import kotlinx.coroutines.CancellationException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response

class Nip11Retriever {
    enum class ErrorCode {
        FAIL_TO_ASSEMBLE_URL,
        FAIL_TO_REACH_SERVER,
        FAIL_TO_PARSE_RESULT,
        FAIL_WITH_HTTP_STATUS,
    }

    fun loadRelayInfo(
        url: String,
        dirtyUrl: String,
        forceProxy: Boolean,
        onInfo: (Nip11RelayInformation) -> Unit,
        onError: (String, ErrorCode, String?) -> Unit,
    ) {
        checkNotInMainThread()
        try {
            val request: Request =
                Request
                    .Builder()
                    .header("Accept", "application/nostr+json")
                    .url(url)
                    .build()

            HttpClientManager
                .getHttpClient(forceProxy)
                .newCall(request)
                .enqueue(
                    object : Callback {
                        @Suppress("UNNECESSARY_SAFE_CALL")
                        override fun onResponse(
                            call: Call,
                            response: Response,
                        ) {
                            checkNotInMainThread()
                            response.use {
                                val body = it.body?.string() ?: ""
                                try {
                                    if (it.isSuccessful) {
                                        onInfo(Nip11RelayInformation.fromJson(body))
                                    } else {
                                        onError(dirtyUrl, ErrorCode.FAIL_WITH_HTTP_STATUS, it.code.toString())
                                    }
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Log.e(
                                        "RelayInfoFail",
                                        "Resulting Message from Relay $dirtyUrl in not parseable: $body",
                                        e,
                                    )
                                    onError(dirtyUrl, ErrorCode.FAIL_TO_PARSE_RESULT, e.message)
                                }
                            }
                        }

                        override fun onFailure(
                            call: Call,
                            e: IOException,
                        ) {
                            Log.e("RelayInfoFail", "$dirtyUrl unavailable", e)
                            onError(dirtyUrl, ErrorCode.FAIL_TO_REACH_SERVER, e.message)
                        }
                    },
                )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("RelayInfoFail", "Invalid URL $dirtyUrl", e)
            onError(dirtyUrl, ErrorCode.FAIL_TO_ASSEMBLE_URL, e.message)
        }
    }
}

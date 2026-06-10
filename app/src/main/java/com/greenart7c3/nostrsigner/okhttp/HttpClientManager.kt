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
package com.greenart7c3.nostrsigner.okhttp

import android.util.Log
import com.greenart7c3.nostrsigner.Amber
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.Duration
import javax.net.SocketFactory
import okhttp3.OkHttpClient

object HttpClientManager {
    private val rootClient by lazy {
        OkHttpClient
            .Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            // Tags the sockets OkHttp opens so StrictMode's untagged-socket
            // detector stays quiet. TaggedSocketFactory covers DIRECT/HTTP
            // routes; TaggingInterceptor (+ fastFallback(false) below) covers
            // SOCKS proxy routes, which bypass the SocketFactory.
            .socketFactory(TaggedSocketFactory(SocketFactory.getDefault()))
            .addInterceptor(TaggingInterceptor())
            .build()
    }

    val DEFAULT_TIMEOUT_ON_WIFI: Duration = Duration.ofSeconds(10L)
    val DEFAULT_TIMEOUT_ON_MOBILE: Duration = Duration.ofSeconds(30L)

    /** How often OkHttp should send WebSocket pings to detect half-closed connections. */
    private val PING_INTERVAL: Duration = Duration.ofSeconds(30L)

    private var defaultTimeout = DEFAULT_TIMEOUT_ON_WIFI
    private var defaultHttpClient: OkHttpClient? = null
    private var defaultHttpClientWithoutProxy: OkHttpClient? = null
    private var userAgent: String = "Amethyst"

    private var currentProxy: Proxy? = null

    private val cache = EncryptionKeyCache()

    private fun setDefaultProxy(proxy: Proxy?) {
        if (currentProxy != proxy) {
            Log.d(Amber.TAG, "Changing proxy to: ${proxy != null}")
            currentProxy = proxy

            // Invalidate so the proxied client is rebuilt lazily on the next
            // getHttpClient() call. Rebuilding here would force OkHttp to
            // create the default SSLSocketFactory (a ~55ms StrictMode "slow
            // call") on whatever thread mutates the proxy — often the main
            // thread. getHttpClient() only runs on network/background threads.
            defaultHttpClient = null
        }
    }

    fun setDefaultTimeout(timeout: Duration) {
        Log.d(Amber.TAG, "Changing timeout to: $timeout")
        if (defaultTimeout.seconds != timeout.seconds) {
            defaultTimeout = timeout

            // Invalidate; see setDefaultProxy for why we rebuild lazily.
            defaultHttpClient = null
            defaultHttpClientWithoutProxy = null
        }
    }

    fun setDefaultUserAgent(userAgentHeader: String) {
        Log.d(Amber.TAG, "Changing userAgent")
        if (userAgent != userAgentHeader) {
            userAgent = userAgentHeader

            // Invalidate; see setDefaultProxy for why we rebuild lazily.
            defaultHttpClient = null
            defaultHttpClientWithoutProxy = null
        }
    }

    private fun buildHttpClient(
        proxy: Proxy?,
        timeout: Duration,
    ): OkHttpClient {
        val seconds = if (proxy != null) timeout.seconds * 3 else timeout.seconds
        val duration = Duration.ofSeconds(seconds)
        val builder = rootClient
            .newBuilder()
            .proxy(proxy)
            .readTimeout(duration)
            .connectTimeout(duration)
            .writeTimeout(duration)
            // Send a WebSocket ping every 30s. Without this, OkHttp accepts
            // writes to half-closed WebSockets (returning `true` from
            // `send()`) without ever surfacing a failure, because the only
            // detection is a TCP write timeout on the next queued frame —
            // which can take ~40-60s and leaves REQ/EVENT messages stranded
            // in the outbound buffer. The periodic ping turns silent
            // half-closes into explicit `onFailure` callbacks, so Amber's
            // reconnect-with-backoff path can rebuild the socket and
            // `NostrClient.onConnected → syncFilters` can re-issue the
            // stranded subscriptions.
            .pingInterval(PING_INTERVAL)
            .addInterceptor(DefaultContentTypeInterceptor(userAgent))
            .addNetworkInterceptor(LoggingInterceptor())
            .addNetworkInterceptor(EncryptedBlobInterceptor(cache))

        // SOCKS proxy (Tor) sockets are built via Socket(proxy), bypassing our
        // SocketFactory, and the relay path is WebSockets, whose connection
        // call drops our EventListener. Disabling fast fallback routes
        // connection setup through SequentialExchangeFinder, which connects
        // synchronously on the call thread — the same thread TaggingInterceptor
        // already tagged — so the socket gets tagged. Only proxied clients need
        // this; a single localhost SOCKS route gains nothing from fast fallback
        // anyway. Direct clients keep it (Happy Eyeballs) and rely on
        // TaggedSocketFactory.
        if (proxy != null) {
            builder.disableFastFallback()
        }

        return builder.build()
    }

    /**
     * Turns off OkHttp 5's fast fallback (parallel IPv4/IPv6 connect racing on
     * background threads). Reflective because the offline flavor compiles
     * against OkHttp 4 (pulled in transitively by Coil), which has no
     * `fastFallback` and already connects sequentially — so doing nothing there
     * is correct.
     */
    private fun OkHttpClient.Builder.disableFastFallback() {
        runCatching {
            OkHttpClient.Builder::class.java
                .getMethod("fastFallback", Boolean::class.javaPrimitiveType)
                .invoke(this, false)
        }
    }

    fun getHttpClient(useProxy: Boolean): OkHttpClient = if (useProxy) {
        if (defaultHttpClient == null) {
            defaultHttpClient = buildHttpClient(currentProxy, defaultTimeout)
        }
        defaultHttpClient!!
    } else {
        if (defaultHttpClientWithoutProxy == null) {
            defaultHttpClientWithoutProxy = buildHttpClient(null, defaultTimeout)
        }
        defaultHttpClientWithoutProxy!!
    }

    fun setDefaultProxyOnPort(port: Int) {
        setDefaultProxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", port)))
    }

    fun clearProxy() {
        setDefaultProxy(null)
    }
}

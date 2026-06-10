package com.greenart7c3.nostrsigner.okhttp

import android.net.TrafficStats
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Tags the calling thread with a TrafficStats stats tag at the start of every
 * call, so sockets opened while serving it are attributed to us and
 * StrictMode's untagged-socket detector stays quiet.
 *
 * This is an application interceptor (not a network interceptor or an
 * EventListener) on purpose:
 *  - It survives WebSocket calls. RealWebSocket.connect rebuilds the client
 *    with eventListener(EventListener.NONE), so an EventListener never fires
 *    for relay sockets; application interceptors are preserved.
 *  - Together with fastFallback(false) on proxied clients, connection setup
 *    runs synchronously on this same thread (SequentialExchangeFinder), so the
 *    tag set here is still in effect when the raw socket fd is created — even
 *    for SOCKS proxy (Tor) routes, which OkHttp opens via Socket(proxy) and
 *    which bypass the SocketFactory.
 */
class TaggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (TrafficStats.getThreadStatsTag() == -1) {
            TrafficStats.setThreadStatsTag(0x0001)
        }
        return chain.proceed(chain.request())
    }
}

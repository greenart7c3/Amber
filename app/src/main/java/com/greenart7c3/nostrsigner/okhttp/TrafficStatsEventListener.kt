package com.greenart7c3.nostrsigner.okhttp

import android.net.TrafficStats
import java.net.InetSocketAddress
import java.net.Proxy
import okhttp3.Call
import okhttp3.EventListener

/**
 * Tags the calling thread with a TrafficStats stats tag so the sockets OkHttp
 * opens are attributed to us, keeping StrictMode's untagged-socket detector
 * quiet.
 *
 * `dnsStart`/`connectStart` fire from OkHttp's route-planning and
 * `ConnectPlan.connectTcp` on the *same* (TaskRunner) thread that immediately
 * goes on to open the DNS/raw socket fd — and they fire for every route type,
 * including SOCKS proxies, where OkHttp builds the socket via `Socket(proxy)`
 * directly and bypasses the configured SocketFactory. A SocketFactory only
 * covers DIRECT routes; an EventListener covers them all.
 */
class TrafficStatsEventListener : EventListener() {
    private fun tagThread() {
        if (TrafficStats.getThreadStatsTag() == -1) {
            TrafficStats.setThreadStatsTag(0x0001)
        }
    }

    override fun dnsStart(call: Call, domainName: String) = tagThread()

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) = tagThread()
}

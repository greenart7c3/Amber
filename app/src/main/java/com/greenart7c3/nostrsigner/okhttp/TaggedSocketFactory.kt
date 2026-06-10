package com.greenart7c3.nostrsigner.okhttp

import android.net.TrafficStats
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * Tags the connecting thread with a TrafficStats stats tag so the raw socket
 * OkHttp opens for a DIRECT or HTTP-proxy route is attributed to us, keeping
 * StrictMode's untagged-socket detector quiet.
 *
 * OkHttp calls createSocket() on the same thread that then opens the socket fd
 * (ConnectPlan.connectSocket), so setting the thread tag here covers that fd
 * whether it is created eagerly (the host/port overloads) or lazily on first
 * use (the no-arg overload OkHttp actually uses).
 *
 * SOCKS proxy routes bypass the SocketFactory entirely — OkHttp builds those
 * sockets with Socket(proxy) directly — so they are handled separately by
 * [TaggingInterceptor] together with fastFallback(false) on proxied clients.
 */
class TaggedSocketFactory(private val delegate: SocketFactory) : SocketFactory() {
    private fun tagThread() {
        if (TrafficStats.getThreadStatsTag() == -1) {
            TrafficStats.setThreadStatsTag(0x0001)
        }
    }

    override fun createSocket(): Socket {
        tagThread()
        return delegate.createSocket()
    }

    override fun createSocket(host: String?, port: Int): Socket {
        tagThread()
        return delegate.createSocket(host, port)
    }

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
        tagThread()
        return delegate.createSocket(host, port, localHost, localPort)
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket {
        tagThread()
        return delegate.createSocket(host, port)
    }

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
        tagThread()
        return delegate.createSocket(address, port, localAddress, localPort)
    }
}

package com.greenart7c3.nostrsigner.okhttp

import android.net.TrafficStats
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

class TaggedSocketFactory(private val delegate: SocketFactory) : SocketFactory() {
    private fun tag() {
        if (TrafficStats.getThreadStatsTag() == -1) {
            TrafficStats.setThreadStatsTag(0x0001)
        }
    }

    override fun createSocket(): Socket {
        tag()
        return delegate.createSocket()
    }

    override fun createSocket(host: String?, port: Int): Socket {
        tag()
        val socket = delegate.createSocket(host, port)
        tagSocket(socket)
        return socket
    }

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
        tag()
        val socket = delegate.createSocket(host, port, localHost, localPort)
        tagSocket(socket)
        return socket
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket {
        tag()
        val socket = delegate.createSocket(host, port)
        tagSocket(socket)
        return socket
    }

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
        tag()
        val socket = delegate.createSocket(address, port, localAddress, localPort)
        tagSocket(socket)
        return socket
    }

    private fun tagSocket(socket: Socket) {
        if (socket.isConnected || socket.isBound) {
            TrafficStats.tagSocket(socket)
        }
    }
}

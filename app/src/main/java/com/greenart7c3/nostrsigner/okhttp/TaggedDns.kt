package com.greenart7c3.nostrsigner.okhttp

import android.net.TrafficStats
import java.net.InetAddress
import okhttp3.Dns

class TaggedDns(private val delegate: Dns) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        if (TrafficStats.getThreadStatsTag() == -1) {
            TrafficStats.setThreadStatsTag(0x0001)
        }
        return delegate.lookup(hostname)
    }
}

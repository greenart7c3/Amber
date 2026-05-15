package com.greenart7c3.nostrsigner.service

import java.net.URI

object RelayUrlUtils {
    /**
     * Normalizes a relay URL/host into a comparable "host[:port]" form used by
     * the auth whitelist and per-relay permission lookups. A bare host:port
     * input (e.g. "relay.example.com:8080") would otherwise be parsed by
     * java.net.URI as a scheme, so we prepend "wss://" when no scheme is
     * present.
     */
    fun extractHostAndPort(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val trimmed = input.trim().trimEnd('/')
        return try {
            val withScheme = if (trimmed.contains("://")) trimmed else "wss://$trimmed"
            val uri = URI(withScheme)
            val host = uri.host
            when {
                host.isNullOrBlank() -> trimmed
                uri.port != -1 -> "$host:${uri.port}"
                else -> host
            }
        } catch (e: Exception) {
            trimmed
        }
    }
}

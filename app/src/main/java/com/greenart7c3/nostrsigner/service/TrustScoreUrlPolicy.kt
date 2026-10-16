package com.greenart7c3.nostrsigner.service

import java.net.URI

object TrustScoreUrlPolicy {
    // Private, home-network and anonymity endpoints must never be sent to the
    // ratings API.
    private val EXCLUDED_SUFFIXES = listOf(".onion", ".i2p", ".loki", ".local", ".localhost", ".internal", ".lan", ".home.arpa", ".home")

    fun publicRelayUrl(value: String): String? = runCatching {
        val uri = URI(value)
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "ws" && scheme != "wss") return null
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
        // Endpoint credentials and anything carrying per-user data in the URL
        // itself must never be sent. Paths are dropped rather than rejected:
        // many public relays live at non-root paths, and only the host
        // identifies the relay to the API.
        if (uri.userInfo != null || uri.query != null || uri.fragment != null) return null
        if (!host.contains('.') || host.contains(':') || host.all { it.isDigit() || it == '.' }) return null
        if (EXCLUDED_SUFFIXES.any { host.endsWith(it) }) return null
        // Drop default ports so one relay has exactly one canonical URL —
        // otherwise wss://relay.example and wss://relay.example:443 get two
        // cache keys and two different API queries.
        val port = when {
            scheme == "wss" && uri.port == 443 -> -1
            scheme == "ws" && uri.port == 80 -> -1
            else -> uri.port
        }
        URI(scheme, null, host, port, null, null, null).toASCIIString()
    }.getOrNull()
}

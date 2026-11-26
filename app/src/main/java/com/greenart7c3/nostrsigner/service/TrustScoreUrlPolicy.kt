package com.greenart7c3.nostrsigner.service

import java.net.URI

object TrustScoreUrlPolicy {
    private val PRIVATE_HOME_NETWORK_AND_ANONYMITY_SUFFIXES = listOf(".onion", ".i2p", ".loki", ".local", ".localhost", ".internal", ".lan", ".home.arpa", ".home")

    private fun isDottedDomainNameNotIpLiteral(host: String): Boolean {
        val isIpAddress = host.contains(':') || host.all { it.isDigit() || it == '.' }
        return host.contains('.') && !isIpAddress
    }

    /**
     * The canonical URL of a public relay to send to the ratings API, or null when [value]
     * must never be sent: not ws/wss, private, home-network and anonymity hosts, IP literals,
     * and URLs with credentials, a query or a fragment (per-user data). Paths are dropped
     * rather than rejected, since many public relays live at non-root paths and only the
     * host identifies the relay. Default ports are dropped too, so `wss://relay.example`
     * and `wss://relay.example:443` share one cache key and one API query.
     */
    fun publicRelayUrl(value: String): String? = runCatching {
        val uri = URI(value)
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "ws" && scheme != "wss") return null
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
        if (uri.userInfo != null || uri.query != null || uri.fragment != null) return null
        if (!isDottedDomainNameNotIpLiteral(host)) return null
        if (PRIVATE_HOME_NETWORK_AND_ANONYMITY_SUFFIXES.any { host.endsWith(it) }) return null
        val port = when (scheme) {
            "wss" if uri.port == 443 -> -1
            "ws" if uri.port == 80 -> -1
            else -> uri.port
        }
        URI(scheme, null, host, port, null, null, null).toASCIIString()
    }.getOrNull()
}

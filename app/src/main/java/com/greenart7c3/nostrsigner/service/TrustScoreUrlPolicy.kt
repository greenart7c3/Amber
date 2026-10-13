package com.greenart7c3.nostrsigner.service

import java.net.URI

object TrustScoreUrlPolicy {
    fun publicRelayUrl(value: String): String? = runCatching {
        val uri = URI(value)
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "ws" && scheme != "wss") return null
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
        // Private endpoints and endpoint credentials must never be sent to the ratings API.
        if (uri.userInfo != null || uri.query != null || uri.fragment != null) return null
        if (!uri.path.isNullOrEmpty() && uri.path != "/") return null
        if (!host.contains('.') || host.contains(':') || host.all { it.isDigit() || it == '.' }) return null
        if (listOf(".onion", ".local", ".localhost", ".internal").any { host.endsWith(it) }) return null
        URI(scheme, null, host, uri.port, null, null, null).toASCIIString()
    }.getOrNull()
}

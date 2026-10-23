package com.greenart7c3.nostrsigner.service

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Decides whether a relay, image or WebDAV URL points at this device or the
 * local network, which skips the Tor proxy.
 *
 * The decision is made on the host OkHttp will actually dial: the URL is
 * parsed with HttpUrl (ws/wss mapped to http/https, bare `host[:port]` input
 * treated as http), never by substring matching, so a public host whose path,
 * query or subdomain merely contains "localhost" or "192.168." still goes
 * through Tor. Unparseable input is not local. Only `localhost` and IPv4
 * literals in 127.0.0.0/8, 192.168.0.0/16 and 172.16.0.0/12 count; there is
 * no DNS lookup.
 */
object PrivateAddress {
    private val ipv4Literal = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    fun isPrivate(url: String): Boolean {
        val host = hostOf(url) ?: return false
        if (host == "localhost") return true
        if (!ipv4Literal.matches(host)) return false
        val octets = host.split('.').map { it.toInt() }
        if (octets.any { it > 255 }) return false
        val (a, b) = octets
        return a == 127 || (a == 192 && b == 168) || (a == 172 && b in 16..31)
    }

    private fun hostOf(url: String): String? {
        val trimmed = url.trim()
        val httpForm = when {
            trimmed.startsWith("wss://", ignoreCase = true) -> "https://" + trimmed.substring(6)
            trimmed.startsWith("ws://", ignoreCase = true) -> "http://" + trimmed.substring(5)
            trimmed.contains("://") -> trimmed
            else -> "http://$trimmed"
        }
        return httpForm.toHttpUrlOrNull()?.host?.lowercase()
    }
}

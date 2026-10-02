package com.greenart7c3.nostrsigner.desktop.core

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/** Mirrors the Android `TorMode`; EXTERNAL is the desktop take on ORBOT (a system tor / Tor Browser SOCKS port). */
enum class TorMode {
    DISABLED,
    EXTERNAL,
    BUILTIN,
}

/**
 * Relays on this machine or the local network (localhost, LAN / VPN
 * addresses, mDNS `.local` names). They are always dialed directly — Tor
 * exits cannot reach them — accept plain `ws://` without a warning, and stay
 * connected when the internet goes away.
 */
object LocalRelays {
    private val ipv4Literal = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val localSuffixes = listOf(".localhost", ".local", ".lan", ".home.arpa", ".internal")

    /** Host part of a relay URL or bare `host[:port][/path]` input, lowercased; null if empty. */
    fun hostOf(url: String): String? {
        val afterScheme = url.trim().substringAfter("://")
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
        val host = when {
            authority.startsWith("[") -> authority.substringAfter('[').substringBefore(']')
            // Bare IPv6 without brackets (no port possible to tell apart).
            authority.count { it == ':' } > 1 -> authority
            else -> authority.substringBefore(':')
        }
        return host.lowercase().trimEnd('.').ifBlank { null }
    }

    fun isLocal(url: NormalizedRelayUrl): Boolean = isLocal(url.url)

    fun isLocal(url: String): Boolean {
        val host = hostOf(url) ?: return false
        if (host == "localhost" || localSuffixes.any { host.endsWith(it) }) return true
        if (!ipv4Literal.matches(host) && !host.contains(':')) return false
        // An IP literal: getByName parses it without a DNS lookup.
        val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
        if (address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress || address.isAnyLocalAddress) return true
        val bytes = address.address
        return when (bytes.size) {
            // 100.64.0.0/10 — carrier-grade NAT range used by Tailscale and similar overlays.
            4 -> (bytes[0].toInt() and 0xFF) == 100 && (bytes[1].toInt() and 0xC0) == 64
            // fc00::/7 — IPv6 unique local addresses.
            16 -> (bytes[0].toInt() and 0xFE) == 0xFC
            else -> false
        }
    }

    fun isOnion(url: String): Boolean = hostOf(url)?.endsWith(".onion") == true

    /**
     * Desktop port of the Android GHSA-8844-q5vh-9j8f warning: an explicit
     * `ws://` relay on the public internet carries NIP-46 metadata and
     * ciphertext in cleartext. Fine for `.onion` and local relays.
     */
    fun isInsecure(url: String): Boolean = url.trim().startsWith("ws://", ignoreCase = true) && !isOnion(url) && !isLocal(url)
}

/**
 * Builds the OkHttp client for each relay dial, routing it through Tor when
 * enabled. Desktop counterpart of the Android `HttpClientManager` + the
 * `Amber.factory` routing rule.
 */
object RelayHttpClients {
    const val LOCALHOST = "127.0.0.1"

    private val direct: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .pingInterval(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val proxied = ConcurrentHashMap<Int, OkHttpClient>()

    /**
     * The SOCKS port a relay must be dialed through, or null to dial it
     * directly. Local relays always go direct. Built-in Tor returns port 0
     * while the daemon is not up yet: a fail-closed placeholder that refuses
     * every connection instead of leaking it to the clearnet.
     */
    fun socksPortFor(url: String, mode: TorMode, externalPort: Int, builtinPort: Int): Int? = when {
        LocalRelays.isLocal(url) -> null
        mode == TorMode.DISABLED -> null
        mode == TorMode.EXTERNAL -> externalPort
        else -> builtinPort
    }

    fun clientFor(url: NormalizedRelayUrl): OkHttpClient = clientFor(url.url)

    /**
     * Client for any URL (relays and plain HTTP fetches such as profile
     * pictures), so everything follows the same Tor routing rule.
     */
    fun clientFor(url: String): OkHttpClient {
        val settings = SettingsStore.settings.value
        val port = socksPortFor(url, settings.torMode, settings.proxyPort, TorManager.socksPort.value) ?: return direct
        return proxied.computeIfAbsent(port) {
            // Tor circuits are slower to establish; triple the timeouts like Android.
            // An explicit proxy also means OkHttp never falls back to a direct
            // route, and SOCKS hosts are passed unresolved so DNS goes through Tor.
            direct.newBuilder()
                .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(LOCALHOST, port)))
                .readTimeout(90, TimeUnit.SECONDS)
                .connectTimeout(90, TimeUnit.SECONDS)
                .build()
        }
    }

    /** Mirrors `Amber.isSocksProxyAlive`: whether something listens on the SOCKS port. */
    fun isSocksProxyAlive(port: Int): Boolean {
        if (port !in 1..65535) return false
        return runCatching {
            Socket().use { it.connect(InetSocketAddress(LOCALHOST, port), 3000) }
            true
        }.getOrDefault(false)
    }
}

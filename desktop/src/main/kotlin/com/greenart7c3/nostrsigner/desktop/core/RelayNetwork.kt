package com.greenart7c3.nostrsigner.desktop.core

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

/** Mirrors the Android `TorMode`; EXTERNAL is the desktop take on ORBOT (a system tor / Tor Browser SOCKS port). */
enum class TorMode {
    DISABLED,
    EXTERNAL,
    BUILTIN,
}

/**
 * Relays on this machine or the local network (localhost, LAN / VPN
 * addresses, mDNS `.local` names; those names are resolved by
 * [LocalNameDns], never the system resolver). Tor exits cannot reach them — accept
 * plain `ws://` without a warning, and stay
 * connected when the internet goes away.
 */
object LocalRelays {
    private val ipv4Literal = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val localSuffixes = listOf(".localhost", ".local")

    /**
     * Host part of a relay URL or bare `host[:port][/path]` input, lowercased; null if
     * empty or unparseable. Parsed with OkHttp's HttpUrl so the Tor-bypass decision is
     * made on exactly the host OkHttp will dial: a hand-rolled authority parser
     * disagreed on inputs like `https://attacker.example\@10.0.0.1/` (OkHttp ends the
     * authority at the backslash), sending a public host direct while Tor was on.
     */
    fun hostOf(url: String): String? {
        val trimmed = url.trim()
        val httpForm = when {
            trimmed.startsWith("wss://", ignoreCase = true) -> "https://" + trimmed.substring(6)
            trimmed.startsWith("ws://", ignoreCase = true) -> "http://" + trimmed.substring(5)
            trimmed.contains("://") -> trimmed
            isUnbracketedIpv6(trimmed) -> "http://[$trimmed]"
            else -> "http://$trimmed"
        }
        return httpForm.toHttpUrlOrNull()?.host?.lowercase()?.trimEnd('.')?.ifBlank { null }
    }

    private fun isUnbracketedIpv6(text: String): Boolean = text.count { it == ':' } > 1 && !text.startsWith("[")

    fun isLocal(url: NormalizedRelayUrl): Boolean = isLocal(url.url)

    @Suppress("NoComments")
    fun isLocal(url: String): Boolean {
        val host = hostOf(url) ?: return false
        if (host == "localhost" || localSuffixes.any { host.endsWith(it) }) return true
        if (!isIpLiteral(host)) return false
        // getByName parses an IP literal without a DNS lookup.
        val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
        if (address.isLocalAddress()) return true
        val bytes = address.address
        return when (bytes.size) {
            4 -> isCarrierGradeNatIpv4(bytes)
            16 -> isUniqueLocalIpv6(bytes)
            else -> false
        }
    }

    private fun isIpLiteral(host: String): Boolean = ipv4Literal.matches(host) || host.contains(':')

    private fun isCarrierGradeNatIpv4(bytes: ByteArray): Boolean = (bytes[0].toInt() and 0xFF) == 100 && (bytes[1].toInt() and 0xC0) == 64

    private fun isUniqueLocalIpv6(bytes: ByteArray): Boolean = (bytes[0].toInt() and 0xFE) == 0xFC

    private fun InetAddress.isLocalAddress(): Boolean = isLoopbackAddress || isSiteLocalAddress || isLinkLocalAddress || isAnyLocalAddress

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

    private const val DIRECT_TIMEOUT_SECONDS = 30L
    private const val TOR_TIMEOUT_SECONDS = DIRECT_TIMEOUT_SECONDS * 3

    @Suppress("NoComments")
    private val direct: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // `.localhost` and `.local` never reach the system resolver (see
            // LocalNameDns); proxied clients hand hosts unresolved to Tor.
            .dns(LocalNameDns)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", "Amber/${BuildVariant.version}").build())
            }
            .pingInterval(30, TimeUnit.SECONDS)
            .readTimeout(DIRECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(DIRECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Direct client for local URLs, which skip Tor. It never follows redirects:
     * a hop is dialed before anything could re-run the routing rule, so a local
     * host redirecting to a public one would send that request around Tor and
     * reveal the user's IP.
     */
    @Suppress("NoComments")
    private val local: OkHttpClient by lazy {
        direct.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
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
    @Suppress("NoComments")
    fun clientFor(url: String): OkHttpClient {
        if (LocalRelays.isLocal(url)) return local
        val settings = SettingsStore.settings.value
        val port = socksPortFor(url, settings.torMode, settings.proxyPort, TorManager.socksPort.value) ?: return direct
        return proxied.computeIfAbsent(port) {
            // An explicit proxy means OkHttp never falls back to a direct
            // route, and SOCKS hosts are passed unresolved so DNS goes through Tor.
            direct.newBuilder()
                .proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(LOCALHOST, port)))
                .readTimeout(TOR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .connectTimeout(TOR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        }
    }

    /** Mirrors `Amber.isSocksProxyAlive`: whether something listens on the SOCKS port. */
    fun isSocksProxyAlive(port: Int): Boolean = port in 1..65535 &&
        runCatching {
            Socket().use { it.connect(InetSocketAddress(LOCALHOST, port), 3000) }
            true
        }.getOrDefault(false)
}

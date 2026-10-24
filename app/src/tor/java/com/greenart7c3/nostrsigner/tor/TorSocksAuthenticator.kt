package com.greenart7c3.nostrsigner.tor

import java.net.Authenticator
import java.net.PasswordAuthentication
import java.security.SecureRandom

/**
 * Hands the built-in Tor SOCKS listener's credentials to Java's SOCKS client.
 *
 * Arti's listener requires RFC 1929 username/password auth with a secret that
 * is fresh for every listener, so other apps on the device can neither use
 * Amber's Tor client nor confirm which port it is. OkHttp has no SOCKS client of
 * its own: it dials through `java.net`, which asks the default [Authenticator]
 * for SOCKS5 credentials. This answers only for the current listener's
 * loopback port and returns null for anything else (Orbot, other proxies).
 */
object TorSocksAuthenticator : Authenticator() {
    private class Credentials(val port: Int, val username: String, val password: CharArray)

    @Volatile private var current: Credentials? = null

    private val random = SecureRandom()

    /** Makes this the process-wide default; idempotent. */
    fun install() {
        setDefault(this)
    }

    /** A new random username and password for a listener about to bind [port]. */
    fun newCredentials(): Pair<String, String> = randomToken() to randomToken()

    /** Publishes the credentials of the listener now bound on [port]. */
    fun set(port: Int, username: String, password: String) {
        current = Credentials(port, username, password.toCharArray())
    }

    /** Forgets the credentials once the listener is gone. */
    fun clear() {
        current = null
    }

    override fun getPasswordAuthentication(): PasswordAuthentication? {
        val credentials = current ?: return null
        val isOurListener = requestingProtocol?.startsWith("SOCKS", ignoreCase = true) == true &&
            requestingPort == credentials.port &&
            requestingSite?.isLoopbackAddress == true
        if (!isOurListener) return null
        return PasswordAuthentication(credentials.username, credentials.password.copyOf())
    }

    private fun randomToken(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

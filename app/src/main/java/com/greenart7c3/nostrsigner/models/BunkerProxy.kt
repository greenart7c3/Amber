package com.greenart7c3.nostrsigner.models

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import java.net.URLDecoder

data class BunkerProxy(
    val remotePubKey: String,
    val relays: List<NormalizedRelayUrl>,
    val secret: String,
) {
    fun toBunkerUri(): String {
        val relayParams = relays.joinToString("") { "&relay=${it.url}" }
        val secretParam = if (secret.isNotBlank()) "&secret=$secret" else ""
        return "bunker://$remotePubKey?${relayParams.trimStart('&')}$secretParam"
    }

    companion object {
        /**
         * Parses a `bunker://` or `nostrconnect://` URI into a [BunkerProxy].
         *
         * URL-encoded inputs are decoded automatically.  For `nostrconnect://` URIs the
         * leading pubkey is treated as the remote signer pubkey (same semantics as in
         * `bunker://` URIs) because some bunker implementations advertise themselves this
         * way.
         */
        fun parse(uri: String): BunkerProxy? {
            return try {
                val decoded = decode(uri.trim())

                val scheme = when {
                    decoded.startsWith("bunker://") -> "bunker://"
                    decoded.startsWith("nostrconnect://") -> "nostrconnect://"
                    else -> return null
                }

                val withoutScheme = decoded.removePrefix(scheme)
                val qIdx = withoutScheme.indexOf('?')
                val remotePubKey = if (qIdx >= 0) withoutScheme.substring(0, qIdx) else withoutScheme
                if (remotePubKey.isBlank()) return null

                val relays = mutableListOf<NormalizedRelayUrl>()
                var secret = ""

                if (qIdx >= 0) {
                    val params = withoutScheme.substring(qIdx + 1).split("&")
                    for (param in params) {
                        val eqIdx = param.indexOf('=')
                        if (eqIdx < 0) continue
                        val key = param.substring(0, eqIdx)
                        val value = decode(param.substring(eqIdx + 1))
                        when (key) {
                            "relay" -> RelayUrlNormalizer.normalizeOrNull(value)?.let { relays.add(it) }
                            "secret" -> secret = value
                        }
                    }
                }

                BunkerProxy(remotePubKey = remotePubKey, relays = relays, secret = secret)
            } catch (_: Exception) {
                null
            }
        }

        /** URL-decodes [input] if it looks URL-encoded, otherwise returns it unchanged. */
        private fun decode(input: String): String {
            if (!input.contains('%')) return input
            return try {
                URLDecoder.decode(input.replace("+", "%2b"), "utf-8")
            } catch (_: Exception) {
                input
            }
        }
    }
}

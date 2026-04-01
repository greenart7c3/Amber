package com.greenart7c3.nostrsigner.models

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

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
        fun parse(uri: String): BunkerProxy? {
            return try {
                val cleaned = uri.trim()
                if (!cleaned.startsWith("bunker://")) return null
                val withoutScheme = cleaned.removePrefix("bunker://")
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
                        val value = param.substring(eqIdx + 1)
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
    }
}

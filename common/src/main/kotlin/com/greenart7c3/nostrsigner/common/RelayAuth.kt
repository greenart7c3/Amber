package com.greenart7c3.nostrsigner.common

import java.net.URI

/** Kind of a NIP-42 relay client authentication event. */
const val RELAY_AUTH_KIND = 22242

/**
 * Normalizes a relay URL/host into the comparable "host[:port]" form the auth
 * whitelist and per-relay relay-auth grants are keyed by. A bare host:port
 * input (e.g. "relay.example.com:8080") would otherwise be parsed by
 * java.net.URI as a scheme, so "wss://" is prepended when no scheme is present.
 */
fun relayHostAndPort(input: String?): String {
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
    } catch (_: Exception) {
        trimmed
    }
}

/** The "host[:port]" of an auth event's first relay tag, or "" when it has none. */
fun relayAuthHost(tags: Array<Array<String>>): String = relayHostAndPort(tags.firstOrNull { it.size > 1 && it[0] == "relay" }?.get(1))

/**
 * Whether a kind-22242 grant's [relay] covers every relay: an explicit "*", or
 * the blank relay the basic sign policy stores.
 */
fun isAllRelaysGrant(relay: String?): Boolean = relay == "*" || relay.isNullOrEmpty()

/**
 * Whether the auth whitelist refuses a relay auth (kind 22242) for
 * [relayHost]: a non-empty whitelist rejects every relay outside it, without
 * asking. Membership never authorizes signing by itself.
 */
fun isBlockedByAuthWhitelist(kind: Int?, relayHost: String, whitelist: List<String>): Boolean = kind == RELAY_AUTH_KIND && whitelist.isNotEmpty() && relayHost !in whitelist

/**
 * Whether a relay-auth choice may be remembered. A blank relay (the request
 * carried no relay tag, or a malformed one) can never match a future
 * per-relay lookup, and blank reads as the all-relays wildcard, so
 * remembering a "this relay only" choice over it would silently grant every
 * relay: such a choice is only applied this once.
 */
fun canRememberRelayAuth(allRelays: Boolean, relayHost: String): Boolean = allRelays || relayHost.isNotBlank()

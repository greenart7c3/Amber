package com.greenart7c3.nostrsigner.common

/** How far a NIP-46 request's created_at may be from now, either way, in seconds. */
const val NIP46_FRESHNESS_WINDOW = 5 * 60L

/**
 * Why a kind-24133 request created at [createdAt] (Unix seconds, with its NIP-40
 * [expiration] if any) is not processed at [now], or null when it is fresh. Stale replays
 * and future-dated events are dropped, the same on Android and desktop.
 */
fun nip46StaleReason(eventId: String, createdAt: Long, expiration: Long?, now: Long): String? = when {
    createdAt < now - NIP46_FRESHNESS_WINDOW -> "Event $eventId is too old: ${now - createdAt}s ago"
    createdAt > now + NIP46_FRESHNESS_WINDOW -> "Event $eventId is in the future: ${createdAt - now}s ahead"
    expiration != null && expiration < now -> "Event $eventId has expired"
    else -> null
}

/**
 * The result of a `switch_relays` request: the relays (by URL) the connection moves to, as
 * a JSON array, or null when there are none to move to (NIP-46: null means no change).
 */
fun switchRelaysResult(relayUrls: List<String>): String? = if (relayUrls.isEmpty()) null else relayUrls.joinToString(",", "[", "]") { "\"$it\"" }

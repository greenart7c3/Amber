package com.greenart7c3.nostrsigner.desktop.ui.components

import com.greenart7c3.nostrsigner.shared.BunkerMethod

/** Shortens a hex pubkey for display, e.g. `deadbeef01...cafebabe02`. */
fun String.shortenHex(): String = if (length > 16) "${take(8)}...${takeLast(8)}" else this

/** A human-readable description of a bunker request, for approval prompts and activity rows. */
fun bunkerMethodDescription(method: BunkerMethod, kind: Int?): String = when (method) {
    BunkerMethod.SIGN_EVENT -> "Sign event" + (kind?.let { " (kind $it)" } ?: "")
    BunkerMethod.CONNECT -> "Connect"
    BunkerMethod.GET_PUBLIC_KEY -> "Get public key"
    BunkerMethod.NIP04_ENCRYPT -> "Encrypt (NIP-04)"
    BunkerMethod.NIP04_DECRYPT -> "Decrypt (NIP-04)"
    BunkerMethod.NIP44_ENCRYPT -> "Encrypt (NIP-44)"
    BunkerMethod.NIP44_DECRYPT -> "Decrypt (NIP-44)"
    BunkerMethod.NIP44_V3_ENCRYPT -> "Encrypt (NIP-44 v3)"
    BunkerMethod.NIP44_V3_DECRYPT -> "Decrypt (NIP-44 v3)"
    BunkerMethod.DECRYPT_ZAP_EVENT -> "Decrypt zap event"
    BunkerMethod.PING -> "Ping"
    BunkerMethod.SWITCH_RELAYS -> "Switch relays"
    BunkerMethod.SIGN_PSBT -> "Sign PSBT"
    BunkerMethod.LOGOUT -> "Logout"
    BunkerMethod.INVALID -> "Unknown request"
}

/** A coarse "N units ago" string; `nowSeconds` is injectable for testing. */
fun relativeTimeFromNow(epochSeconds: Long, nowSeconds: Long = System.currentTimeMillis() / 1000): String {
    val diff = (nowSeconds - epochSeconds).coerceAtLeast(0)
    return when {
        diff < 60 -> "just now"
        diff < 3600 -> "${diff / 60}m ago"
        diff < 86400 -> "${diff / 3600}h ago"
        diff < 604800 -> "${diff / 86400}d ago"
        else -> "${diff / 604800}w ago"
    }
}

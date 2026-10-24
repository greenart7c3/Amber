package com.greenart7c3.nostrsigner.desktop.core

/**
 * Event kinds whose signature changes the user's profile, social graph,
 * relays or stored data, or grants access to another service. Approving one
 * from a notification shows nothing but the client-chosen app name, so they
 * are approved on the request card instead, where the details are visible.
 */
internal val QUICK_APPROVE_EXCLUDED_KINDS = setOf(
    0, // profile metadata
    3, // contact list
    5, // deletion
    13, // seal
    1059, // gift wrap
    10000, // mute list
    10002, // relay list
    10050, // DM relays
    13194, // NWC wallet info
    22242, // NIP-42 relay auth
    23194, // NWC request
    23195, // NWC response
    24133, // NIP-46 message
    24242, // Blossom auth
    27235, // HTTP auth
    30078, // app-specific data (Amber's own backups use it)
)

/**
 * Whether a notification or popup may offer one-click Approve for [this]
 * request: only low-risk ones. Decrypts, sign_psbt, connects, relay switches
 * and sensitive event kinds are approved on the request card.
 */
fun PendingBunkerRequest.allowsQuickApprove(): Boolean = when (type) {
    SignerType.SIGN_EVENT -> kind != null && kind !in QUICK_APPROVE_EXCLUDED_KINDS
    SignerType.NIP04_ENCRYPT, SignerType.NIP44_ENCRYPT, SignerType.NIP44_V3_ENCRYPT,
    SignerType.GET_PUBLIC_KEY, SignerType.PING,
    -> true
    else -> false
}

/**
 * The "Account: name · npub…" line a notification or popup adds when there is
 * more than one account, so the user can tell which one would sign; null with
 * a single account. Matches the request card's account label.
 */
fun PendingBunkerRequest.notificationAccountLine(accounts: List<AccountRecord>, language: String): String? {
    if (accounts.size < 2) return null
    val name = accounts.firstOrNull { it.npub == account.npub }?.name.orEmpty()
    val label = if (name.isBlank()) account.npub.toShortenHex() else "$name · ${account.npub.toShortenHex()}"
    return "${Strings.get("account", language)}: $label"
}

/** Longest app name a notification or popup shows, in characters. */
internal const val NOTIFICATION_APP_NAME_MAX = 64

/**
 * The client-chosen [name] made safe to show as a notification title: control,
 * format (bidi overrides, zero-width) and separator characters are dropped,
 * whitespace runs and line breaks become single spaces, and it is cut to
 * [max] characters, so a client cannot fake extra lines or Amber's own text.
 */
fun sanitizeAppName(name: String, max: Int = NOTIFICATION_APP_NAME_MAX): String {
    val cleaned = buildString {
        name.codePoints().forEach { cp ->
            when (Character.getType(cp).toByte()) {
                Character.CONTROL, Character.FORMAT, Character.PRIVATE_USE, Character.UNASSIGNED, Character.SURROGATE -> {
                    if (Character.isWhitespace(cp)) append(' ')
                }
                Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> append(' ')
                else -> appendCodePoint(cp)
            }
        }
    }.replace(Regex("\\s+"), " ").trim()
    if (cleaned.codePointCount(0, cleaned.length) <= max) return cleaned
    return cleaned.substring(0, cleaned.offsetByCodePoints(0, max - 1)).trimEnd() + "…"
}

/**
 * The title of a request's notification or popup: the sanitized app name, or
 * the connection key when that leaves nothing. The freedesktop summary is
 * plain text, unlike the body, so the client-chosen name goes here and the
 * body holds only Amber's own text.
 */
fun PendingBunkerRequest.notificationTitle(): String = sanitizeAppName(appName).ifBlank { localKey.toShortenHex() }

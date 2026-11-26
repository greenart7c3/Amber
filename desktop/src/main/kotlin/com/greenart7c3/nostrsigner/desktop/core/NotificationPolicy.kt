package com.greenart7c3.nostrsigner.desktop.core

import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.sanitizeAppName
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.account
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking

/**
 * Event kinds whose signature changes the user's profile, social graph,
 * relays or stored data, or grants access to another service. Approving one
 * from a notification shows nothing but the client-chosen app name, so they
 * are approved on the request card instead, where the details are visible.
 */
@Suppress("NoComments")
internal val QUICK_APPROVE_EXCLUDED_KINDS = setOf(
    PROFILE_METADATA_KIND,
    CONTACT_LIST_KIND,
    DELETION_KIND,
    SEAL_KIND,
    GIFT_WRAP_KIND,
    MUTE_LIST_KIND,
    RELAY_LIST_KIND,
    DM_RELAYS_KIND,
    NWC_WALLET_INFO_KIND,
    NIP42_RELAY_AUTH_KIND,
    NWC_REQUEST_KIND,
    NWC_RESPONSE_KIND,
    NIP46_MESSAGE_KIND,
    BLOSSOM_AUTH_KIND,
    HTTP_AUTH_KIND,
    APP_SPECIFIC_DATA_KIND_USED_BY_AMBER_BACKUPS,
)

private const val PROFILE_METADATA_KIND = 0
private const val CONTACT_LIST_KIND = 3
private const val DELETION_KIND = 5
private const val SEAL_KIND = 13
private const val GIFT_WRAP_KIND = 1059
private const val MUTE_LIST_KIND = 10000
private const val RELAY_LIST_KIND = 10002
private const val DM_RELAYS_KIND = 10050
private const val NWC_WALLET_INFO_KIND = 13194
private const val NIP42_RELAY_AUTH_KIND = 22242
private const val NWC_REQUEST_KIND = 23194
private const val NWC_RESPONSE_KIND = 23195
private const val NIP46_MESSAGE_KIND = 24133
private const val BLOSSOM_AUTH_KIND = 24242
private const val HTTP_AUTH_KIND = 27235
private const val APP_SPECIFIC_DATA_KIND_USED_BY_AMBER_BACKUPS = 30078

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
fun PendingBunkerRequest.notificationAccountLine(accounts: List<AccountRecord>): String? {
    if (accounts.size < 2) return null
    val name = accounts.firstOrNull { it.npub == account.npub }?.name.orEmpty()
    val label = if (name.isBlank()) account.npub.toShortenHex() else "$name · ${account.npub.toShortenHex()}"
    return "${getStringBlocking(Res.string.account)}: $label"
}

/**
 * The title of a request's notification or popup: the sanitized app name, or
 * the connection key when that leaves nothing. The freedesktop summary is
 * plain text, unlike the body, so the client-chosen name goes here and the
 * body holds only Amber's own text.
 */
fun PendingBunkerRequest.notificationTitle(): String = sanitizeAppName(appName).ifBlank { localKey.toShortenHex() }

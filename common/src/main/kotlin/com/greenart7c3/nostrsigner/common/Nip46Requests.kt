package com.greenart7c3.nostrsigner.common

import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest

private val nip46Methods = mapOf(
    "connect" to SignerType.CONNECT,
    "sign_event" to SignerType.SIGN_EVENT,
    "get_public_key" to SignerType.GET_PUBLIC_KEY,
    "nip04_encrypt" to SignerType.NIP04_ENCRYPT,
    "nip04_decrypt" to SignerType.NIP04_DECRYPT,
    "nip44_encrypt" to SignerType.NIP44_ENCRYPT,
    "nip44_decrypt" to SignerType.NIP44_DECRYPT,
    "nip44v3_encrypt" to SignerType.NIP44_V3_ENCRYPT,
    "nip44v3_decrypt" to SignerType.NIP44_V3_DECRYPT,
    "decrypt_zap_event" to SignerType.DECRYPT_ZAP_EVENT,
    "ping" to SignerType.PING,
    "switch_relays" to SignerType.SWITCH_RELAYS,
    "sign_psbt" to SignerType.SIGN_PSBT,
    "logout" to SignerType.LOGOUT,
)

/** The operation a NIP-46 [method] asks for; [SignerType.INVALID] for one Amber does not know. */
fun nip46SignerType(method: String): SignerType = nip46Methods[method] ?: SignerType.INVALID

/** The `kind` of a NIP-44 v3 request (`[pubkey, kind, scope, payload]`); null when missing or not a number. */
fun BunkerRequest.nip44v3Kind(): Int? = params.getOrNull(1)?.toIntOrNull()

/** The `scope` of a NIP-44 v3 request; empty when missing, per the spec. */
fun BunkerRequest.nip44v3Scope(): String = params.getOrElse(2) { "" }

/**
 * Whether a `connect` from an app already stored is a repeat that is acked
 * without asking again: the app connected before, or it is a bunker connection
 * a client already attached to (its [appKey] is no longer the placeholder
 * [appSecret] the connection was created with).
 */
fun isRepeatedConnect(
    type: SignerType,
    appKey: String,
    appSecret: String,
    appUsesSecret: Boolean,
    appConnected: Boolean,
): Boolean {
    val usesBunkerSecret = appSecret != appKey && appUsesSecret
    return type == SignerType.CONNECT && (usesBunkerSecret || appConnected)
}

/**
 * Why a `connect` carrying [secret] is refused, or null when the bunker
 * connection it names ([appFound], with its [appConnected] and [appUsesSecret]
 * state) may be attached. A secret is single use: a connection that is already
 * connected or no longer uses one refuses it.
 */
fun connectSecretRejection(
    secret: String,
    appFound: Boolean,
    appConnected: Boolean,
    appUsesSecret: Boolean,
): String? = when {
    !appFound -> "invalid secret"
    secret.isBlank() -> "no secret"
    appConnected -> "already connected"
    !appUsesSecret -> "secret not in use"
    else -> null
}

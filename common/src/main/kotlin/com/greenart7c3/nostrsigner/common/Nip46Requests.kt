package com.greenart7c3.nostrsigner.common

import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign

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

/**
 * The stored permission type(s) a requested permission (a connect's `perms`, a
 * nostrconnect URI's, the "basic actions" list) is granted under: the types the
 * request path looks up. A NIP-46 method is stored as its type (`nip44v3_encrypt`
 * as NIP44_V3_ENCRYPT), a generic `encrypt`/`decrypt` as both NIPs, and anything
 * else, Amber's content types (`encrypt_clear_text`, …) included, as-is,
 * upper-cased the same in every locale.
 */
fun expandPermissionTypes(type: String): List<String> = when (val method = type.lowercase()) {
    "encrypt" -> listOf(SignerType.NIP04_ENCRYPT.toString(), SignerType.NIP44_ENCRYPT.toString())
    "decrypt" -> listOf(SignerType.NIP04_DECRYPT.toString(), SignerType.NIP44_DECRYPT.toString())
    else -> listOf(nip46SignerType(method).takeUnless { it == SignerType.INVALID }?.toString() ?: type.uppercase())
}

/**
 * NIP-44 v3 encrypt/decrypt: granted per event kind (the v3 context), not per
 * content type. The context scope string is shown in the prompt but is not part
 * of a grant, so a remembered kind-K grant covers every scope.
 */
val nip44v3SignerTypes = setOf(SignerType.NIP44_V3_ENCRYPT, SignerType.NIP44_V3_DECRYPT)

/** The error a NIP-44 v3 request without a context kind is refused with. */
const val NIP44V3_KIND_REQUIRED = "kind is required for nip44v3"

/**
 * The error a NIP-44 v3 decrypt whose payload does not open is refused with: kept
 * generic, so the reply never tells the requester the ciphertext's embedded context.
 */
const val NIP44V3_DECRYPT_FAILED = "could not decrypt the message"

/**
 * The `kind` of a NIP-44 v3 request (`[pubkey, kind, scope, payload]`, the pubkey read by
 * [peerPubKeyParam]); null when missing or not a number.
 */
fun BunkerRequest.nip44v3Kind(): Int? = params.getOrNull(1)?.toIntOrNull()

/** The `scope` of a NIP-44 v3 request; empty when missing, per the spec. */
fun BunkerRequest.nip44v3Scope(): String = params.getOrElse(2) { "" }

/**
 * The `payload` of a NIP-44 v3 request: the base64 plaintext of an encrypt, the v3
 * ciphertext of a decrypt; empty when missing.
 */
fun BunkerRequest.nip44v3Payload(): String = params.getOrElse(3) { "" }

/**
 * The zap request event (JSON) a `decrypt_zap_event` carries; null when missing. The
 * params follow the other decrypts, `[pubkey, zap request]`, as the Android app has
 * always read them.
 */
fun BunkerRequest.zapRequestParam(): String? = params.getOrNull(1)

/**
 * The peer pubkey of an encrypt or decrypt, NIP-04, NIP-44 and NIP-44 v3 alike: the
 * first param (`[pubkey, …]`); null when missing.
 */
fun BunkerRequest.peerPubKeyParam(): String? = params.firstOrNull()

/**
 * The text a NIP-04 or NIP-44 encrypt/decrypt works on (`[pubkey, text]`): the
 * plaintext of an encrypt, the ciphertext of a decrypt; empty when missing.
 */
fun BunkerRequest.encryptionTextParam(): String = params.getOrElse(1) { "" }

/** The event (JSON) a `sign_event` asks to sign (`[event]`); null when missing. */
fun BunkerRequest.eventParam(): String? = params.firstOrNull()

/** The PSBT a `sign_psbt` asks to sign (`[psbt]`); null when missing. */
fun BunkerRequest.psbtParam(): String? = params.firstOrNull()

/** The error a request whose params are missing or unreadable is refused with. */
const val NIP46_INVALID_PARAMS = "invalid params"

/**
 * Whether the request carries the params its method needs: the peer pubkey of an
 * encrypt or decrypt, the zap request of `decrypt_zap_event`, the PSBT of `sign_psbt`,
 * and an event that parses for `sign_event`. Without them it can never be answered, so
 * both apps refuse it with [NIP46_INVALID_PARAMS] before admission, before any key is
 * used, instead of failing after it.
 */
fun BunkerRequest.hasRequiredParams(): Boolean = when (nip46SignerType(method)) {
    SignerType.SIGN_EVENT -> this is BunkerRequestSign
    SignerType.NIP04_ENCRYPT, SignerType.NIP04_DECRYPT, SignerType.NIP44_ENCRYPT, SignerType.NIP44_DECRYPT,
    SignerType.NIP44_V3_ENCRYPT, SignerType.NIP44_V3_DECRYPT,
    -> peerPubKeyParam() != null
    SignerType.SIGN_PSBT -> psbtParam() != null
    SignerType.DECRYPT_ZAP_EVENT -> zapRequestParam() != null
    else -> true
}

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

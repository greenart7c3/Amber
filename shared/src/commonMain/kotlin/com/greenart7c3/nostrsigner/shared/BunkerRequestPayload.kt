package com.greenart7c3.nostrsigner.shared

import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest

/**
 * Pure helpers for reading the fields a [BunkerRequest]'s NIP-46 `params` array carries,
 * mirroring the NIP-46 spec's per-method param layout. No signing/encryption happens here —
 * see [BunkerSigner] for that.
 */
object BunkerRequestPayload {
    /** The event JSON (for `sign_event`), ciphertext/plaintext payload, or PSBT hex a request carries, if any. */
    fun payload(bunkerRequest: BunkerRequest): String = when (bunkerMethodOf(bunkerRequest.method)) {
        BunkerMethod.SIGN_EVENT -> bunkerRequest.params.firstOrNull() ?: ""
        BunkerMethod.NIP04_ENCRYPT, BunkerMethod.NIP04_DECRYPT,
        BunkerMethod.NIP44_ENCRYPT, BunkerMethod.NIP44_DECRYPT,
        BunkerMethod.DECRYPT_ZAP_EVENT,
        -> bunkerRequest.params.getOrElse(1) { "" }
        // NIP-44 v3 NIP-46 layout: [pubkey, kind, scope, payload]. For
        // `nip44v3_encrypt` the payload is base64-encoded plaintext; for
        // `nip44v3_decrypt` it is the v3 ciphertext.
        BunkerMethod.NIP44_V3_ENCRYPT, BunkerMethod.NIP44_V3_DECRYPT -> bunkerRequest.params.getOrElse(3) { "" }
        BunkerMethod.SIGN_PSBT -> bunkerRequest.params.firstOrNull() ?: ""
        else -> ""
    }

    /** The counterparty pubkey a NIP-04/NIP-44 encrypt/decrypt request targets, if any. */
    fun counterpartyPubKey(bunkerRequest: BunkerRequest): String? = when (bunkerMethodOf(bunkerRequest.method)) {
        BunkerMethod.NIP04_ENCRYPT, BunkerMethod.NIP04_DECRYPT,
        BunkerMethod.NIP44_ENCRYPT, BunkerMethod.NIP44_DECRYPT,
        BunkerMethod.NIP44_V3_ENCRYPT, BunkerMethod.NIP44_V3_DECRYPT,
        -> bunkerRequest.params.firstOrNull()
        else -> null
    }

    /** Extract `kind` from a NIP-44 v3 bunker request; null if missing/invalid. */
    fun nip44v3Kind(bunkerRequest: BunkerRequest): Int? = bunkerRequest.params.getOrNull(1)?.toIntOrNull()

    /** Extract `scope` from a NIP-44 v3 bunker request; defaults to empty per spec. */
    fun nip44v3Scope(bunkerRequest: BunkerRequest): String = bunkerRequest.params.getOrElse(2) { "" }
}

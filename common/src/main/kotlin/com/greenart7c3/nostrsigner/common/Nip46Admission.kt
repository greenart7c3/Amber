package com.greenart7c3.nostrsigner.common

/** A saved NIP-46 connection, as the admission check needs it. */
data class Nip46Connection(
    // The client's public key, the connection's identity.
    val key: String,
    // The private key of the connection's own signer key, "" for a legacy connection.
    val localKey: String,
    val secret: String,
    val useSecret: Boolean,
    val isConnected: Boolean,
    val isExpired: Boolean,
)

/** What a decrypted NIP-46 request gets before the account key is used for its sender. */
sealed interface Nip46Admission {
    /** A connect whose secret is refused: answer with [message], published once. */
    data class RefuseConnect(val message: String) : Nip46Admission

    /** No connection and no valid connect secret: answer "no permission", published once. */
    data object Unknown : Nip46Admission

    /** Drop it: a stored app wrote to a connection key that is not its own. */
    data object Ignore : Nip46Admission

    /** An already connected client sent `connect` again: acknowledge it without asking. */
    data class AckRepeatedConnect(val connection: Nip46Connection) : Nip46Admission

    /**
     * Decide the request: [stored] is the sender's own live connection, [bySecret] the one
     * a valid connect secret names; at least one is set.
     */
    data class Admit(val stored: Nip46Connection?, val bySecret: Nip46Connection?) : Nip46Admission
}

/**
 * Whether a request of [type] from a sender may be answered with the account key, the same
 * on Android and desktop. [stored] is the connection saved under the sender's key (an
 * expired one counts as none), [routedPrivKey] the connection key the request arrived on
 * ("" when it was addressed to the account key). For a `connect`, [connectSecret] is the
 * secret it carries and [bySecret] the connection saved with that secret.
 */
fun admitNip46Request(
    type: SignerType,
    stored: Nip46Connection?,
    routedPrivKey: String,
    connectSecret: String? = null,
    bySecret: Nip46Connection? = null,
): Nip46Admission {
    val live = stored?.takeUnless { it.isExpired }
    if (live != null && connectsThroughOtherKey(live.localKey, routedPrivKey, type)) return Nip46Admission.Ignore
    if (live != null && isRepeatedConnect(type, live.key, live.secret, live.useSecret, live.isConnected)) {
        return Nip46Admission.AckRepeatedConnect(live)
    }
    if (connectSecret != null) {
        connectSecretRejection(
            connectSecret,
            appFound = bySecret != null,
            appConnected = bySecret?.isConnected == true,
            appUsesSecret = bySecret?.useSecret == true,
        )?.let { return Nip46Admission.RefuseConnect(it) }
    }
    if (live == null && bySecret == null) return Nip46Admission.Unknown
    return Nip46Admission.Admit(live, bySecret)
}

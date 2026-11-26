package com.greenart7c3.nostrsigner.common

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * A saved NIP-46 connection, as the admission check needs it: [key] is the client's public
 * key, the connection's identity, and [localKey] the private key of the connection's own
 * signer key, "" for a legacy connection.
 */
data class Nip46Connection(
    val key: String,
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

    /**
     * Drop it: a stored app wrote to a connection key that is not its own. A stored app may
     * only use its own connection key, so the request is dropped rather than answered under
     * that other connection's identity.
     */
    data object Ignore : Nip46Admission

    /** An already connected client sent `connect` again: acknowledge it without asking. */
    data class AckRepeatedConnect(val connection: Nip46Connection) : Nip46Admission

    /**
     * Decide the request: [stored] is the sender's own live connection, [bySecret] the one
     * a valid connect secret names; at least one is set.
     */
    data class Admit(val stored: Nip46Connection?, val bySecret: Nip46Connection?) : Nip46Admission
}

/** The error an unknown sender gets: no connection and no valid connect secret. */
const val NIP46_NO_PERMISSION = "no permission"

/**
 * The reply a request gets from its [Nip46Admission] alone, the same on Android and
 * desktop. Each app sends it under its own connection key and records its history.
 */
sealed interface Nip46AdmissionReply {
    /** Acknowledge a repeated connect from [connection] on [relays], with the retried reply. */
    data class Ack(val connection: Nip46Connection, val relays: List<NormalizedRelayUrl>) : Nip46AdmissionReply {
        val result: String get() = "ack"
        val attempts: Int get() = BUNKER_REPLY_ATTEMPTS
    }

    /** Refuse with [error] on [relays], published once (the sender is unverified); [log] says why. */
    data class Refuse(val error: String, val relays: List<NormalizedRelayUrl>, val log: String) : Nip46AdmissionReply {
        val attempts: Int get() = UNVERIFIED_REPLY_ATTEMPTS
    }

    /** Send nothing; [log] says why. */
    data class Drop(val log: String) : Nip46AdmissionReply
}

/**
 * The reply this admission decides, or null for [Nip46Admission.Admit], whose request is
 * decided on its grants. [storedRelays] are the relays of the sender's stored connection,
 * [bySecretRelays] those of the connection a connect secret names, and [deliveredOn] the
 * relay the request arrived on: a repeated connect is acknowledged on its connection's
 * relays (on [deliveredOn] when it has none), a refused connect answered on the relays of
 * the connection its secret names, and an unknown sender on [deliveredOn].
 */
fun Nip46Admission.reply(
    senderKey: String,
    requestId: String,
    storedRelays: List<NormalizedRelayUrl>?,
    bySecretRelays: List<NormalizedRelayUrl>?,
    deliveredOn: List<NormalizedRelayUrl>,
): Nip46AdmissionReply? = when (this) {
    is Nip46Admission.AckRepeatedConnect -> Nip46AdmissionReply.Ack(connection, storedRelays.orEmpty().ifEmpty { deliveredOn })
    is Nip46Admission.RefuseConnect -> Nip46AdmissionReply.Refuse(message, bySecretRelays ?: deliveredOn, "Connection rejected: $message")
    Nip46Admission.Unknown -> Nip46AdmissionReply.Refuse(NIP46_NO_PERMISSION, deliveredOn, "No permission found for $senderKey")
    Nip46Admission.Ignore -> Nip46AdmissionReply.Drop("Request $requestId from $senderKey reached another connection's key")
    is Nip46Admission.Admit -> null
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

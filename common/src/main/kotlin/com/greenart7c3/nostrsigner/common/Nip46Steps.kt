package com.greenart7c3.nostrsigner.common

import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign

/** The error a relay auth event with more than one relay tag is refused with. */
const val NIP46_MULTI_RELAY_AUTH = "refusing a multi-relay auth event"

/** The error a request the user (or a rule standing for them) refused gets. */
const val NIP46_USER_REJECTED = "user rejected"

/**
 * What an admitted NIP-46 request does first, the same on Android and desktop. Replies go
 * to the connection with the retried reply ([BUNKER_REPLY_ATTEMPTS]).
 */
sealed interface Nip46Step {
    /**
     * Move the connection to the default relays: reply [result], and save the relays only
     * once the client has it, so it never loses the relays it listens on.
     */
    data class SwitchRelays(val result: String?) : Nip46Step

    /** Log out: reply "ack", and delete the connection only once the client has it. */
    data object Logout : Nip46Step {
        const val RESULT = "ack"
    }

    /**
     * Refuse with [error] before anything is signed or decrypted; [log] says why. When
     * [recordRejected], the refusal is also kept in the history, as a rejected request.
     */
    data class Refuse(val error: String, val log: String, val recordRejected: Boolean = false) : Nip46Step

    /** Answer it from the stored grants, or ask the user. */
    data object Decide : Nip46Step
}

/**
 * The first step of an admitted [request] of [type]. A relay auth event is refused when it
 * names more than one relay (authorization and the approval screen decide on the first
 * alone, so a second would be signed without ever being granted) or one outside a
 * non-empty [authWhitelist]; a NIP-44 v3 request without a context kind is refused too.
 * [defaultRelays] are the relays `switch_relays` moves the connection to.
 */
fun nip46Step(
    request: BunkerRequest,
    type: SignerType,
    authWhitelist: List<String>,
    defaultRelays: List<String>,
): Nip46Step {
    val authEvent = (request as? BunkerRequestSign)?.event?.takeIf { it.kind == RELAY_AUTH_KIND }
    val relayHost = authEvent?.let { relayAuthHost(it.tags) }.orEmpty()
    return when {
        type == SignerType.SWITCH_RELAYS -> Nip46Step.SwitchRelays(switchRelaysResult(defaultRelays))
        type == SignerType.LOGOUT -> Nip46Step.Logout
        authEvent != null && hasAmbiguousRelayTags(authEvent.kind, authEvent.tags) ->
            Nip46Step.Refuse(NIP46_MULTI_RELAY_AUTH, "Refusing a multi-relay auth event ${request.id}")
        authEvent != null && isBlockedByAuthWhitelist(authEvent.kind, relayHost, authWhitelist) ->
            Nip46Step.Refuse(NIP46_USER_REJECTED, "Relay auth for $relayHost is not in the auth whitelist", recordRejected = true)
        type in nip44v3SignerTypes && request.nip44v3Kind() == null ->
            Nip46Step.Refuse(NIP44V3_KIND_REQUIRED, "Rejecting invalid NIP-44 v3 request: $NIP44V3_KIND_REQUIRED")
        else -> Nip46Step.Decide
    }
}

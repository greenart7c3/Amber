package com.greenart7c3.nostrsigner.common

/**
 * The remembered decision of a permission in effect at [now]: true to accept,
 * false to reject, null to ask. Only the timestamp on the side that
 * [acceptable] selects counts, so a stale value left on the other side (for
 * example from an edited backup) can never decide a request.
 */
fun activeDecision(acceptable: Boolean, acceptUntil: Long, rejectUntil: Long, now: Long): Boolean? = when {
    !acceptable && rejectUntil > now -> false
    acceptable && acceptUntil > now -> true
    else -> null
}

/** The `acceptUntil` / `rejectUntil` of a decision remembered for good. */
const val REMEMBER_FOREVER = Long.MAX_VALUE / 1000

/**
 * Until when (seconds) a decision remembered with [screenCode], the `RememberType`
 * code both apps store, stays in effect from [now]: 0 for "never" or an unknown code.
 */
fun rememberUntil(screenCode: Int, now: Long): Long = when (screenCode) {
    1 -> now + 60
    2 -> now + 5 * 60
    3 -> now + 10 * 60
    4 -> REMEMBER_FOREVER
    5 -> now + 60 * 60
    6 -> now + 24 * 60 * 60
    7 -> now + 7 * 24 * 60 * 60
    else -> 0L
}

/**
 * Whether remembering a decision as [type], [kind] and [relay] replaces a stored grant
 * ([existingType], [existingKind], [existingRelay]), so the two never decide together.
 *
 * A kind grant replaces the same type and kind; for relay auth "*" replaces every
 * relay's grant, a relay only its own, and no relay only the grant without one. A
 * kind-less grant replaces every grant of its type, except SIGN_EVENT, which is kept
 * per kind, and also [otherScopeType]: for NIP-04/NIP-44 the whole-NIP type replaces
 * the content type (DECRYPT_CLEAR_TEXT, …) and the other way round.
 */
fun replacesGrant(
    type: String,
    kind: Int?,
    relay: String,
    otherScopeType: String?,
    existingType: String,
    existingKind: Int?,
    existingRelay: String,
): Boolean = when {
    kind != null ->
        existingType == type &&
            existingKind == kind &&
            when (relay) {
                "*" -> true
                "" -> existingRelay.isEmpty()
                else -> existingRelay == relay
            }
    existingType == type -> type != "SIGN_EVENT"
    else -> existingType == otherScopeType
}

/** The app sign policy that trusts every request ("fully trust"). */
const val SIGN_POLICY_FULLY_TRUST = 2

/**
 * Whether a request is decided without asking: true = auto-accept, false =
 * auto-reject, null = ask the user. [decision] is the matching permission's
 * [activeDecision], or null when there is none. An active explicit rejection
 * wins over the "fully trust" sign policy.
 */
fun rememberedDecision(signPolicy: Int?, decision: Boolean?): Boolean? {
    if (decision == false) {
        return false
    }
    if (signPolicy == SIGN_POLICY_FULLY_TRUST) {
        return true
    }
    return decision
}

/** A stored grant to look for: its permission type and kind (null: the kind-less grant). */
data class PermissionKey(val type: String, val kind: Int? = null)

/**
 * The stored grants that may decide a request, most specific first: the first
 * one stored decides, so a broader grant never overrides a narrower one.
 * [kind] is the event kind of a sign request or the NIP-44 v3 context kind,
 * [contentType] the content-classified type of a NIP-04/NIP-44 request
 * (`DECRYPT_CLEAR_TEXT`, …) and [signNip] the NIP the signed kind belongs to.
 * Relay auth (kind 22242) is not covered: it is granted per relay. NIP-44 v3 grants
 * are kind-scoped and fall back only to the explicit "all kinds" grant, never to
 * another kind's, so a kind-A reject cannot leak to kind B.
 */
fun permissionLookupOrder(type: SignerType, kind: Int?, contentType: String? = null, signNip: Int? = null): List<PermissionKey> = when (type) {
    SignerType.SIGN_EVENT -> listOfNotNull(PermissionKey(type.name, kind), signNip?.let { PermissionKey("NIP", it) })
    SignerType.NIP44_V3_ENCRYPT, SignerType.NIP44_V3_DECRYPT -> listOf(PermissionKey(type.name, kind), PermissionKey(type.name)).distinct()
    SignerType.NIP04_ENCRYPT, SignerType.NIP04_DECRYPT -> contentScopedLookup(type, contentType, nip = 4)
    SignerType.NIP44_ENCRYPT, SignerType.NIP44_DECRYPT -> contentScopedLookup(type, contentType, nip = 44)
    else -> listOf(PermissionKey(type.name))
}

private fun contentScopedLookup(type: SignerType, contentType: String?, nip: Int) = listOfNotNull(contentType?.let { PermissionKey(it) }, PermissionKey(type.name), PermissionKey("NIP", nip)).distinct()

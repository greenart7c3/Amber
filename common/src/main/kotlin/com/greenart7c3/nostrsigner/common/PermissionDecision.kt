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

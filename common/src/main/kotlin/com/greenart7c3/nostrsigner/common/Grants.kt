package com.greenart7c3.nostrsigner.common

/**
 * NIP-04/NIP-44 encrypt and decrypt: granted per content type (DECRYPT_CLEAR_TEXT, …) or
 * for the whole NIP (NIP44_DECRYPT), as the user chooses.
 */
val contentScopedSignerTypes = setOf(
    SignerType.NIP04_ENCRYPT,
    SignerType.NIP44_ENCRYPT,
    SignerType.NIP04_DECRYPT,
    SignerType.NIP44_DECRYPT,
)

/**
 * A remembered decision, as both apps store it: Android in `ApplicationPermissionsEntity`,
 * the desktop in `AppPermissionRecord`. [rememberType] is the `RememberType` screen code.
 */
data class Grant(
    val type: String,
    val kind: Int?,
    val acceptable: Boolean,
    val rememberType: Int,
    val acceptUntil: Long,
    val rejectUntil: Long,
    val relay: String = "",
)

/** The `RememberType` screen code of a decision remembered for good. */
const val REMEMBER_ALWAYS = 4

/**
 * The grants approving a connection with [signPolicy] gives: [basic] for "approve basic
 * actions" (0), the [checked] requested permissions for "manually approve" (1), and none
 * for "fully trust" (2), which needs none; null for an unknown policy. A requested
 * permission is stored under every type the request path looks it up by
 * ([expandPermissionTypes]), accepted for good, and one already stored ([isStored], by
 * type and kind) is not added again.
 */
fun signPolicyGrants(
    signPolicy: Int,
    basic: List<RequestedPermission>,
    checked: List<RequestedPermission>,
    isStored: (type: String, kind: Int?) -> Boolean,
): List<Grant>? {
    val granted = when (signPolicy) {
        0 -> basic
        1 -> checked
        SIGN_POLICY_FULLY_TRUST -> emptyList()
        else -> return null
    }
    val grants = mutableListOf<Grant>()
    granted.forEach { perm ->
        expandPermissionTypes(perm.type).forEach { type ->
            if (!isStored(type, perm.kind) && grants.none { it.type == type && it.kind == perm.kind }) {
                grants += Grant(type, perm.kind, acceptable = true, rememberType = REMEMBER_ALWAYS, acceptUntil = REMEMBER_FOREVER, rejectUntil = 0)
            }
        }
    }
    return grants
}

/**
 * Remembering the user's decision on one request: the [grant] to store, and which stored
 * grants it replaces ([replaces]).
 */
data class RememberedGrant(val grant: Grant, private val otherScopeType: String?) {
    /** Whether a stored grant of [type], [kind] and [relay] gives way to [grant] ([replacesGrant]). */
    fun replaces(type: String, kind: Int?, relay: String): Boolean = replacesGrant(grant.type, grant.kind, grant.relay, otherScopeType, type, kind, relay)
}

/**
 * The grant remembering the decision ([accepted]) on a request of [type] with
 * [rememberType] (the `RememberType` screen code), from [now]. For NIP-04/NIP-44
 * encrypt/decrypt ([contentScopedSignerTypes]) it is the whole-NIP type when
 * [wholeNip], else the request's [contentType] (DECRYPT_CLEAR_TEXT, …), and it replaces
 * the grant of the other scope; any other type is stored under its own name. [kind]
 * (an event kind, or a NIP-44 v3 context kind) and [relay] (a relay auth's) narrow it.
 */
fun rememberedGrant(
    type: SignerType,
    contentType: String,
    wholeNip: Boolean,
    kind: Int?,
    accepted: Boolean,
    rememberType: Int,
    relay: String,
    now: Long,
): RememberedGrant {
    val contentScoped = type in contentScopedSignerTypes
    val typeName = if (contentScoped && !wholeNip) contentType else type.toString()
    val otherScopeType = when {
        !contentScoped -> null
        wholeNip -> contentType
        else -> type.toString()
    }
    val until = rememberUntil(rememberType, now)
    val grant = Grant(typeName, kind, accepted, rememberType, if (accepted) until else 0, if (accepted) 0 else until, relay)
    return RememberedGrant(grant, otherScopeType)
}

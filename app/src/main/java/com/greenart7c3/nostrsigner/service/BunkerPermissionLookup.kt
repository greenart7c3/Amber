package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.nip44v3Kind
import com.greenart7c3.nostrsigner.common.nip46SignerType
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest

/**
 * The saved grant of the client [key] that answers [bunkerRequest] on the single-request
 * screen, or null when none does (the user is asked). [relayUrl] is the relay host of a
 * NIP-42 auth event (kind 22242). Same order as [intentPermission].
 */
fun bunkerPermission(
    application: ApplicationWithPermissions?,
    key: String,
    bunkerRequest: AmberBunkerRequest,
    relayUrl: String? = null,
): ApplicationPermissionsEntity? {
    val grants = application?.permissions?.filter { it.pkKey == key } ?: return null
    val type = nip46SignerType(bunkerRequest.request.method)
    return when (type) {
        SignerType.CONNECT, SignerType.GET_PUBLIC_KEY -> null
        SignerType.NIP04_DECRYPT, SignerType.NIP04_ENCRYPT, SignerType.NIP44_ENCRYPT, SignerType.NIP44_DECRYPT ->
            encryptionGrant(grants, type, bunkerRequest.encryptedData)
        SignerType.NIP44_V3_ENCRYPT, SignerType.NIP44_V3_DECRYPT -> nip44v3Grant(grants, type.toString(), bunkerRequest.request.nip44v3Kind())
        SignerType.SIGN_EVENT -> bunkerRequest.signedEvent?.let { event ->
            if (event.kind == RELAY_AUTH_KIND) relayAuthGrant(grants, type.toString(), relayUrl) else eventGrant(grants, type.toString(), event.kind)
        }
        // Ping, zap decryption and PSBT signing: only a grant of the request type (unlike
        // an intent's zap decryption, a bunker one never matches an encryption-type grant).
        else -> grants.firstOrNull { it.type == type.toString() }
    }
}

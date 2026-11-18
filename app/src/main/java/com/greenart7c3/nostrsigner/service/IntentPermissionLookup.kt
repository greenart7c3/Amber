package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.commonsui.kindToNip
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.isAllRelaysGrant
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.toPermissionType

/**
 * The saved grant of the app [key] that answers [intentData] on the single-request
 * screen, or null when none does (the user is asked). [relayUrl] is the relay host
 * of a NIP-42 auth event (kind 22242).
 */
fun intentPermission(
    application: ApplicationWithPermissions?,
    key: String,
    intentData: IntentData,
    relayUrl: String? = null,
): ApplicationPermissionsEntity? {
    val grants = application?.permissions?.filter { it.pkKey == key } ?: return null
    val type = intentData.type.toString()
    return when (intentData.type) {
        SignerType.GET_PUBLIC_KEY -> null
        SignerType.SIGN_PSBT -> grants.firstOrNull { it.type == type }
        SignerType.NIP44_V3_ENCRYPT, SignerType.NIP44_V3_DECRYPT -> nip44v3Grant(grants, type, intentData.nip44v3Kind)
        SignerType.NIP04_DECRYPT, SignerType.NIP04_ENCRYPT, SignerType.NIP44_ENCRYPT, SignerType.NIP44_DECRYPT, SignerType.DECRYPT_ZAP_EVENT -> encryptionGrant(grants, intentData.type, intentData.encryptedData)
        else -> intentData.event?.let { event ->
            if (event.kind == RELAY_AUTH_KIND) relayAuthGrant(grants, type, relayUrl) else eventGrant(grants, type, event.kind)
        }
    }
}

// V3 grants are scoped by (app, type, kind), with a kind-less "all kinds" fallback.
// They never satisfy v2/v4 requests.
internal fun nip44v3Grant(grants: List<ApplicationPermissionsEntity>, type: String, kind: Int?) = grants.firstOrNull { it.type == type && it.kind == kind } ?: grants.firstOrNull { it.type == type && it.kind == null }

// The encryption-specific type first, then the request type, then a whole-NIP grant.
internal fun encryptionGrant(grants: List<ApplicationPermissionsEntity>, type: SignerType, encryptedData: EncryptedDataKind?): ApplicationPermissionsEntity? {
    val nip = when (type) {
        SignerType.NIP04_DECRYPT, SignerType.NIP04_ENCRYPT -> 4
        SignerType.NIP44_ENCRYPT, SignerType.NIP44_DECRYPT -> 44
        else -> null
    }
    val isEncrypt = type == SignerType.NIP04_ENCRYPT || type == SignerType.NIP44_ENCRYPT
    val permType = encryptedData.toPermissionType(isEncrypt = isEncrypt)
    return grants.firstOrNull { it.type == permType }
        ?: grants.firstOrNull { it.type == type.toString() }
        ?: nip?.let { n -> grants.firstOrNull { it.type == "NIP" && it.kind == n } }
}

// Per relay host first, then an all-relays grant ("*" or blank).
internal fun relayAuthGrant(grants: List<ApplicationPermissionsEntity>, type: String, relayUrl: String?) = grants.firstOrNull { it.type == type && it.kind == RELAY_AUTH_KIND && it.relay == relayUrl }
    ?: grants.firstOrNull { it.type == type && it.kind == RELAY_AUTH_KIND && isAllRelaysGrant(it.relay) }

internal fun eventGrant(grants: List<ApplicationPermissionsEntity>, type: String, kind: Int): ApplicationPermissionsEntity? {
    val nip = kind.kindToNip()?.toIntOrNull()
    return grants.firstOrNull { (it.type == type && it.kind == kind) || (nip != null && it.type == "NIP" && it.kind == nip) }
}

internal const val RELAY_AUTH_KIND = 22242

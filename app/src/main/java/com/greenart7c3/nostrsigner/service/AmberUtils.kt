package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.common.REMEMBER_FOREVER
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.canRememberRelayAuth
import com.greenart7c3.nostrsigner.common.expandPermissionTypes
import com.greenart7c3.nostrsigner.common.rememberUntil
import com.greenart7c3.nostrsigner.common.replacesGrant
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.basicPermissions
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.models.encryptDecryptSignerTypes
import com.greenart7c3.nostrsigner.models.toPermissionTypeString
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.components.DecryptTypeScope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.io.encoding.Base64

object AmberUtils {
    /**
     * Produces the wire value for a request. Per the nip44v3 NIP-46 draft, v3
     * plaintext travels Base64-encoded: encrypt receives base64(plaintext) and
     * returns the ciphertext; decrypt returns base64(plaintext). The readable
     * plaintext for logs/display comes from the EncryptedDataKind instead (see
     * [com.greenart7c3.nostrsigner.models.nip44v3Plaintext]). [nip44v3Kind]/[nip44v3Scope] are required for V3.
     */
    suspend fun encryptOrDecryptData(
        data: String,
        type: SignerType,
        account: Account,
        pubKey: HexKey,
        nip44v3Kind: Int? = null,
        nip44v3Scope: String = "",
    ): String? = when (type) {
        SignerType.DECRYPT_ZAP_EVENT -> {
            account.decryptZapEvent(data)
        }
        SignerType.NIP04_DECRYPT -> {
            account.nip04Decrypt(data, pubKey)
        }
        SignerType.NIP04_ENCRYPT -> {
            account.nip04Encrypt(data, pubKey)
        }
        SignerType.NIP44_ENCRYPT -> {
            account.nip44Encrypt(data, pubKey)
        }
        SignerType.NIP44_V3_ENCRYPT -> {
            requireNotNull(nip44v3Kind) { "kind is required for NIP44_V3_ENCRYPT" }
            account.nip44v3Encrypt(Base64.decode(data), pubKey, nip44v3Kind, nip44v3Scope)
        }
        SignerType.NIP44_V3_DECRYPT -> {
            requireNotNull(nip44v3Kind) { "kind is required for NIP44_V3_DECRYPT" }
            Base64.encode(account.nip44v3Decrypt(data, pubKey, nip44v3Kind, nip44v3Scope))
        }
        else -> {
            account.nip44Decrypt(data, pubKey)
        }
    }

    suspend fun sendBunkerError(
        bunkerResponder: BunkerResponder,
        account: Account,
        bunkerRequest: AmberBunkerRequest,
        relays: List<NormalizedRelayUrl>,
        closeApplication: Boolean,
        onLoading: (Boolean) -> Unit,
    ) {
        bunkerResponder.sendBunkerResponse(
            account,
            bunkerRequest,
            BunkerResponse(bunkerRequest.request.id, "", "user rejected"),
            relays,
            onLoading = onLoading,
            onDone = { result ->
                if (!result) {
                    onLoading(false)
                } else {
                    bunkerResponder.finishRequest(bunkerRequest, closeApplication)
                }
            },
        )
    }

    fun updatePermission(
        application: ApplicationWithPermissions,
        key: String,
        signerType: SignerType,
        kind: Int?,
        value: Boolean,
        rememberType: RememberType,
        relay: String = "",
        encryptedData: EncryptedDataKind?,
        decryptTypeScope: DecryptTypeScope = DecryptTypeScope.ALL,
    ) {
        val until = rememberUntil(rememberType.screenCode, TimeUtils.now())

        // ALL = entire NIP (NIP04_DECRYPT / NIP44_DECRYPT), covers every content type for that NIP
        // SPECIFIC = only this content type (DECRYPT_CLEAR_TEXT, DECRYPT_TAG_ARRAY, etc.)
        val permissionTypeStr = if (decryptTypeScope == DecryptTypeScope.ALL && signerType in encryptDecryptSignerTypes) {
            signerType.toString()
        } else {
            signerType.toPermissionTypeString(encryptedData)
        }

        // ALL replaces the narrower content-classified grant, SPECIFIC the broader NIP-level one.
        val otherScopeType = when {
            signerType !in encryptDecryptSignerTypes -> null
            decryptTypeScope == DecryptTypeScope.ALL -> signerType.toPermissionTypeString(encryptedData)
            else -> signerType.toString()
        }
        application.permissions.removeIf { replacesGrant(permissionTypeStr, kind, relay, otherScopeType, it.type, it.kind, it.relay) }

        application.permissions.add(
            ApplicationPermissionsEntity(
                null,
                key,
                permissionTypeStr,
                kind,
                value,
                rememberType.screenCode,
                if (value) until else 0L,
                if (!value) until else 0L,
                relay,
            ),
        )
    }

    suspend fun acceptOrRejectPermission(
        application: ApplicationWithPermissions,
        key: String,
        signerType: SignerType,
        kind: Int?,
        value: Boolean,
        rememberType: RememberType,
        relay: String = "",
        encryptedData: EncryptedDataKind?,
        decryptTypeScope: DecryptTypeScope = DecryptTypeScope.ALL,
        dao: ApplicationDao,
    ) {
        updatePermission(
            application,
            key,
            signerType,
            kind,
            value,
            rememberType,
            relay,
            encryptedData,
            decryptTypeScope,
        )

        dao
            .insertApplicationWithPermissions(application)
    }

    fun configureSignPolicy(
        application: ApplicationWithPermissions,
        signPolicy: Int,
        key: String,
        permissions: List<Permission>?,
    ) {
        val granted = when (signPolicy) {
            0 -> basicPermissions
            1 -> permissions?.filter { it.checked }.orEmpty()
            2 -> emptyList()
            else -> return
        }
        application.application.signPolicy = signPolicy
        // Under the types the request path looks up: a generic encrypt/decrypt grants
        // both NIPs, and the upper-casing does not depend on the phone's language.
        granted.forEach { perm ->
            expandPermissionTypes(perm.type).forEach { type ->
                if (application.permissions.none { it.type == type && it.kind == perm.kind }) {
                    application.permissions.add(
                        ApplicationPermissionsEntity(null, key, type, perm.kind, true, RememberType.ALWAYS.screenCode, REMEMBER_FOREVER, 0),
                    )
                }
            }
        }
    }

    fun acceptPermission(
        application: ApplicationWithPermissions,
        key: String,
        type: SignerType,
        kind: Int?,
        rememberType: RememberType,
        relay: String = "",
        encryptedData: EncryptedDataKind? = null,
        decryptTypeScope: DecryptTypeScope = DecryptTypeScope.ALL,
    ) = updatePermission(application, key, type, kind, true, rememberType, relay, encryptedData, decryptTypeScope)
}

fun String.toShortenHex(): String {
    if (length <= 16) return this
    return replaceRange(8, length - 8, ":")
}

fun isPrivateEvent(
    kind: Int,
    tags: Array<Array<String>>,
): Boolean = kind == ZapRequestEvent.KIND && tags.any { t -> t.size > 1 && t[0] == "anon" }

/**
 * True when a sign_event payload names a pubkey that the approving account
 * must not sign over: any non-blank key other than the approving account's,
 * except anonymous zap requests (kind 9734 carrying an "anon" tag) whose
 * named key is not another account stored on this device.
 */
fun isCrossAccountSignEvent(
    unsignedEventKey: String,
    accountHexKey: String,
    kind: Int,
    tags: Array<Array<String>>,
    savedNpubs: Collection<String>,
): Boolean {
    if (unsignedEventKey.isBlank() || unsignedEventKey == accountHexKey) return false
    if (!isPrivateEvent(kind, tags)) return true
    return savedNpubs.any { it == Hex.decode(unsignedEventKey).toNpub() }
}

/**
 * Remember decision for a kind-22242 relay-auth grant. A blank relay (the
 * request carried no relay tag, or a malformed one) can never match a future
 * per-relay lookup, and the DAO reads blank relay as the all-relays wildcard —
 * remembering a SPECIFIC-scope choice over it would silently grant every
 * relay. Such a choice is downgraded to "just this time".
 */
fun relayAuthRememberType(
    allRelays: Boolean,
    relayUrl: String,
    rememberType: RememberType,
): RememberType = if (canRememberRelayAuth(allRelays, relayUrl)) rememberType else RememberType.NEVER

/** Remember decision for a sign grant of [kind]: only relay auth goes through [relayAuthRememberType]. */
fun signRememberType(kind: Int, allRelays: Boolean, relayUrl: String, rememberType: RememberType): RememberType = if (kind == 22242) relayAuthRememberType(allRelays, relayUrl, rememberType) else rememberType

/**
 * Whether the auth whitelist refuses a relay auth (kind 22242) for [relayHost]:
 * a non-empty whitelist rejects every relay outside it, without asking, on
 * every path that decides a relay auth (provider, single and batch screens).
 */
fun isBlockedByAuthWhitelist(kind: Int, relayHost: String, whitelist: List<String>): Boolean = com.greenart7c3.nostrsigner.common.isBlockedByAuthWhitelist(kind, relayHost, whitelist)

/** [isBlockedByAuthWhitelist] for a signed [event], decided on its first relay tag. */
fun isBlockedByAuthWhitelist(event: Event?, whitelist: List<String>): Boolean = event != null && event.kind == 22242 && isBlockedByAuthWhitelist(event.kind, RelayUrlUtils.extractHostAndPort(AmberEvent.relay(event)), whitelist)

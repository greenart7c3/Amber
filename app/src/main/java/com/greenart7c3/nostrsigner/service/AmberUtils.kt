package com.greenart7c3.nostrsigner.service

import android.content.Context
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.basicPermissions
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
     * [nip44v3Plaintext]). [nip44v3Kind]/[nip44v3Scope] are required for V3.
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
        account: Account,
        bunkerRequest: AmberBunkerRequest,
        relays: List<NormalizedRelayUrl>,
        context: Context,
        closeApplication: Boolean,
        onLoading: (Boolean) -> Unit,
    ) {
        BunkerRequestUtils.sendBunkerResponse(
            context,
            account,
            bunkerRequest,
            BunkerResponse(bunkerRequest.request.id, "", "user rejected"),
            relays,
            onLoading = onLoading,
            onDone = { result ->
                if (!result) {
                    onLoading(false)
                } else {
                    BunkerRequestUtils.finishRequest(bunkerRequest, closeApplication)
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
        val until = when (rememberType) {
            RememberType.ALWAYS -> Long.MAX_VALUE / 1000
            RememberType.ONE_MINUTE -> TimeUtils.oneMinuteFromNow()
            RememberType.FIVE_MINUTES -> TimeUtils.now() + TimeUtils.FIVE_MINUTES
            RememberType.TEN_MINUTES -> TimeUtils.now() + TimeUtils.FIFTEEN_MINUTES
            RememberType.ONE_HOUR -> TimeUtils.now() + 3600
            RememberType.ONE_DAY -> TimeUtils.now() + 86400
            RememberType.ONE_WEEK -> TimeUtils.now() + 604800
            RememberType.NEVER -> 0L
        }

        // ALL = entire NIP (NIP04_DECRYPT / NIP44_DECRYPT), covers every content type for that NIP
        // SPECIFIC = only this content type (DECRYPT_CLEAR_TEXT, DECRYPT_TAG_ARRAY, etc.)
        val permissionTypeStr = if (decryptTypeScope == DecryptTypeScope.ALL && signerType in encryptDecryptSignerTypes) {
            signerType.toString()
        } else {
            signerType.toPermissionTypeString(encryptedData)
        }

        if (kind != null) {
            if (relay.isNotEmpty()) {
                if (relay == "*") {
                    application.permissions.removeIf { it.kind == kind && it.type == permissionTypeStr }
                } else {
                    application.permissions.removeIf { it.kind == kind && it.type == permissionTypeStr && it.relay == relay }
                }
            } else {
                application.permissions.removeIf { it.kind == kind && it.type == permissionTypeStr && it.relay.isEmpty() }
            }
        } else {
            application.permissions.removeIf { it.type == permissionTypeStr && it.type != "SIGN_EVENT" }
            if (signerType in encryptDecryptSignerTypes) {
                if (decryptTypeScope == DecryptTypeScope.ALL) {
                    // Remove any narrower content-classified permission so ALL wins
                    application.permissions.removeIf { it.type == signerType.toPermissionTypeString(encryptedData) }
                } else {
                    // Remove any broader NIP-level permission so SPECIFIC wins
                    application.permissions.removeIf { it.type == signerType.toString() }
                }
            }
        }

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
        account: Account,
        relay: String = "",
        encryptedData: EncryptedDataKind?,
        decryptTypeScope: DecryptTypeScope = DecryptTypeScope.ALL,
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

        Amber.instance.dao(account.npub)
            .insertApplicationWithPermissions(application)
    }

    fun configureSignPolicy(
        application: ApplicationWithPermissions,
        signPolicy: Int,
        key: String,
        permissions: List<Permission>?,
    ) {
        when (signPolicy) {
            0 -> {
                application.application.signPolicy = 0
                basicPermissions.forEach {
                    if (application.permissions.any { permission -> permission.type == it.type.toUpperCase(Locale.current) && permission.kind == it.kind }) {
                        return@forEach
                    }
                    application.permissions.add(
                        ApplicationPermissionsEntity(
                            null,
                            key,
                            it.type.toUpperCase(Locale.current),
                            it.kind,
                            true,
                            RememberType.ALWAYS.screenCode,
                            Long.MAX_VALUE / 1000,
                            0,
                        ),
                    )
                }
            }
            1 -> {
                application.application.signPolicy = 1
                permissions?.filter { it.checked }?.forEach {
                    if (application.permissions.any { permission -> permission.type == it.type.toUpperCase(Locale.current) && permission.kind == it.kind }) {
                        return@forEach
                    }
                    application.permissions.add(
                        ApplicationPermissionsEntity(
                            null,
                            key,
                            it.type.toUpperCase(Locale.current),
                            it.kind,
                            true,
                            RememberType.ALWAYS.screenCode,
                            Long.MAX_VALUE / 1000,
                            0,
                        ),
                    )
                }
            }
            2 -> {
                application.application.signPolicy = 2
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
    ) {
        val until = when (rememberType) {
            RememberType.ALWAYS -> Long.MAX_VALUE / 1000
            RememberType.ONE_MINUTE -> TimeUtils.oneMinuteFromNow()
            RememberType.FIVE_MINUTES -> TimeUtils.now() + TimeUtils.FIVE_MINUTES
            RememberType.TEN_MINUTES -> TimeUtils.now() + TimeUtils.FIFTEEN_MINUTES
            RememberType.ONE_HOUR -> TimeUtils.now() + 3600
            RememberType.ONE_DAY -> TimeUtils.now() + 86400
            RememberType.ONE_WEEK -> TimeUtils.now() + 604800
            else -> 0L
        }

        // ALL = entire NIP (NIP04_DECRYPT / NIP44_DECRYPT), covers every content type for that NIP
        // SPECIFIC = only this content type (DECRYPT_CLEAR_TEXT, DECRYPT_TAG_ARRAY, etc.)
        val permissionTypeStr = if (decryptTypeScope == DecryptTypeScope.ALL && type in encryptDecryptSignerTypes) {
            type.toString()
        } else {
            type.toPermissionTypeString(encryptedData)
        }

        if (kind != null) {
            if (relay.isNotEmpty()) {
                if (relay == "*") {
                    application.permissions.removeIf { it.kind == kind && it.type == permissionTypeStr }
                } else {
                    application.permissions.removeIf { it.kind == kind && it.type == permissionTypeStr && it.relay == relay }
                }
            } else {
                application.permissions.removeIf { it.kind == kind && it.type == permissionTypeStr && it.relay.isEmpty() }
            }
        } else {
            application.permissions.removeIf { it.type == permissionTypeStr && it.type != "SIGN_EVENT" }
            if (type in encryptDecryptSignerTypes) {
                if (decryptTypeScope == DecryptTypeScope.ALL) {
                    // Remove any narrower content-classified permission so ALL wins
                    application.permissions.removeIf { it.type == type.toPermissionTypeString(encryptedData) }
                } else {
                    // Remove any broader NIP-level permission so SPECIFIC wins
                    application.permissions.removeIf { it.type == type.toString() }
                }
            }
        }

        application.permissions.add(
            ApplicationPermissionsEntity(
                null,
                key,
                permissionTypeStr,
                kind,
                true,
                rememberType.screenCode,
                until,
                0,
                relay,
            ),
        )
    }
}

fun String.toShortenHex(): String {
    if (length <= 16) return this
    return replaceRange(8, length - 8, ":")
}

/** Longest client-chosen app name Amber shows, in characters. */
internal const val APP_NAME_MAX = 64

/**
 * The client-chosen [name] made safe to show: control, format (bidi
 * overrides, zero-width) and separator characters are dropped, whitespace
 * runs and line breaks become single spaces, and it is cut to [max]
 * characters, so a client cannot fake extra lines or Amber's own text on an
 * approval sheet or notification. Mirrors the desktop `sanitizeAppName`.
 */
fun sanitizeAppName(name: String, max: Int = APP_NAME_MAX): String {
    val cleaned = buildString {
        name.codePoints().forEach { cp ->
            when (Character.getType(cp).toByte()) {
                Character.CONTROL, Character.FORMAT, Character.PRIVATE_USE, Character.UNASSIGNED, Character.SURROGATE -> {
                    if (Character.isWhitespace(cp)) append(' ')
                }
                Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> append(' ')
                else -> appendCodePoint(cp)
            }
        }
    }.replace(Regex("\\s+"), " ").trim()
    if (cleaned.codePointCount(0, cleaned.length) <= max) return cleaned
    return cleaned.substring(0, cleaned.offsetByCodePoints(0, max - 1)).trimEnd() + "…"
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
): RememberType = if (!allRelays && relayUrl.isBlank()) RememberType.NEVER else rememberType

/**
 * NIP-42 names exactly one relay per auth event: authorization, the auth
 * whitelist and the consent screen all decide on the FIRST relay tag, so a
 * second relay tag would be signed without ever being granted — the signed
 * event would also vouch for a relay the user never approved.
 */
fun hasAmbiguousRelayTags(kind: Int, tags: Array<Array<String>>): Boolean = kind == 22242 && tags.count { it.size > 1 && it[0] == "relay" } > 1

/**
 * Whether the auth whitelist refuses a relay auth (kind 22242) for [relayHost]:
 * a non-empty whitelist rejects every relay outside it, without asking, on
 * every path that decides a relay auth (provider, single and batch screens).
 */
fun isBlockedByAuthWhitelist(kind: Int, relayHost: String, whitelist: List<String> = Amber.instance.settings.authWhitelist): Boolean = kind == 22242 && whitelist.isNotEmpty() && relayHost !in whitelist

/** [isBlockedByAuthWhitelist] for a signed [event], decided on its first relay tag. */
fun isBlockedByAuthWhitelist(event: Event?): Boolean = event != null && event.kind == 22242 && isBlockedByAuthWhitelist(event.kind, RelayUrlUtils.extractHostAndPort(AmberEvent.relay(event)))

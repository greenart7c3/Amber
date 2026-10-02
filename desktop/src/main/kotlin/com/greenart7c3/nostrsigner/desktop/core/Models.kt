package com.greenart7c3.nostrsigner.desktop.core

import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.TimeUtils

enum class SignerType {
    CONNECT,
    SIGN_EVENT,
    NIP04_ENCRYPT,
    NIP04_DECRYPT,
    NIP44_ENCRYPT,
    NIP44_DECRYPT,
    NIP44_V3_ENCRYPT,
    NIP44_V3_DECRYPT,
    GET_PUBLIC_KEY,
    DECRYPT_ZAP_EVENT,
    PING,
    INVALID,
    SWITCH_RELAYS,
    SIGN_PSBT,
    LOGOUT,
}

enum class EncryptionType {
    NIP44,
    NIP04,
}

/** Mirrors the Android `RememberType` screen codes so exports stay compatible. */
enum class RememberType(val screenCode: Int, val labelKey: String) {
    NEVER(0, "d_remember_never"),
    ONE_MINUTE(1, "d_remember_1m"),
    FIVE_MINUTES(2, "d_remember_5m"),
    TEN_MINUTES(3, "d_remember_10m"),
    ALWAYS(4, "d_remember_always"),
    ONE_HOUR(5, "d_remember_1h"),
    ONE_DAY(6, "d_remember_1d"),
    ONE_WEEK(7, "d_remember_1w"),
    ;

    /** Localized display label. */
    fun label(language: String = Strings.currentLanguage.value): String = Strings.get(labelKey, language)

    /** Compact label, mirroring the Android `shortResourceId` ("5m", "1h", …). */
    fun shortLabel(language: String = Strings.currentLanguage.value): String = when (this) {
        NEVER -> Strings.get("never", language)
        ONE_MINUTE -> Strings.get("one_minute_short", language)
        FIVE_MINUTES -> Strings.get("five_minutes_short", language)
        TEN_MINUTES -> Strings.get("ten_minutes_short", language)
        ALWAYS -> Strings.get("always", language)
        ONE_HOUR -> Strings.get("one_hour_short", language)
        ONE_DAY -> Strings.get("one_day_short", language)
        ONE_WEEK -> Strings.get("one_week_short", language)
    }

    fun acceptUntil(): Long = when (this) {
        ALWAYS -> Long.MAX_VALUE / 1000
        ONE_MINUTE -> TimeUtils.now() + 60
        FIVE_MINUTES -> TimeUtils.now() + 300
        TEN_MINUTES -> TimeUtils.now() + 600
        ONE_HOUR -> TimeUtils.now() + 3600
        ONE_DAY -> TimeUtils.now() + 86400
        ONE_WEEK -> TimeUtils.now() + 604800
        NEVER -> 0L
    }
}

/**
 * "Delete after" choice for a new connection, mirroring the Android
 * `DeleteAfterType`: the connection is removed once [deleteAt] passes.
 */
enum class DeleteAfterType(val screenCode: Int, val labelKey: String, val seconds: Long) {
    NEVER(0, "never", 0L),
    FIVE_MINUTES(1, "five_minutes", 300L),
    TEN_MINUTES(2, "ten_minutes", 600L),
    ONE_HOUR(3, "one_hour", 3600L),
    ONE_DAY(4, "one_day", 86400L),
    ONE_WEEK(5, "one_week", 604800L),
    ;

    fun label(language: String = Strings.currentLanguage.value): String = Strings.get(labelKey, language)

    /** Unix seconds to store in [AppRecord.deleteAfter]; 0 means never. */
    fun deleteAt(now: Long = TimeUtils.now()): Long = if (this == NEVER) 0L else now + seconds
}

val rememberTypeDisplayOrder = listOf(
    RememberType.NEVER,
    RememberType.FIVE_MINUTES,
    RememberType.TEN_MINUTES,
    RememberType.ONE_HOUR,
    RememberType.ONE_DAY,
    RememberType.ONE_WEEK,
    RememberType.ALWAYS,
)

data class RequestedPermission(
    val type: String,
    val kind: Int?,
    var checked: Boolean = true,
)

val basicPermissions = listOf(
    RequestedPermission("get_public_key", null),
    RequestedPermission("encrypt_clear_text", null),
    RequestedPermission("decrypt_clear_text", null),
    RequestedPermission("encrypt_event", null),
    RequestedPermission("decrypt_event", null),
    RequestedPermission("encrypt_tag_array", null),
    RequestedPermission("decrypt_tag_array", null),
    RequestedPermission("decrypt_zap_event", null),
    RequestedPermission("sign_event", 0),
    RequestedPermission("sign_event", 1),
    RequestedPermission("sign_event", 3),
    RequestedPermission("sign_event", 4),
    RequestedPermission("sign_event", 5),
    RequestedPermission("sign_event", 6),
    RequestedPermission("sign_event", 7),
    RequestedPermission("sign_event", 9734),
    RequestedPermission("sign_event", 9735),
    RequestedPermission("sign_event", 10000),
    RequestedPermission("sign_event", 10002),
    RequestedPermission("sign_event", 10003),
    RequestedPermission("sign_event", 10013),
    RequestedPermission("sign_event", 31234),
    RequestedPermission("sign_event", 30078),
    RequestedPermission("sign_event", 22242),
    RequestedPermission("sign_event", 27235),
    RequestedPermission("sign_event", 30023),
)

/**
 * What an encrypt/decrypt payload holds, mirroring the Android
 * `EncryptedDataKind` (clear text, a tag array, or an event of some kind).
 * Encrypt/decrypt permissions are granted per content type.
 */
enum class EncryptedContentType {
    CLEAR_TEXT,
    EVENT,
    TAG_ARRAY,
}

data class EncryptedContent(
    val type: EncryptedContentType,
    /** Kind of the event, for [EncryptedContentType.EVENT]. */
    val eventKind: Int? = null,
) {
    companion object {
        /** Classifies a plaintext (the input of an encrypt, the result of a decrypt). */
        fun classify(plaintext: String): EncryptedContent {
            val trimmed = plaintext.trimStart()
            if (trimmed.startsWith("{")) {
                val node = runCatching { JacksonMapper.mapper.readTree(trimmed) }.getOrNull()
                val kind = node?.get("kind")
                if (node != null && node.isObject && kind != null && kind.canConvertToInt()) {
                    return EncryptedContent(EncryptedContentType.EVENT, kind.asInt())
                }
            } else if (trimmed.startsWith("[")) {
                val node = runCatching { JacksonMapper.mapper.readTree(trimmed) }.getOrNull()
                if (node != null && node.isArray && node.all { tag -> tag.isArray && tag.all { it.isTextual } }) {
                    return EncryptedContent(EncryptedContentType.TAG_ARRAY)
                }
            }
            return EncryptedContent(EncryptedContentType.CLEAR_TEXT)
        }
    }
}

/**
 * Scope of a remembered encrypt/decrypt choice, mirroring the Android
 * `DecryptTypeScope`: [SPECIFIC] covers only this content type (e.g.
 * `DECRYPT_CLEAR_TEXT`), [ALL] the whole NIP (e.g. `NIP44_DECRYPT`).
 */
enum class EncryptionScope {
    SPECIFIC,
    ALL,
}

/** NIP-04/NIP-44 encrypt and decrypt: the request types granted per content type. */
val contentScopedSignerTypes = setOf(
    SignerType.NIP04_ENCRYPT,
    SignerType.NIP44_ENCRYPT,
    SignerType.NIP04_DECRYPT,
    SignerType.NIP44_DECRYPT,
)

/** NIP-44 v3 encrypt/decrypt: granted per event kind (the v3 context), not per content type. */
val nip44v3SignerTypes = setOf(SignerType.NIP44_V3_ENCRYPT, SignerType.NIP44_V3_DECRYPT)

/**
 * The content-type permission for a request, mirroring the Android
 * `toPermissionTypeString`: e.g. NIP44_DECRYPT of a tag array ->
 * `DECRYPT_TAG_ARRAY`. Other types keep their own name.
 */
fun SignerType.contentPermissionType(content: EncryptedContent?): String {
    val isEncrypt = this == SignerType.NIP04_ENCRYPT || this == SignerType.NIP44_ENCRYPT
    return if (this in contentScopedSignerTypes) {
        (if (isEncrypt) "ENCRYPT_" else "DECRYPT_") + (content?.type ?: EncryptedContentType.CLEAR_TEXT).name
    } else {
        toString()
    }
}

/**
 * Persisted connection record. Mirrors the Android `ApplicationEntity`
 * column-for-column so behavior (and future import/export) matches.
 */
data class AppRecord(
    val key: String,
    val name: String = "",
    val relays: List<String> = emptyList(),
    val url: String = "",
    val icon: String = "",
    val description: String = "",
    val pubKey: String = "",
    val isConnected: Boolean = false,
    val secret: String = "",
    val useSecret: Boolean = false,
    val signPolicy: Int = 0,
    val deleteAfter: Long = 0L,
    val lastUsed: Long = 0L,
    val localKey: String = "",
) {
    fun normalizedRelays(): List<NormalizedRelayUrl> = relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }

    fun displayName(): String = name.ifBlank { key.toShortenHex() }
}

/** Mirrors the Android `ApplicationPermissionsEntity`. */
data class AppPermissionRecord(
    val type: String,
    val kind: Int?,
    val acceptable: Boolean,
    val rememberType: Int,
    val acceptUntil: Long,
    val rejectUntil: Long,
    val relay: String = "",
)

data class AppWithPermissions(
    val app: AppRecord,
    val permissions: MutableList<AppPermissionRecord> = mutableListOf(),
)

data class HistoryRecord(
    val appKey: String,
    val type: String,
    val kind: Int?,
    val time: Long,
    val accepted: Boolean,
)

data class LogRecord(
    val url: String,
    val type: String,
    val message: String,
    val time: Long,
)

data class DesktopSettings(
    /** Mirrors the Android app's `defaultAppRelays` (see AmberSettings.kt). */
    val defaultRelays: List<String> = listOf(
        "wss://auth.nostr1.com/",
        "wss://bucket.coracle.social/",
        "wss://nrs.primal.net/",
        "wss://relay.nip46.com/",
    ),
    val currentAccount: String = "",
    val darkTheme: Boolean? = null,
    /** Auto-lock delay for the passphrase lock, in minutes; 0 = never. Defaults to 1 hour. */
    val autoLockMinutes: Int = 60,
    /** Keep running in the system tray when the window is closed. */
    val closeToTray: Boolean = true,
    /** Show a system notification when a request needs approval. */
    val showNotifications: Boolean = true,
    /** Start automatically at login (systemd user service on Linux, HKCU Run entry on Windows). */
    val startOnBoot: Boolean = false,
    /** UI language tag (matches Strings.supportedLanguages); null = follow the OS. */
    val language: String? = null,
    /** Last floating window size in dp; null = default (fitted to the screen). */
    val windowWidth: Int? = null,
    val windowHeight: Int? = null,
    /** Reopen maximized when the window was maximized at last change. */
    val windowMaximized: Boolean = false,
    /** Route relay connections through Tor (built-in daemon or an external SOCKS proxy). */
    val torMode: TorMode = TorMode.DISABLED,
    /** SOCKS port of the external Tor proxy (9050 for system tor, 9150 for Tor Browser). */
    val proxyPort: Int = 9050,
    /** Indexer relays queried for account profiles; mirrors the Android `defaultIndexerRelays`. */
    val defaultProfileRelays: List<String> = listOf(
        "wss://purplepag.es/",
        "wss://user.kindpag.es/",
        "wss://profiles.nostr1.com/",
        "wss://directory.yabu.me/",
        "wss://indexer.coracle.social/",
    ),
) {
    fun normalizedDefaultRelays(): List<NormalizedRelayUrl> = defaultRelays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }

    fun normalizedProfileRelays(): List<NormalizedRelayUrl> = defaultProfileRelays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
}

data class AccountRecord(
    val npub: String,
    val name: String = "",
    val encryptedPrivKey: String = "",
    val encryptedSeedWords: String = "",
    val signPolicy: Int = 1,
    val didBackup: Boolean = true,
    /** Profile picture URL from the account's kind-0 metadata. */
    val picture: String = "",
    /** Unix seconds of the last profile change applied from metadata (Android `lastMetadataUpdate`). */
    val lastMetadataUpdate: Long = 0L,
    /** Unix seconds of the last finished profile fetch (Android `lastCheck`). */
    val lastProfileCheck: Long = 0L,
    /** Write relays from the account's NIP-65 list, also queried for its profile. */
    val userRelays: List<String> = emptyList(),
    val userRelaysCreatedAt: Long = 0L,
)

fun String.toShortenHex(): String = if (length <= 16) this else "${take(8)}…${takeLast(8)}"

/**
 * Mirrors `IntentUtils.isRemembered`: true = auto-accept, false = auto-reject,
 * null = ask the user.
 */
fun isRemembered(signPolicy: Int?, permission: AppPermissionRecord?): Boolean? {
    val rejectUntil = permission?.rejectUntil ?: 0
    val acceptUntil = permission?.acceptUntil ?: 0
    if (signPolicy == 2) {
        return true
    }
    if (rejectUntil == 0L && acceptUntil == 0L) return null
    return if (rejectUntil > TimeUtils.now() && rejectUntil > 0 && permission?.acceptable == false) {
        false
    } else if (acceptUntil > TimeUtils.now() && acceptUntil > 0 && permission?.acceptable == true) {
        true
    } else {
        null
    }
}

/** The NIP-46 method string for a signer type (e.g. SIGN_EVENT -> "sign_event"). */
fun SignerType.methodString(): String = name.lowercase()

/**
 * A localized action phrase for a request, e.g. "wants you to sign a Short
 * text note" — built from the same translations and event-kind descriptions
 * as the Android app.
 */
fun SignerType.describe(kind: Int?, language: String = Strings.currentLanguage.value): String = when (this) {
    SignerType.CONNECT -> SignerDescriptions.permission("connect", null, language)
    SignerType.SIGN_EVENT -> Strings.format(
        "wants_you_to_sign_a",
        SignerDescriptions.signEventDescription(kind, language),
        language = language,
    )
    SignerType.SWITCH_RELAYS -> Strings.get("switch_relays", language)
    SignerType.LOGOUT -> Strings.get("logout", language)
    SignerType.INVALID -> Strings.get("invalid_request", language)
    SignerType.PING -> Strings.get("ping", language)
    SignerType.NIP44_V3_ENCRYPT -> Strings.get("nip44_v3_wants_to_encrypt", language)
    SignerType.NIP44_V3_DECRYPT -> Strings.get("nip44_v3_wants_to_decrypt", language)
    else -> "${Strings.get("requests", language)} ${SignerDescriptions.permission(methodString(), kind, language)}"
}

/**
 * Mirrors the Android encrypt/decrypt approval text: "wants to encrypt this
 * text with NIP44", "wants to read Short text note from NIP04 encrypted
 * content", … Falls back to [describe] for other request types.
 */
fun SignerType.describe(kind: Int?, content: EncryptedContent?, language: String = Strings.currentLanguage.value): String {
    if (this !in contentScopedSignerTypes || content == null) return describe(kind, language)
    val nip = name.substringBefore('_')
    val isEncrypt = this == SignerType.NIP04_ENCRYPT || this == SignerType.NIP44_ENCRYPT
    val text = when (content.type) {
        EncryptedContentType.EVENT -> {
            val what = SignerDescriptions.signEventDescription(content.eventKind, language)
            Strings.format(if (isEncrypt) "wants_to_encrypt_with" else "wants_to_read_from_encrypted_content", what, nip, language = language)
        }

        EncryptedContentType.TAG_ARRAY ->
            Strings.format(if (isEncrypt) "wants_to_encrypt_this_list_of_tags_with" else "wants_to_read_this_list_of_tags_from_encrypted_content", nip, language = language)

        EncryptedContentType.CLEAR_TEXT ->
            Strings.format(if (isEncrypt) "wants_to_encrypt_this_text_with" else "wants_to_read_this_text_from_encrypted_content", nip, language = language)
    }
    return text.trim()
}

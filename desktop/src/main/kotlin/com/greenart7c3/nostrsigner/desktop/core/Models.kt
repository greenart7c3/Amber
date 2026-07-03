package com.greenart7c3.nostrsigner.desktop.core

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
    RequestedPermission("nip04_encrypt", null),
    RequestedPermission("nip04_decrypt", null),
    RequestedPermission("nip44_encrypt", null),
    RequestedPermission("nip44_decrypt", null),
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
    val defaultRelays: List<String> = listOf(
        "wss://nostr.oxtr.dev/",
        "wss://theforest.nostr1.com/",
        "wss://relay.primal.net/",
    ),
    val currentAccount: String = "",
    val darkTheme: Boolean? = null,
    /** Auto-lock delay for the passphrase lock, in minutes; 0 = never. */
    val autoLockMinutes: Int = 0,
    /** Keep running in the system tray when the window is closed. */
    val closeToTray: Boolean = true,
    /** Show a system notification when a request needs approval. */
    val showNotifications: Boolean = true,
    /** UI language tag (matches Strings.supportedLanguages); null = follow the OS. */
    val language: String? = null,
) {
    fun normalizedDefaultRelays(): List<NormalizedRelayUrl> = defaultRelays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
}

data class AccountRecord(
    val npub: String,
    val name: String = "",
    val encryptedPrivKey: String = "",
    val encryptedSeedWords: String = "",
    val signPolicy: Int = 1,
    val didBackup: Boolean = true,
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
    else -> "${Strings.get("requests", language)} ${SignerDescriptions.permission(methodString(), kind, language)}"
}

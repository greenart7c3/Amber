package com.greenart7c3.nostrsigner

import android.content.Context
import androidx.core.content.edit
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

// Keys in each account's preference file (`prefs_<npub>`); renaming one loses the stored value.
internal enum class AccountPrefKeys(val key: String) {
    NOSTR_PUBKEY("nostr_pubkey"),
    ACCOUNT_NAME("account_name"),
    SIGN_POLICY("default_sign_policy"),
    PROFILE_URL("profile_url"),
    LAST_METADATA_UPDATE("last_metadata_update"),
    METADATA_CREATED_AT("metadata_created_at"),
    LAST_CHECK("last_check"),
    USER_RELAYS("user_relays"),
    USER_RELAYS_CREATED_AT("user_relays_created_at"),
    DID_BACKUP("did_backup"),
    BACKUP_APPLICATIONS("backup_applications"),
}

/** What Amber remembers per account besides its key: backup consent, relay list and fetch times. */
object AccountPreferences {
    fun getBackupApplications(context: Context, npub: String): Boolean = accountPreferences(context, npub).getBoolean(AccountPrefKeys.BACKUP_APPLICATIONS.key, false)

    fun setBackupApplications(context: Context, npub: String, enabled: Boolean) {
        accountPreferences(context, npub).edit { putBoolean(AccountPrefKeys.BACKUP_APPLICATIONS.key, enabled) }
    }

    fun getLastCheck(context: Context, npub: String): Long = accountPreferences(context, npub).getLong(AccountPrefKeys.LAST_CHECK.key, 0)

    fun setLastCheck(context: Context, npub: String, time: Long) {
        accountPreferences(context, npub).edit { putLong(AccountPrefKeys.LAST_CHECK.key, time) }
    }

    fun getUserRelays(context: Context, npub: String): List<NormalizedRelayUrl> = accountPreferences(context, npub).getStringSet(AccountPrefKeys.USER_RELAYS.key, null)?.mapNotNull {
        RelayUrlNormalizer.normalizeOrNull(it)
    } ?: emptyList()

    fun getUserRelaysCreatedAt(context: Context, npub: String): Long = accountPreferences(context, npub).getLong(AccountPrefKeys.USER_RELAYS_CREATED_AT.key, 0)

    fun setUserRelays(context: Context, npub: String, relays: List<NormalizedRelayUrl>, createdAt: Long) {
        accountPreferences(context, npub).edit {
            putStringSet(AccountPrefKeys.USER_RELAYS.key, relays.map { it.url }.toSet())
            putLong(AccountPrefKeys.USER_RELAYS_CREATED_AT.key, createdAt)
        }
    }

    fun getLastMetadataUpdate(context: Context, npub: String): Long = accountPreferences(context, npub).getLong(AccountPrefKeys.LAST_METADATA_UPDATE.key, 0)

    fun setLastMetadataUpdate(context: Context, npub: String, time: Long) {
        accountPreferences(context, npub).edit { putLong(AccountPrefKeys.LAST_METADATA_UPDATE.key, time) }
    }

    /** created_at of the kind-0 event the account's name/picture were last taken from. */
    fun getMetadataCreatedAt(context: Context, npub: String): Long = accountPreferences(context, npub).getLong(AccountPrefKeys.METADATA_CREATED_AT.key, 0)

    fun setMetadataCreatedAt(context: Context, npub: String, createdAt: Long) {
        accountPreferences(context, npub).edit { putLong(AccountPrefKeys.METADATA_CREATED_AT.key, createdAt) }
    }
}

/** Whether any saved account agreed to publish its application backup (the daily worker runs only then). */
fun anyAccountBackupEnabled(context: Context): Boolean = SavedAccounts.all(context).any { AccountPreferences.getBackupApplications(context, it.npub) }

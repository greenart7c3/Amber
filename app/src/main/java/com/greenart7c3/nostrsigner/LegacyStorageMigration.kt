package com.greenart7c3.nostrsigner

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.preferences.core.Preferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LEGACY_FILE_PREFIX = "secret_keeper"
private const val SETTINGS_FILE_NAME = "$LEGACY_FILE_PREFIX.xml"

/**
 * Brings the storage Amber used up to 3.3.0 back into the app: the account
 * list and every setting lived in the `EncryptedSharedPreferences` file
 * `secret_keeper.xml`, the keys and preferences of each account in
 * `secret_keeper_<npub>.xml`. 3.4.0 moved to plain `prefs*` files plus an
 * encrypted DataStore per account, with a migration that 4.1.0 deleted while
 * the old files stayed on the device. The private key, the seed words and the
 * PIN go only to the encrypted DataStore, never to a plain preference file.
 *
 * Without this, an install that jumps from 3.3.0 or older to a current build
 * finds an empty `prefs`, reports that nobody is logged in and shows the
 * first-run onboarding as if the user had never had an account.
 *
 * The legacy files are only ever read here, and deleted once the account they
 * belong to provably opens again, so an interrupted or failed run is retried on
 * the next launch instead of losing the keys.
 */
object LegacyStorageMigration {
    private const val TAG = "LegacyStorageMigration"

    private const val SAVED_ACCOUNTS = "all_saved_accounts"
    private const val CURRENT_ACCOUNT = "currently_logged_in_account"
    private const val PIN = "pin"
    private const val USE_PROXY = "use_proxy"
    private const val PROXY_PORT = "proxy_port"

    internal val SECRET_KEYS = setOf("nostr_privkey", "seed_words")

    private val SETTINGS_KEYS_MIGRATED_ONE_BY_ONE = setOf(SAVED_ACCOUNTS, CURRENT_ACCOUNT, PIN)

    /**
     * One directory read, no Keystore access: every current install returns
     * false and pays nothing else.
     */
    fun needed(context: Context): Boolean = legacyDirectory(context).list()?.any {
        it.startsWith(LEGACY_FILE_PREFIX) && it.endsWith(".xml")
    } == true

    /**
     * Runs off the main thread. Failures are logged and swallowed: the legacy
     * files stay in place, the app continues with what it has, and the next
     * launch tries again.
     */
    suspend fun run(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                migrate(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AmberLog.e(TAG, "Could not migrate the storage Amber used before 3.4.0, keeping it for the next launch", e)
            }
        }
    }

    private suspend fun migrate(context: Context) {
        val legacySettings = openLegacyPreferences(context, null) ?: return
        val candidates = legacyAccounts(context, legacySettings)
        if (candidates.isEmpty()) {
            AmberLog.w(TAG, "The legacy preferences hold no account file left to restore")
        }

        AccountStorage.invalidateCaches()
        val hadCurrentAccount = !SavedAccounts.current(context).isNullOrBlank()

        val restored = candidates.filter { npub ->
            try {
                migrateAccount(context, npub)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AmberLog.e(TAG, "Could not migrate the legacy preferences of $npub", e)
                false
            }
        }

        if (restored.isNotEmpty()) {
            SavedAccounts.replace(
                context,
                mergeAccountLists(SavedAccounts.list(context), restored),
            )

            if (!hadCurrentAccount) {
                val legacyCurrent = legacySettings.getString(CURRENT_ACCOUNT, null)
                (legacyCurrent?.takeIf { it in restored } ?: restored.firstOrNull())
                    ?.let { SavedAccounts.switchTo(context, it) }
            }
        }

        copyPreferences(
            from = legacySettings,
            to = appPreferences(context),
            skipped = SETTINGS_KEYS_MIGRATED_ONE_BY_ONE,
            onlyMissing = true,
        )
        restorePinIntoEncryptedStore(context, legacySettings)
        restoreTorSettings(context)

        val reopened = restored.filter { npub -> opensAgain(context, npub) }
        if (reopened.size < restored.size) {
            AmberLog.w(TAG, "Only ${reopened.size} of ${restored.size} legacy accounts could be opened")
        }

        deleteLegacyFiles(context, legacyFilesToDelete(candidates, reopened))
    }

    private suspend fun opensAgain(context: Context, npub: String): Boolean = try {
        AccountStorage.load(context, npub) != null
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        AmberLog.e(TAG, "The migrated account $npub does not open yet", e)
        false
    }

    @Suppress("DEPRECATION", "NoComments")
    private fun openLegacyPreferences(context: Context, npub: String?): SharedPreferences? {
        // Opening a missing EncryptedSharedPreferences creates it, so a stale
        // empty file would be left behind for every account that is gone.
        if (npub != null && !File(legacyDirectory(context), "${LEGACY_FILE_PREFIX}_$npub.xml").exists()) return null

        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            if (npub == null) LEGACY_FILE_PREFIX else "${LEGACY_FILE_PREFIX}_$npub",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun legacyAccounts(context: Context, legacySettings: SharedPreferences): List<String> {
        val listed = legacySettings.getString(SAVED_ACCOUNTS, null)?.split(",")?.filter { it.isNotBlank() }.orEmpty()
        val accountFilesOnDisk = legacyDirectory(context).list().orEmpty()
            .filter { it.startsWith("${LEGACY_FILE_PREFIX}_") && it.endsWith(".xml") }
            .map { it.removePrefix("${LEGACY_FILE_PREFIX}_").removeSuffix(".xml") }
        return (listed.filter { it in accountFilesOnDisk } + accountFilesOnDisk).distinct()
    }

    private suspend fun migrateAccount(context: Context, npub: String): Boolean {
        val legacy = openLegacyPreferences(context, npub) ?: return false
        val privateKey = legacy.getString("nostr_privkey", null)
        if (privateKey.isNullOrBlank()) return false

        if (!hasStoredSecret(context, npub, DataStoreAccess.NOSTR_PRIVKEY)) {
            DataStoreAccess.saveEncryptedKey(context, npub, DataStoreAccess.NOSTR_PRIVKEY, privateKey)
        }
        val seedWords = legacy.getString("seed_words", null)
        if (!seedWords.isNullOrBlank() && !hasStoredSecret(context, npub, DataStoreAccess.SEED_WORDS)) {
            DataStoreAccess.saveEncryptedKey(context, npub, DataStoreAccess.SEED_WORDS, seedWords)
        }

        copyPreferences(
            from = legacy,
            to = accountPreferences(context, npub),
            skipped = SECRET_KEYS,
            onlyMissing = true,
        )
        return true
    }

    private suspend fun hasStoredSecret(context: Context, npub: String, key: Preferences.Key<String>): Boolean = DataStoreAccess.getEncryptedRaw(context, npub, key) != null

    private suspend fun restorePinIntoEncryptedStore(context: Context, legacySettings: SharedPreferences) {
        val pin = legacySettings.getString(PIN, null)
        if (!pin.isNullOrBlank() && DataStoreAccess.getPinRaw(context) == null) {
            DataStoreAccess.savePin(context, pin)
        }
    }

    /**
     * 3.3.0 kept the Tor switch in the settings file, and some versions after it
     * in the account files; both are read from `prefs` now. Restoring it keeps a
     * user who routed every connection through their Orbot off the clearnet.
     */
    @Suppress("NoComments")
    private fun restoreTorSettings(context: Context) {
        val settings = appPreferences(context)
        if (settings.contains(USE_PROXY)) return

        val accounts = SavedAccounts.all(context)
        val useProxy = accounts.any { accountPreferences(context, it.npub).getBoolean(USE_PROXY, false) }
        val proxyPort = accounts.firstNotNullOfOrNull {
            val accountPrefs = accountPreferences(context, it.npub)
            if (accountPrefs.getBoolean(USE_PROXY, false)) accountPrefs.getInt(PROXY_PORT, 9050) else null
        } ?: 9050

        settings.edit {
            putBoolean(USE_PROXY, useProxy)
            putInt(PROXY_PORT, proxyPort)
        }
    }

    private fun deleteLegacyFiles(context: Context, names: List<String>) {
        names.forEach { name ->
            val file = File(legacyDirectory(context), name)
            if (file.delete()) {
                AmberLog.d(TAG, "Deleted migrated legacy preferences $name")
            } else if (file.exists()) {
                AmberLog.w(TAG, "Could not delete migrated legacy preferences $name")
            }
        }
    }

    private fun legacyDirectory(context: Context): File = File("${context.filesDir.parent}/shared_prefs/")
}

internal fun mergeAccountLists(current: List<String>, legacy: List<String>): List<String> = (current + legacy).distinct()

internal fun legacyFilesToDelete(accounts: Collection<String>, restored: Collection<String>): List<String> {
    val accountFiles = restored.map { "${LEGACY_FILE_PREFIX}_$it.xml" }
    val everyAccountRestored = accounts.all { it in restored }
    return if (everyAccountRestored) accountFiles + SETTINGS_FILE_NAME else accountFiles
}

internal fun copyPreferences(
    from: SharedPreferences,
    to: SharedPreferences,
    skipped: Set<String> = emptySet(),
    onlyMissing: Boolean = false,
) {
    val existing = if (onlyMissing) to.all.keys else emptySet()
    val editor = to.edit()
    var changed = false
    from.all.forEach { (key, value) ->
        if (key in skipped || key in existing) return@forEach
        if (editor.putValue(key, value)) changed = true
    }
    if (changed) editor.apply()
}

private fun SharedPreferences.Editor.putValue(key: String, value: Any?): Boolean = when (value) {
    is String -> {
        putString(key, value)
        true
    }
    is Boolean -> {
        putBoolean(key, value)
        true
    }
    is Int -> {
        putInt(key, value)
        true
    }
    is Long -> {
        putLong(key, value)
        true
    }
    is Float -> {
        putFloat(key, value)
        true
    }
    is Set<*> -> if (value.all { it is String }) {
        @Suppress("UNCHECKED_CAST")
        putStringSet(key, value as Set<String>)
        true
    } else {
        false
    }
    else -> false
}

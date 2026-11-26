package com.greenart7c3.nostrsigner

import android.content.Context
import androidx.core.content.edit
import com.greenart7c3.nostrsigner.database.LogRetentionManager
import com.greenart7c3.nostrsigner.models.RetentionPeriod
import kotlin.coroutines.cancellation.CancellationException

/** How long logs and history are kept, and privacy mode, which keeps none; changing one prunes every account. */
object DataRetentionSettings {
    /**
     * Read synchronously, not from [Amber.settings], so pruning that runs before the
     * async settings load never applies the default (possibly shorter) period and
     * deletes rows the user chose to keep.
     */
    fun getLogRetention(context: Context): RetentionPeriod = getRetention(context, SettingsKeys.LOG_RETENTION, RetentionPeriod.SEVEN_DAYS)

    fun getHistoryRetention(context: Context): RetentionPeriod = getRetention(context, SettingsKeys.HISTORY_RETENTION, RetentionPeriod.THIRTY_DAYS)

    private fun getRetention(context: Context, key: SettingsKeys, default: RetentionPeriod): RetentionPeriod = appPreferences(context).enumOr(key, default)

    /**
     * Toggles privacy mode. When enabled, immediately wipes all existing logs and
     * activity history for every account so no previously collected data survives.
     * Future writes are blocked at the DAO layer while this setting stays on.
     */
    suspend fun updatePrivacyMode(context: Context, enabled: Boolean) {
        appPreferences(context).edit {
            apply {
                putBoolean(SettingsKeys.PRIVACY_MODE.key, enabled)
            }
        }
        LocalPreferences.settingsStore.settings = LocalPreferences.loadSettingsFromEncryptedStorage(context)
        if (enabled) {
            SavedAccounts.all(context).forEach {
                try {
                    LocalPreferences.accountRepository.getLogDatabase(it.npub).cleanupDao().clearLogs()
                    LocalPreferences.accountRepository.getHistoryDatabase(it.npub).dao().clearHistory()
                } catch (e: Exception) {
                    AmberLog.e(Amber.TAG, "Error wiping data for privacy mode", e)
                }
            }
        }
    }

    suspend fun updateLogRetention(context: Context, retention: RetentionPeriod) {
        appPreferences(context).edit {
            apply {
                putString(SettingsKeys.LOG_RETENTION.key, retention.name)
            }
        }
        LocalPreferences.settingsStore.settings = LocalPreferences.loadSettingsFromEncryptedStorage(context)
        pruneAllAccounts(context)
    }

    suspend fun updateHistoryRetention(context: Context, retention: RetentionPeriod) {
        appPreferences(context).edit {
            apply {
                putString(SettingsKeys.HISTORY_RETENTION.key, retention.name)
            }
        }
        LocalPreferences.settingsStore.settings = LocalPreferences.loadSettingsFromEncryptedStorage(context)
        pruneAllAccounts(context)
    }

    private suspend fun pruneAllAccounts(context: Context) {
        SavedAccounts.all(context).forEach {
            try {
                LogRetentionManager.prune(context, LocalPreferences.accountRepository, it.npub)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AmberLog.e(Amber.TAG, "Error pruning logs for ${it.npub}", e)
            }
        }
    }
}

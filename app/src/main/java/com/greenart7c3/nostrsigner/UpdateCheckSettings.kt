package com.greenart7c3.nostrsigner

import android.content.Context
import androidx.core.content.edit
import com.greenart7c3.nostrsigner.models.UpdateChannel
import com.greenart7c3.nostrsigner.models.UpdateCheckFrequency

/** The self-update settings: whether, how often and on which channel Amber checks, and when it last did. */
object UpdateCheckSettings {
    fun updateAutoCheckUpdates(context: Context, enabled: Boolean) {
        appPreferences(context).edit {
            apply {
                putBoolean(SettingsKeys.AUTO_CHECK_UPDATES.key, enabled)
            }
        }
        LocalPreferences.settingsStore.settings = LocalPreferences.loadSettingsFromEncryptedStorage(context)
    }

    fun updateUpdateCheckFrequency(context: Context, frequency: UpdateCheckFrequency) {
        appPreferences(context).edit {
            apply {
                putString(SettingsKeys.UPDATE_CHECK_FREQUENCY.key, frequency.name)
            }
        }
        LocalPreferences.settingsStore.settings = LocalPreferences.loadSettingsFromEncryptedStorage(context)
    }

    fun updateUpdateChannel(context: Context, channel: UpdateChannel) {
        appPreferences(context).edit {
            apply {
                putString(SettingsKeys.UPDATE_CHANNEL.key, channel.name)
            }
        }
        LocalPreferences.settingsStore.settings = LocalPreferences.loadSettingsFromEncryptedStorage(context)
    }

    fun getLastUpdateCheckTime(context: Context): Long = appPreferences(context).getLong(SettingsKeys.LAST_UPDATE_CHECK_TIME.key, 0L)

    fun setLastUpdateCheckTime(context: Context, time: Long) {
        appPreferences(context).edit {
            apply {
                putLong(SettingsKeys.LAST_UPDATE_CHECK_TIME.key, time)
            }
        }
    }
}

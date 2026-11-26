package com.greenart7c3.nostrsigner

import android.content.Context
import androidx.core.content.edit
import com.greenart7c3.nostrsigner.models.ThemeMode

/** How Amber runs on this device: the theme, the permission prompts already answered and starting at boot. */
object DevicePreferences {
    fun shouldShowRationale(context: Context): Boolean? {
        if (!appPreferences(context).contains(SettingsKeys.RATIONALE.key)) {
            return null
        }
        return appPreferences(context).getBoolean(SettingsKeys.RATIONALE.key, true)
    }

    fun updateShouldShowRationale(context: Context, value: Boolean) {
        appPreferences(context).edit {
            apply {
                putBoolean(SettingsKeys.RATIONALE.key, value)
            }
        }
    }

    /** Read synchronously so the first frame of each activity already uses the chosen theme. */
    fun getThemeMode(context: Context): ThemeMode = appPreferences(context).enumOr(SettingsKeys.THEME_MODE, ThemeMode.SYSTEM)

    fun updateThemeMode(context: Context, mode: ThemeMode) {
        appPreferences(context).edit {
            apply {
                putString(SettingsKeys.THEME_MODE.key, mode.name)
            }
        }
        LocalPreferences.settingsStore.themeMode.value = mode
    }

    fun getBatteryOptimization(context: Context): Boolean = appPreferences(context).getBoolean(SettingsKeys.BATERRY_OPTIMIZATION.key, false)

    fun updateBatteryOptimization(context: Context, value: Boolean) {
        appPreferences(context).edit {
            apply {
                putBoolean(SettingsKeys.BATERRY_OPTIMIZATION.key, value)
            }
        }
    }

    fun getStartServiceOnBoot(context: Context): Boolean = appPreferences(context).getBoolean(SettingsKeys.START_SERVICE_ON_BOOT.key, true)

    fun updateStartServiceOnBoot(context: Context, enabled: Boolean) {
        appPreferences(context).edit {
            apply {
                putBoolean(SettingsKeys.START_SERVICE_ON_BOOT.key, enabled)
            }
        }
        LocalPreferences.settingsStore.settings = LocalPreferences.loadSettingsFromEncryptedStorage(context)
    }
}

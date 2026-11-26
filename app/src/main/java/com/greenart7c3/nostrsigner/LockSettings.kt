package com.greenart7c3.nostrsigner

import android.content.Context
import androidx.core.content.edit

/** The app-lock PIN and the `requireUnlockedDevice` key policy. */
object LockSettings {
    suspend fun loadPinFromEncryptedStorage(context: Context): String? {
        val legacyPin = appPreferences(context).getString(SettingsKeys.PIN.key, null)
        if (legacyPin != null) {
            DataStoreAccess.savePin(context, legacyPin)
            appPreferences(context).edit { remove(SettingsKeys.PIN.key) }
        }
        return DataStoreAccess.loadPin(context)
    }

    suspend fun savePinToEncryptedStorage(context: Context, pin: String?) {
        DataStoreAccess.savePin(context, pin)
    }

    /**
     * Toggles the opt-in "require unlocked device" key policy (GHSA-8844-q5vh-9j8f, L1).
     * The Keystore flag is set at key-generation time, so changing the setting
     * requires rotating to a new wrapping key. [SecureCryptoHelper.rotateKey]
     * persists the setting together with the new active key alias.
     */
    suspend fun updateRequireUnlockedDevice(context: Context, enabled: Boolean) {
        SecureCryptoHelper.rotateKey(context, requireUnlockedDevice = enabled)
        LocalPreferences.settingsStore.settings = LocalPreferences.loadSettingsFromEncryptedStorage(context)
    }
}

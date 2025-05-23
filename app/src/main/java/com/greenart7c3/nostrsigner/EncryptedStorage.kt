@file:Suppress("DEPRECATION")

package com.greenart7c3.nostrsigner

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object EncryptedStorage {
    private const val PREFERENCES_NAME = "secret_keeper"

    private fun prefsFileName(npub: String? = null): String {
        return if (npub == null) PREFERENCES_NAME else "${PREFERENCES_NAME}_$npub"
    }

    fun preferences(npub: String? = null, context: Context): EncryptedSharedPreferences {
        val masterKey: MasterKey =
            MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

        val preferencesName = prefsFileName(npub)

        return EncryptedSharedPreferences.create(
            context,
            preferencesName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ) as EncryptedSharedPreferences
    }
}

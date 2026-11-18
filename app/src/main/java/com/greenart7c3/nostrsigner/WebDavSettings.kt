package com.greenart7c3.nostrsigner

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit

/** The WebDAV server cloud backups go to, in the app-wide preference file; the password is encrypted. */
object WebDavSettings {
    // Keys in the app-wide preference file; renaming one loses the stored value.
    private const val URL = "webdav_url"
    private const val USERNAME = "webdav_username"
    private const val PASSWORD_ENCRYPTED = "webdav_password_encrypted"
    private const val FILENAME = "webdav_filename"

    fun url(context: Context): String = appPreferences(context).getString(URL, "") ?: ""

    fun username(context: Context): String = appPreferences(context).getString(USERNAME, "") ?: ""

    /** The decrypted password, or "" when none is saved or it cannot be decrypted. */
    suspend fun password(context: Context): String {
        val encrypted = encryptedPassword(context)
        return if (encrypted.isBlank()) {
            ""
        } else {
            try {
                SecureCryptoHelper.decrypt(encrypted)
            } catch (_: Exception) {
                ""
            }
        }
    }

    /**
     * Returns the still-encrypted password (raw SharedPreferences value). Used by
     * [SecureCryptoHelper.rotateKey] to avoid mutex reentrancy.
     */
    fun encryptedPassword(context: Context): String = appPreferences(context).getString(PASSWORD_ENCRYPTED, "") ?: ""

    /**
     * Durably replaces the encrypted password with [encryptedPassword] only if it still
     * equals [expected]. Used by [SecureCryptoHelper.rotateKey].
     */
    @Synchronized
    @SuppressLint("UseKtx") // needs commit()'s result, see KeystorePolicy.commitActiveAlias
    fun replaceEncryptedPassword(context: Context, expected: String, encryptedPassword: String): Boolean = encryptedPassword(context) == expected &&
        appPreferences(context).edit().putString(PASSWORD_ENCRYPTED, encryptedPassword).commit()

    suspend fun save(
        context: Context,
        url: String,
        username: String,
        password: String,
        fileName: String,
    ) {
        val encryptedPassword = if (password.isBlank()) "" else SecureCryptoHelper.encrypt(password)
        appPreferences(context).edit {
            putString(URL, url)
            putString(USERNAME, username)
            putString(PASSWORD_ENCRYPTED, encryptedPassword)
            putString(FILENAME, fileName)
        }
    }
}

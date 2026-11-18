package com.greenart7c3.nostrsigner

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import com.greenart7c3.nostrsigner.service.DeviceUnlockPolicy

/**
 * The opt-in "require unlocked device" key policy (GHSA-8844-q5vh-9j8f, L1) and the
 * Keystore alias it belongs to, stored in the app-wide preference file. Both are
 * plain synchronous reads, so the gate is right before the async settings load
 * (offline flavor, WorkManager or ContentProvider cold start).
 */
object KeystorePolicy {
    // Keys in the app-wide preference file; renaming one loses the stored value.
    private const val REQUIRE_UNLOCKED_DEVICE = "require_unlocked_device"
    private const val ACTIVE_KEYSTORE_ALIAS = "active_keystore_alias"

    /**
     * The only source of the device-lock flag. It is written together with the
     * active Keystore alias ([commitActiveAlias]) so it always matches the key
     * actually in use, and can't be overwritten by a stale settings copy saved
     * while a key rotation is running.
     */
    fun requireUnlockedDevice(context: Context): Boolean = appPreferences(context).getBoolean(REQUIRE_UNLOCKED_DEVICE, false)

    /** Keystore alias currently used to encrypt new secrets, or null if never recorded. */
    fun activeAlias(context: Context): String? = appPreferences(context).getString(ACTIVE_KEYSTORE_ALIAS, null)

    /**
     * Durably (synchronous `commit`) records the active Keystore alias and,
     * when given, the matching key policy in one atomic write, so the
     * setting can never disagree with the key that is actually in use.
     * Returns false if the write did not reach disk. Not the KTX `edit {}`:
     * it discards `commit()`'s result, which key rotation depends on.
     */
    @SuppressLint("UseKtx")
    fun commitActiveAlias(context: Context, alias: String, requireUnlockedDevice: Boolean? = null): Boolean = appPreferences(context).edit()
        .putString(ACTIVE_KEYSTORE_ALIAS, alias)
        .apply { requireUnlockedDevice?.let { putBoolean(REQUIRE_UNLOCKED_DEVICE, it) } }
        .commit()

    /**
     * The signing gate: throws [SecurityException] while the policy is on and the device is
     * locked. Without a keyguard to ask it fails closed and treats the device as locked.
     */
    fun requireAccess(context: Context) {
        DeviceUnlockPolicy.requireAccess(requireUnlockedDevice(context)) {
            context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked ?: true
        }
    }

    /**
     * Whether the wrapping key is refusing because the policy is on and the device is locked.
     * That is the setting working, not a broken key, so it must not be reported as one (the
     * report tells the user to log out and back in); without a keyguard it is reported.
     */
    fun isLockedOut(context: Context): Boolean = requireUnlockedDevice(context) &&
        context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true
}

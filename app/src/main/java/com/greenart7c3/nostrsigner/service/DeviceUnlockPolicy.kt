package com.greenart7c3.nostrsigner.service

object DeviceUnlockPolicy {
    // isDeviceLocked is a supplier so callers only pay for the KeyguardManager
    // lookup when the opt-in flag is actually set.
    fun requireAccess(requireUnlockedDevice: Boolean, isDeviceLocked: () -> Boolean) {
        if (requireUnlockedDevice && isDeviceLocked()) {
            throw SecurityException("Private-key operations require an unlocked device")
        }
    }
}

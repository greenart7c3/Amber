package com.greenart7c3.nostrsigner.service

object DeviceUnlockPolicy {
    /** Throws while the device is locked; [isDeviceLocked] is only asked when [requireUnlockedDevice] is set. */
    fun requireAccess(requireUnlockedDevice: Boolean, isDeviceLocked: () -> Boolean) {
        if (requireUnlockedDevice && isDeviceLocked()) {
            throw SecurityException("Private-key operations require an unlocked device")
        }
    }
}

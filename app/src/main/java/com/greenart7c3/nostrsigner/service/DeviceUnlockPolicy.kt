package com.greenart7c3.nostrsigner.service

object DeviceUnlockPolicy {
    fun requireAccess(requireUnlockedDevice: Boolean, isDeviceLocked: Boolean) {
        if (requireUnlockedDevice && isDeviceLocked) {
            throw SecurityException("Private-key operations require an unlocked device")
        }
    }
}

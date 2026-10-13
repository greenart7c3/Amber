package com.greenart7c3.nostrsigner.service

import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceUnlockPolicyTest {
    @Test
    fun `opt in blocks operations after the device locks`() {
        DeviceUnlockPolicy.requireAccess(true, false)
        assertThrows(SecurityException::class.java) { DeviceUnlockPolicy.requireAccess(true, true) }
        DeviceUnlockPolicy.requireAccess(true, false)
    }

    @Test
    fun `default policy preserves authorized background operation`() {
        DeviceUnlockPolicy.requireAccess(false, true)
        DeviceUnlockPolicy.requireAccess(false, false)
    }
}

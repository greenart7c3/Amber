package com.greenart7c3.nostrsigner.desktop.core

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F-16: any peer on the session bus can emit `ActionInvoked`, so a signal
 * must be ignored unless it comes from the notification daemon's unique bus
 * name — otherwise a hostile peer approves or rejects pending requests by
 * sweeping notification ids.
 */
class DbusNotificationSenderFilterTest {
    @After
    fun tearDown() {
        DbusNotifications.daemonOwner = null
    }

    @Test
    fun `signals from the daemon owner are accepted`() {
        DbusNotifications.daemonOwner = ":1.42"

        assertTrue(DbusNotifications.isFromDaemon(":1.42"))
    }

    @Test
    fun `signals from other peers are rejected`() {
        DbusNotifications.daemonOwner = ":1.42"

        assertFalse(DbusNotifications.isFromDaemon(":1.99"))
        assertFalse(DbusNotifications.isFromDaemon("org.freedesktop.Notifications"))
        assertFalse(DbusNotifications.isFromDaemon(null))
    }

    @Test
    fun `without a resolved owner every signal is rejected`() {
        DbusNotifications.daemonOwner = null

        assertFalse(DbusNotifications.isFromDaemon(":1.42"))
        assertFalse(DbusNotifications.isFromDaemon(null))
    }
}

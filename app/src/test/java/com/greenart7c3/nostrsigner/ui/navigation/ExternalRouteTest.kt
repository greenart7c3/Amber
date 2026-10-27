package com.greenart7c3.nostrsigner.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The exported activities used to navigate to any "route" extra another app
 * sent, including routes that take arguments.
 */
class ExternalRouteTest {
    @Test
    fun amberOwnNotificationRoutesAreAllowed() {
        assertEquals(Route.IncomingRequest.route, externalRoute(Route.IncomingRequest.route))
        assertEquals(Route.UpdateSettings.route, externalRoute(Route.UpdateSettings.route))
    }

    @Test
    fun anyOtherRouteIsIgnored() {
        listOf(
            "QrCode/nsec1attackerchosencontent",
            "NewNsecBunkerCreated/attacker",
            Route.Settings.route,
            Route.Applications.route,
            "",
            null,
        ).forEach { assertNull(it, externalRoute(it)) }
    }
}

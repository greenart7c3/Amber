package com.greenart7c3.nostrsigner.ui

import com.greenart7c3.nostrsigner.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Test

class StartRouteTest {
    @Test
    fun `an external request always opens on the request`() {
        assertEquals(Route.IncomingRequest.route, startRoute(isExternalRequest = true, pendingRoute = Route.Settings.route, hasRequests = false))
    }

    @Test
    fun `a route asked for wins over waiting requests`() {
        assertEquals(Route.Settings.route, startRoute(isExternalRequest = false, pendingRoute = Route.Settings.route, hasRequests = true))
    }

    @Test
    fun `waiting requests open on the request, otherwise the app list`() {
        assertEquals(Route.IncomingRequest.route, startRoute(isExternalRequest = false, pendingRoute = null, hasRequests = true))
        assertEquals(Route.Applications.route, startRoute(isExternalRequest = false, pendingRoute = null, hasRequests = false))
    }
}

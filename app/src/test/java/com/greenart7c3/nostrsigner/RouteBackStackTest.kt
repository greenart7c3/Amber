package com.greenart7c3.nostrsigner

import com.greenart7c3.nostrsigner.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteBackStackTest {
    @Test
    fun addApplicationOpensOnTopOfTheApplicationList() {
        assertEquals(
            listOf(Route.Applications.route, Route.NewApplication.route),
            routeBackStack(Route.NewApplication.route),
        )
    }

    @Test
    fun updateSettingsOpensOnTopOfSettings() {
        assertEquals(
            listOf(Route.Settings.route, Route.UpdateSettings.route),
            routeBackStack(Route.UpdateSettings.route),
        )
    }

    @Test
    fun incomingRequestOpensAlone() {
        assertEquals(listOf(Route.IncomingRequest.route), routeBackStack(Route.IncomingRequest.route))
    }
}

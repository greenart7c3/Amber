package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.NetworkConnectivity
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkConnectivityTransitionTest {
    @Test
    fun transitions() {
        // Startup probe only records state, mirroring Android's first
        // onAvailable (no reconnect before the boot connect path runs).
        assertEquals(NetworkConnectivity.Transition.OPENED, NetworkConnectivity.transition(wasOnline = null, nowOnline = true))
        assertEquals(NetworkConnectivity.Transition.OPENED, NetworkConnectivity.transition(wasOnline = null, nowOnline = false))
        assertEquals(NetworkConnectivity.Transition.ONLINE, NetworkConnectivity.transition(wasOnline = false, nowOnline = true))
        assertEquals(NetworkConnectivity.Transition.OFFLINE, NetworkConnectivity.transition(wasOnline = true, nowOnline = false))
        assertEquals(NetworkConnectivity.Transition.UNCHANGED, NetworkConnectivity.transition(wasOnline = true, nowOnline = true))
        assertEquals(NetworkConnectivity.Transition.UNCHANGED, NetworkConnectivity.transition(wasOnline = false, nowOnline = false))
    }
}

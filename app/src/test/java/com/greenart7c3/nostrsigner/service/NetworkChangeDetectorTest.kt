package com.greenart7c3.nostrsigner.service

import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_VPN
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkChangeDetectorTest {
    private val wifi = NetworkSnapshot(1, setOf(TRANSPORT_WIFI), validated = true)
    private val mobile = NetworkSnapshot(2, setOf(TRANSPORT_CELLULAR), validated = true)
    private val vpnOverWifi = NetworkSnapshot(3, setOf(TRANSPORT_VPN, TRANSPORT_WIFI), validated = true)

    @Test
    fun firstNetworkIsTheBaselineNotAChange() {
        val detector = NetworkChangeDetector()
        assertNull(detector.onNetwork(wifi))
    }

    @Test
    fun repeatedCallbacksForTheSameStateAreIgnored() {
        val detector = NetworkChangeDetector()
        detector.onNetwork(wifi)
        assertNull(detector.onNetwork(wifi))
        assertNull(detector.onNetwork(wifi.copy(validated = false)))
    }

    @Test
    fun switchingBetweenWifiAndMobileIsAChange() {
        val detector = NetworkChangeDetector()
        detector.onNetwork(wifi)
        assertEquals("network changed", detector.onNetwork(mobile))
        assertEquals("network changed", detector.onNetwork(wifi))
    }

    @Test
    fun vpnComingUpAndGoingDownIsAChange() {
        val detector = NetworkChangeDetector()
        detector.onNetwork(wifi)
        assertEquals("VPN connected", detector.onNetwork(vpnOverWifi))
        assertEquals("VPN disconnected", detector.onNetwork(wifi))
    }

    @Test
    fun vpnMovingToAnotherUnderlyingTransportIsAChange() {
        val detector = NetworkChangeDetector()
        detector.onNetwork(vpnOverWifi)
        assertEquals(
            "network changed",
            detector.onNetwork(vpnOverWifi.copy(transports = setOf(TRANSPORT_VPN, TRANSPORT_CELLULAR))),
        )
    }

    @Test
    fun regainingInternetAccessIsAChange() {
        val detector = NetworkChangeDetector()
        detector.onNetwork(wifi.copy(validated = false))
        assertEquals("network regained internet access", detector.onNetwork(wifi))
    }

    @Test
    fun networkReturningAfterALossIsAChange() {
        val detector = NetworkChangeDetector()
        detector.onNetwork(wifi)
        assertTrue(detector.onLost(wifi.networkId))
        assertEquals("network available again", detector.onNetwork(wifi))
    }

    @Test
    fun losingANetworkThatWasAlreadyReplacedIsIgnored() {
        val detector = NetworkChangeDetector()
        detector.onNetwork(wifi)
        detector.onNetwork(mobile)
        assertFalse(detector.onLost(wifi.networkId))
        assertNull(detector.onNetwork(mobile))
    }
}

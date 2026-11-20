package com.greenart7c3.nostrsigner.service

import android.net.NetworkCapabilities
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager

/** The network the device is on, as the last capabilities update reported it. */
class NetworkState {
    @Volatile var isOnMobileData = false
        private set

    @Volatile var isOnWifi = false
        private set

    @Volatile var isOnVpn = false
        private set

    @Volatile private var isOffline = false

    /** Records [networkCapabilities]; true when any transport or the internet capability changed. */
    fun update(networkCapabilities: NetworkCapabilities?): Boolean {
        val mobile = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val wifi = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val vpn = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val offline = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true

        val changedNetwork = mobile != isOnMobileData || wifi != isOnWifi || vpn != isOnVpn || offline != isOffline
        isOnMobileData = mobile
        isOnWifi = wifi
        isOnVpn = vpn
        isOffline = offline

        if (changedNetwork) {
            HttpClientManager.setDefaultTimeout(if (mobile) HttpClientManager.DEFAULT_TIMEOUT_ON_MOBILE else HttpClientManager.DEFAULT_TIMEOUT_ON_WIFI)
        }
        return changedNetwork
    }
}

package com.greenart7c3.nostrsigner.models

import com.greenart7c3.nostrsigner.ui.BiometricsTimeType
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.RelaySetupInfo

val defaultAppRelays = listOf(
    RelaySetupInfo("wss://relay.nsec.app", read = true, write = true, feedTypes = COMMON_FEED_TYPES),
    RelaySetupInfo("wss://nostr.oxtr.dev", read = true, write = true, feedTypes = COMMON_FEED_TYPES),
    RelaySetupInfo("wss://theforest.nostr1.com", read = true, write = true, feedTypes = COMMON_FEED_TYPES),
    RelaySetupInfo("wss://relay.primal.net", read = true, write = true, feedTypes = COMMON_FEED_TYPES),
)

data class AmberSettings(
    val defaultRelays: List<RelaySetupInfo> = defaultAppRelays,
    val defaultProfileRelays: List<RelaySetupInfo> = listOf(
        RelaySetupInfo("wss://relay.nostr.band", read = true, write = false, feedTypes = COMMON_FEED_TYPES),
        RelaySetupInfo("wss://purplepag.es", read = true, write = false, feedTypes = COMMON_FEED_TYPES),
    ),
    val lastBiometricsTime: Long = 0,
    val useAuth: Boolean = false,
    val biometricsTimeType: BiometricsTimeType = BiometricsTimeType.EVERY_TIME,
    val usePin: Boolean = false,
    val useProxy: Boolean = false,
    val proxyPort: Int = 9050,
)

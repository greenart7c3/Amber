package com.greenart7c3.nostrsigner.models

import com.greenart7c3.nostrsigner.ui.BiometricsTimeType
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.RelaySetupInfo

data class AmberSettings(
    val endpoint: String = "",
    val pushServerMessage: Boolean = false,
    val defaultRelays: List<RelaySetupInfo> = listOf(RelaySetupInfo("wss://relay.nsec.app", read = true, write = true, feedTypes = COMMON_FEED_TYPES)),
    val defaultProfileRelays: List<RelaySetupInfo> = listOf(
        RelaySetupInfo("wss://relay.nostr.band", read = true, write = false, feedTypes = COMMON_FEED_TYPES),
        RelaySetupInfo("wss://purplepag.es", read = true, write = false, feedTypes = COMMON_FEED_TYPES),
    ),
    val lastBiometricsTime: Long = 0,
    val useAuth: Boolean = false,
    val biometricsTimeType: BiometricsTimeType = BiometricsTimeType.EVERY_TIME,
    val usePin: Boolean = false,
)

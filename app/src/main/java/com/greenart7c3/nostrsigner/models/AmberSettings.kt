package com.greenart7c3.nostrsigner.models

import com.greenart7c3.nostrsigner.ui.BiometricsTimeType
import com.greenart7c3.nostrsigner.ui.NotificationType
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.RelaySetupInfo

data class AmberSettings(
    val endpoint: String = "",
    val pushServerMessage: Boolean = false,
    val defaultRelays: List<RelaySetupInfo> = listOf(RelaySetupInfo("wss://relay.nsec.app", read = true, write = true, feedTypes = COMMON_FEED_TYPES)),
    val lastBiometricsTime: Long = 0,
    val useAuth: Boolean = false,
    val notificationType: NotificationType = NotificationType.PUSH,
    val biometricsTimeType: BiometricsTimeType = BiometricsTimeType.EVERY_TIME,
)

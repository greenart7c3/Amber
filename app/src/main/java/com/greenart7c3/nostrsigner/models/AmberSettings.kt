package com.greenart7c3.nostrsigner.models

import com.greenart7c3.nostrsigner.ui.BiometricsTimeType
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.flow.MutableStateFlow

val defaultAppRelays = listOf(
    RelayUrlNormalizer.normalize("wss://relay.nsec.app/"),
    RelayUrlNormalizer.normalize("wss://nostr.oxtr.dev/"),
    RelayUrlNormalizer.normalize("wss://theforest.nostr1.com/"),
    RelayUrlNormalizer.normalize("wss://relay.primal.net/"),
)

val defaultIndexerRelays = listOf(
    RelayUrlNormalizer.normalize("wss://purplepag.es/"),
    RelayUrlNormalizer.normalize("wss://user.kindpag.es/"),
    RelayUrlNormalizer.normalize("wss://profiles.nostr1.com/"),
    RelayUrlNormalizer.normalize("wss://directory.yabu.me/"),
)

data class AmberSettings(
    val defaultRelays: List<NormalizedRelayUrl> = defaultAppRelays,
    val defaultProfileRelays: List<NormalizedRelayUrl> = defaultIndexerRelays,
    val lastBiometricsTime: Long = 0,
    val useAuth: Boolean = false,
    val biometricsTimeType: BiometricsTimeType = BiometricsTimeType.EVERY_TIME,
    val usePin: Boolean = false,
    val useProxy: Boolean = false,
    val proxyPort: Int = 9050,
    val killSwitch: MutableStateFlow<Boolean> = MutableStateFlow(false),
    var language: String? = null,
)

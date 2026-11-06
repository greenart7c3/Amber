package com.greenart7c3.nostrsigner.service

import android.net.TrafficStats
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.common.relays.LocalRelayServer
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes

/**
 * The Android app's local bunker relay ([LocalRelayServer], shared with the
 * desktop app): a private WebSocket relay on `ws://127.0.0.1:7777` (7778 for
 * debug builds, 7779 for the benchmark flavor, so side-by-side installs never
 * fight over the port) that lets NIP-46 clients on this device reach the
 * bunker without public relays. Opt in under Settings -> Relays (off by default)
 * ([com.greenart7c3.nostrsigner.models.AmberSettings.localBunkerServer]);
 * never runs in the offline flavor, which has no network permission.
 *
 * `NotificationSubscription` subscribes to it like to any other relay once a
 * connection lists it, so requests take the same permission and approval
 * path; new bunker connections list it beside the default relays
 * ([newBunkerRelays]). It lives in the app process, so it serves while that
 * process runs (the foreground `ConnectivityService` keeps it alive).
 */
object LocalBunkerRelayServer : LocalRelayServer(
    logger = object : LocalRelayServer.Logger {
        override fun d(message: String) = AmberLog.d(LocalRelayServer.TAG, message)

        override fun i(message: String) = AmberLog.i(LocalRelayServer.TAG, message)

        override fun e(message: String, throwable: Throwable?) = AmberLog.e(LocalRelayServer.TAG, message, throwable)
    },
    approvedSenders = { approvedRelaySenders() },
    // Same tag as the app's other sockets (TaggedSocketFactory).
    tagThread = { if (TrafficStats.getThreadStatsTag() == -1) TrafficStats.setThreadStatsTag(0x0001) },
    // The offline flavor has no network permission and must never open a port.
    isAllowed = { !BuildFlavorChecker.isOfflineFlavor() },
) {
    private const val BENCHMARK_PORT = 7779

    private val isAvailable: Boolean get() = !BuildFlavorChecker.isOfflineFlavor()

    /** The automatic port for this build. */
    val defaultPort: Int
        get() = when {
            @Suppress("KotlinConstantConditions")
            BuildConfig.FLAVOR
                == "benchmark" -> BENCHMARK_PORT
            BuildConfig.DEBUG -> DEBUG_PORT
            else -> DEFAULT_PORT
        }

    /** The relay port: the user's choice, or the automatic default for this build. */
    fun port(): Int = Amber.instance.settings.localBunkerPort ?: defaultPort

    /**
     * Relays a NEW bunker connection uses: the local relay (when the setting
     * is on and the server is actually serving) followed by the default
     * relays, so clients on this device can talk to the bunker without any
     * public relay.
     */
    fun newBunkerRelays(): List<NormalizedRelayUrl> = buildList {
        val local = if (isAvailable && Amber.instance.settings.localBunkerServer) relayUrl() else null
        if (local != null) add(local)
        addAll(Amber.instance.settings.defaultRelays)
    }.distinct()

    /** Starts the relay at app start when the setting is on. Call off the main thread. */
    fun startIfEnabled() {
        if (isAvailable && Amber.instance.settings.localBunkerServer) start(port())
    }

    /**
     * Applies the "local bunker server" setting: starts or stops the loopback
     * relay, then redials so subscriptions follow (a stopped relay's sockets
     * are dropped; a fresh one is dialed at once instead of after the backoff).
     * Call off the main thread.
     */
    suspend fun applySetting() {
        if (!isAvailable) return
        if (Amber.instance.settings.localBunkerServer) start(port()) else stop()
        Amber.instance.resetRelayConnections("local bunker relay setting changed")
    }

    /**
     * Changes the relay's port, refused while any saved connection still lists
     * the relay: its bunker:// URI (and its subscription) would keep pointing
     * at the old port. Returns false when refused. Call off the main thread.
     */
    suspend fun changePort(newPort: Int): Boolean {
        if (!isAvailable) return false
        val current = RelayUrlNormalizer.normalizeOrNull("ws://127.0.0.1:${port()}/")
        val inUse = current != null &&
            LocalPreferences.allSavedAccounts(Amber.instance).any { account ->
                Amber.instance.getDatabase(account.npub).dao().getAllRelayLists().any { current in it.relays }
            }
        if (inUse) return false
        Amber.instance.settings = Amber.instance.settings.copy(localBunkerPort = newPort)
        LocalPreferences.saveSettingsToEncryptedStorage(Amber.instance.settings)
        // A running relay must come down first: start() never rebinds a live one.
        stop()
        applySetting()
        return true
    }
}

/**
 * Pubkeys the relay treats as approved senders (never rate limited): the
 * client key of every saved connection (request senders), every connection
 * key and the account keys (what the signer answers with).
 */
private fun approvedRelaySenders(): Set<String> = buildSet {
    LocalPreferences.allSavedAccounts(Amber.instance).forEach { account ->
        runCatching { add(account.npub.bechToBytes().toHexKey()) }
        addAll(Amber.instance.getDatabase(account.npub).dao().getAllKeys())
    }
    addAll(LocalKeyAccountIndex.localPubKeys())
}

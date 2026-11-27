package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.net.TrafficStats
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.common.relays.LocalRelayServer
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.RelayClient
import com.greenart7c3.nostrsigner.data.SettingsStore
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
 *
 * One per process: Amber owns the instance (`Amber.localBunkerRelay`).
 */
class LocalBunkerRelayServer(
    private val appContext: Context,
    private val settingsStore: SettingsStore,
    private val accountRepository: AccountRepository,
    private val relayClient: RelayClient,
) : LocalRelayServer(
    logger = object : LocalRelayServer.Logger {
        override fun d(message: String) = AmberLog.d(LocalRelayServer.TAG, message)

        override fun i(message: String) = AmberLog.i(LocalRelayServer.TAG, message)

        override fun e(message: String, throwable: Throwable?) = AmberLog.e(LocalRelayServer.TAG, message, throwable)
    },
    approvedSenders = { approvedRelaySenders(appContext, accountRepository) },
    tagThread = { if (TrafficStats.getThreadStatsTag() == -1) TrafficStats.setThreadStatsTag(APP_SOCKET_STATS_TAG) },
    isAllowed = { !BuildFlavorChecker.isOfflineFlavor() },
) {
    private val isAvailable: Boolean get() = !BuildFlavorChecker.isOfflineFlavor()

    /** The relay port: the user's choice, or the automatic default for this build. */
    fun port(): Int = settingsStore.settings.localBunkerPort ?: defaultPort

    /**
     * Relays a NEW bunker connection uses: the local relay (when the setting
     * is on and the server is actually serving) followed by the default
     * relays, so clients on this device can talk to the bunker without any
     * public relay.
     */
    fun newBunkerRelays(): List<NormalizedRelayUrl> = buildList {
        val local = if (isAvailable && settingsStore.settings.localBunkerServer) relayUrl() else null
        if (local != null) add(local)
        addAll(settingsStore.settings.defaultRelays)
    }.distinct()

    /** Starts the relay at app start when the setting is on. Call off the main thread. */
    fun startIfEnabled() {
        if (isAvailable && settingsStore.settings.localBunkerServer) start(port())
    }

    /**
     * Applies the "local bunker server" setting: starts or stops the loopback
     * relay, then redials so subscriptions follow (a stopped relay's sockets
     * are dropped; a fresh one is dialed at once instead of after the backoff).
     * Call off the main thread.
     */
    suspend fun applySetting() {
        if (!isAvailable) return
        if (settingsStore.settings.localBunkerServer) start(port()) else stop()
        relayClient.resetRelayConnections("local bunker relay setting changed")
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
            SavedAccounts.all(appContext).any { account ->
                accountRepository.getDatabase(account.npub).applicationListDao().getAllRelayLists().any { current in it.relays }
            }
        if (inUse) return false
        settingsStore.settings = settingsStore.settings.copy(localBunkerPort = newPort)
        LocalPreferences.saveSettingsToEncryptedStorage(appContext, settingsStore.settings)
        restartOnNewPort()
        return true
    }

    private suspend fun restartOnNewPort() {
        stop()
        applySetting()
    }

    companion object {
        private const val BENCHMARK_PORT = 7779
        private const val APP_SOCKET_STATS_TAG = 0x0001

        /** The automatic port for this build. */
        val defaultPort: Int
            get() = when {
                @Suppress("KotlinConstantConditions")
                BuildConfig.FLAVOR
                    == "benchmark" -> BENCHMARK_PORT
                BuildConfig.DEBUG -> DEBUG_PORT
                else -> DEFAULT_PORT
            }
    }
}

private fun approvedRelaySenders(context: Context, accountRepository: AccountRepository): Set<String> = buildSet {
    SavedAccounts.all(context).forEach { account ->
        runCatching { add(account.npub.bechToBytes().toHexKey()) }
        addAll(accountRepository.getDatabase(account.npub).applicationListDao().getAllKeys())
    }
    addAll(LocalKeyAccountIndex.localPubKeys())
}

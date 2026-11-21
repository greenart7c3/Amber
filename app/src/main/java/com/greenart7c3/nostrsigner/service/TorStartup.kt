package com.greenart7c3.nostrsigner.service

import android.content.Context
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.data.RelayClient
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.models.TorMode
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val MAX_TOR_START_ATTEMPTS = 5

/** Brings the built-in Tor up for the relays, and reconnects them whenever it comes back. */
class TorStartup(
    // The Application, which TorManager starts the daemon with.
    private val appContext: Context,
    private val settingsStore: SettingsStore,
    private val relayClient: RelayClient,
    private val scope: CoroutineScope,
) {
    // Guard for startRecoveryObserver(): runMigrations' only caller
    // (ConnectivityService.onCreate) can run again after the service is
    // recreated, and the observer must be registered once per process.
    private var recoveryObserverStarted = false

    private val usesBuiltInTor get() = settingsStore.settings.torMode == TorMode.BUILTIN && !BuildFlavorChecker.isOfflineFlavor()

    /** Starts the built-in Tor when the user chose it; false when it could not start, so the relays must not dial yet. */
    suspend fun start(): Boolean {
        if (!usesBuiltInTor) return true
        TorManager.start(appContext, scope)

        startRecoveryObserver()

        // Wait for Tor to be ready before establishing relay connections.
        // Bounded: when Tor cannot bootstrap (airplane mode, censored
        // network) an unbounded stop/start cycle restarts the whole
        // daemon forever, burning battery and data for as long as the
        // device stays offline. After the last attempt the daemon is
        // stopped and the Failed notification (Restart action) takes
        // over; startFunctions() parks on waitIfNeeded() until
        // Tor actually comes back.
        val ready = waitForStartup()
        if (!ready) {
            TorManager.stop()
            TorManager.showFailed()
            AmberLog.e(
                Amber.TAG,
                "Built-in Tor failed to start after $MAX_TOR_START_ATTEMPTS attempts; relays stay disconnected until it recovers",
            )
        }
        return ready
    }

    /** Suspends until the built-in Tor runs, when the user chose it. */
    suspend fun waitIfNeeded() {
        if (usesBuiltInTor) {
            TorManager.isRunning.first { it }
        }
    }

    /**
     * Bounded Tor startup: at most [MAX_TOR_START_ATTEMPTS] full daemon starts,
     * each allowed 120s to bootstrap, with a growing pause (3s, 6s, 12s, 24s)
     * between attempts. Returns true as soon as the SOCKS port is up.
     */
    private suspend fun waitForStartup(): Boolean {
        var attempt = 0
        while (!TorManager.isRunning.value) {
            if (attempt >= MAX_TOR_START_ATTEMPTS) return false
            if (attempt > 0) {
                TorManager.showRetrying()
                TorManager.stop()
                delay((3L shl (attempt - 1)).seconds)
                TorManager.start(appContext, scope)
            }
            attempt++
            withTimeoutOrNull(120.seconds) {
                TorManager.isRunning.first { it }
            }
        }
        return true
    }

    /**
     * Reconnects relays whenever built-in Tor transitions into a running state.
     * Covers recovery after the bounded startup retries gave up (manual Restart
     * action or a network change retried Tor) and mid-session restarts, neither
     * of which runs through runMigrations again.
     */
    private fun startRecoveryObserver() {
        if (recoveryObserverStarted) return
        recoveryObserverStarted = true
        scope.launch {
            var wasRunning = TorManager.isRunning.value
            TorManager.isRunning.collect { running ->
                if (running && !wasRunning) {
                    AmberLog.d(Amber.TAG, "Built-in Tor is up; refreshing relay connections")
                    relayClient.checkForNewRelaysAndUpdateAllFilters(true)
                }
                wasRunning = running
            }
        }
    }
}

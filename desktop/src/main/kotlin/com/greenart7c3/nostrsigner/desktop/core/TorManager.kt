package com.greenart7c3.nostrsigner.desktop.core

import com.greenart7c3.nostrsigner.common.tor.TorSocksAuthenticator
import com.greenart7c3.nostrsigner.tor.ArtiNative
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

sealed class TorStatus {
    data object Stopped : TorStatus()

    data class Connecting(val percentage: Int) : TorStatus()

    data object Connected : TorStatus()

    data class Failed(val message: String?) : TorStatus()
}

/**
 * Desktop port of the Android `TorManager` (free flavor): runs Arti (Tor in
 * Rust) in-process through [ArtiNative] and publishes its SOCKS port. Status
 * is surfaced in Settings instead of an OS notification.
 */
object TorManager {
    private const val TAG = "TorManager"
    private const val BOOTSTRAP_POLL_MS = 500L
    private const val BOOTSTRAP_TIMEOUT_MS = 120_000L
    private const val SOCKS_BIND_ATTEMPTS = 5
    private const val FAIL_CLOSED_SOCKS_PORT = 0

    private val startStopLock = Mutex()

    @Volatile private var bootstrapWatcher: Job? = null

    @Volatile private var nativeLibraryLoaded = false

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _socksPort = MutableStateFlow(FAIL_CLOSED_SOCKS_PORT)
    val socksPort: StateFlow<Int> = _socksPort.asStateFlow()

    private val _status = MutableStateFlow<TorStatus>(TorStatus.Stopped)
    val status: StateFlow<TorStatus> = _status.asStateFlow()

    private fun updateStatus(status: TorStatus) {
        _status.value = status
    }

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) { startStopLock.withLock { startLocked(scope) } }
    }

    fun stop(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) { startStopLock.withLock { stopLocked() } }
    }

    /**
     * Restarts from scratch: besides the SOCKS listener, the Tor client itself
     * is rebuilt (fresh circuits), as restarting the old tor daemon did.
     */
    fun restart(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            startStopLock.withLock {
                AmberLogger.i(TAG, "Restarting built-in Tor")
                stopLocked()
                startLocked(scope)
            }
        }
    }

    /** Stops the SOCKS listener at application shutdown; Arti itself ends with the process. */
    fun shutdown() {
        bootstrapWatcher?.cancel()
        if (nativeLibraryLoaded) ArtiNative.stopSocksProxy()
    }

    @Suppress("NoComments")
    private fun startLocked(scope: CoroutineScope) {
        if (bootstrapWatcher != null) return
        updateStatus(TorStatus.Connecting(0))
        try {
            ArtiNative.setLogCallback { line -> AmberLogger.d(TAG, line) }
            nativeLibraryLoaded = true
            AmberLogger.i(TAG, "Starting built-in Tor: ${ArtiNative.getVersion()}")

            val dataDir = File(AppDirs.dataDir, "arti").apply { mkdirs() }
            AppDirs.restrictToOwner(dataDir)
            val result = ArtiNative.initialize(dataDir.absolutePath)
            if (result != 0) {
                fail("Arti failed to initialize ($result)")
                return
            }

            val port = bindSocksProxy()
            if (port == null) {
                fail("Could not open the Tor SOCKS port")
                return
            }
            AmberLogger.i(TAG, "Built-in Tor SOCKS proxy on port $port")
            _socksPort.value = port
            bootstrapWatcher = scope.launch(Dispatchers.IO) { awaitBootstrap() }
        } catch (e: Throwable) {
            // Throwable: a missing/broken native library surfaces as an Error
            // (UnsatisfiedLinkError, ExceptionInInitializerError).
            AmberLogger.e(TAG, "Failed to start built-in Tor", e)
            fail(e.message)
        }
    }

    /**
     * The proxy accepts connections right away (each one waits for its
     * circuit), but report Connected only once Arti can carry traffic, like
     * the C tor daemon's bootstrap did.
     */
    @Suppress("NoComments")
    private suspend fun awaitBootstrap() {
        try {
            val bootstrapped = withTimeoutOrNull(BOOTSTRAP_TIMEOUT_MS.milliseconds) {
                while (currentCoroutineContext().isActive) {
                    if (ArtiNative.isBootstrapped() == 1) {
                        return@withTimeoutOrNull true
                    }
                    val permille = ArtiNative.bootstrapProgressPermille()
                    updateStatus(TorStatus.Connecting((permille / 10).coerceIn(0, 99)))
                    delay(BOOTSTRAP_POLL_MS.milliseconds)
                }
                false
            } ?: false
            if (bootstrapped) {
                AmberLogger.i(TAG, "Built-in Tor bootstrapped")
                _isRunning.value = true
                updateStatus(TorStatus.Connected)
            } else if (currentCoroutineContext().isActive) {
                AmberLogger.e(TAG, "Built-in Tor bootstrap timed out")
                failCurrentBootstrap("Tor bootstrap timed out")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AmberLogger.e(TAG, "Built-in Tor bootstrap check failed", e)
            failCurrentBootstrap(e.message)
        }
    }

    private suspend fun failCurrentBootstrap(message: String?) {
        val currentJob = currentCoroutineContext()[Job]
        startStopLock.withLock {
            if (bootstrapWatcher == currentJob) {
                bootstrapWatcher = null
                fail(message)
            }
        }
    }

    @Suppress("NoComments")
    private fun bindSocksProxy(): Int? {
        TorSocksAuthenticator.install()
        repeat(SOCKS_BIND_ATTEMPTS) {
            val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
            val (username, password) = TorSocksAuthenticator.newCredentials()
            val result = ArtiNative.startSocksProxy(port, username, password)
            if (result == 0) {
                // Published before the port, so no dial can reach the listener without them.
                TorSocksAuthenticator.set(port, username, password)
                return port
            }
            AmberLogger.e(TAG, "Failed to bind the Tor SOCKS proxy on port $port ($result)")
        }
        return null
    }

    private fun fail(message: String?) {
        _isRunning.value = false
        _socksPort.value = FAIL_CLOSED_SOCKS_PORT
        TorSocksAuthenticator.clear()
        destroyNativeClient()
        updateStatus(TorStatus.Failed(message))
    }

    private fun stopLocked() {
        bootstrapWatcher?.cancel()
        bootstrapWatcher = null
        _isRunning.value = false
        _socksPort.value = FAIL_CLOSED_SOCKS_PORT
        TorSocksAuthenticator.clear()
        updateStatus(TorStatus.Stopped)
        destroyNativeClient()
        AmberLogger.i(TAG, "Built-in Tor stopped")
    }

    private fun destroyNativeClient() {
        if (!nativeLibraryLoaded) return
        try {
            ArtiNative.destroy()
        } catch (e: Throwable) {
            AmberLogger.e(TAG, "Error destroying built-in Tor", e)
        }
    }
}

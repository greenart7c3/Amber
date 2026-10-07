package com.greenart7c3.nostrsigner.desktop.core

import com.greenart7c3.nostrsigner.tor.ArtiNative
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
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
    private const val SOCKS_BIND_ATTEMPTS = 5

    /** Serializes start/stop/restart. */
    private val lifecycle = Mutex()

    /** The bootstrap watcher; non-null while Tor is up or coming up. */
    @Volatile private var job: Job? = null

    /** True once the native library loaded; native calls are skipped until then. */
    @Volatile private var nativeReady = false

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /** 0 while there is no SOCKS listener: the fail-closed placeholder port. */
    private val _socksPort = MutableStateFlow(0)
    val socksPort: StateFlow<Int> = _socksPort.asStateFlow()

    private val _status = MutableStateFlow<TorStatus>(TorStatus.Stopped)
    val status: StateFlow<TorStatus> = _status.asStateFlow()

    private fun updateStatus(status: TorStatus) {
        _status.value = status
    }

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) { lifecycle.withLock { startLocked(scope) } }
    }

    fun stop(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) { lifecycle.withLock { stopLocked() } }
    }

    /**
     * Restarts from scratch: besides the SOCKS listener, the Tor client itself
     * is rebuilt (fresh circuits), as restarting the old tor daemon did.
     */
    fun restart(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            lifecycle.withLock {
                AmberLogger.i(TAG, "Restarting built-in Tor")
                stopLocked()
                if (nativeReady) ArtiNative.destroy()
                startLocked(scope)
            }
        }
    }

    /** Stops the SOCKS listener at application shutdown; Arti itself ends with the process. */
    fun shutdown() {
        job?.cancel()
        if (nativeReady) ArtiNative.stopSocksProxy()
    }

    private suspend fun startLocked(scope: CoroutineScope) {
        if (job != null) return
        updateStatus(TorStatus.Connecting(0))
        try {
            ArtiNative.setLogCallback { line -> AmberLogger.d(TAG, line) }
            nativeReady = true
            AmberLogger.i(TAG, "Starting built-in Tor: ${ArtiNative.getVersion()}")

            // kmp-tor's working directory from before the switch to Arti.
            File(AppDirs.dataDir, "kmptor").takeIf { it.exists() }?.let { runCatching { it.deleteRecursively() } }

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
            job = scope.launch(Dispatchers.IO) { awaitBootstrap() }
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
    private suspend fun awaitBootstrap() {
        try {
            while (currentCoroutineContext().isActive) {
                if (ArtiNative.isBootstrapped() == 1) {
                    AmberLogger.i(TAG, "Built-in Tor bootstrapped")
                    _isRunning.value = true
                    updateStatus(TorStatus.Connected)
                    return
                }
                val permille = ArtiNative.bootstrapProgressPermille()
                updateStatus(TorStatus.Connecting((permille / 10).coerceIn(0, 99)))
                delay(BOOTSTRAP_POLL_MS)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            AmberLogger.e(TAG, "Built-in Tor bootstrap check failed", e)
            fail(e.message)
        }
    }

    private fun bindSocksProxy(): Int? {
        repeat(SOCKS_BIND_ATTEMPTS) {
            val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
            val result = ArtiNative.startSocksProxy(port)
            if (result == 0) return port
            AmberLogger.e(TAG, "Failed to bind the Tor SOCKS proxy on port $port ($result)")
        }
        return null
    }

    private fun fail(message: String?) {
        _isRunning.value = false
        _socksPort.value = 0
        updateStatus(TorStatus.Failed(message))
    }

    private fun stopLocked() {
        job?.cancel()
        job = null
        _isRunning.value = false
        _socksPort.value = 0
        updateStatus(TorStatus.Stopped)
        if (!nativeReady) return
        try {
            ArtiNative.stopSocksProxy()
            AmberLogger.i(TAG, "Built-in Tor stopped")
        } catch (e: Throwable) {
            AmberLogger.e(TAG, "Error stopping built-in Tor", e)
        }
    }
}

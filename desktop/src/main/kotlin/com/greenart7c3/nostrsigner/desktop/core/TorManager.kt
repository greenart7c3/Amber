package com.greenart7c3.nostrsigner.desktop.core

import io.matthewnelson.kmp.tor.resource.exec.tor.ResourceLoaderTorExec
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonAsync
import io.matthewnelson.kmp.tor.runtime.Action.Companion.stopDaemonAsync
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.TorState
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class TorStatus {
    data object Stopped : TorStatus()

    data class Connecting(val percentage: Int) : TorStatus()

    data object Connected : TorStatus()

    data class Failed(val message: String?) : TorStatus()
}

/**
 * Desktop port of the Android `TorManager` (free flavor): runs the bundled
 * tor executable through kmp-tor and publishes its SOCKS port. Status is
 * surfaced in Settings instead of an OS notification.
 */
object TorManager {
    private const val TAG = "TorManager"

    @Volatile private var torRuntime: TorRuntime? = null

    @Volatile private var stopRequested = false

    @Volatile private var daemonStarted = false

    @Volatile private var bootstrapped = false

    private val lifecycle = Mutex()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    /** 0 while the daemon has no SOCKS listener: the fail-closed placeholder port. */
    private val _socksPort = MutableStateFlow(0)
    val socksPort: StateFlow<Int> = _socksPort.asStateFlow()

    private val _status = MutableStateFlow<TorStatus>(TorStatus.Stopped)
    val status: StateFlow<TorStatus> = _status.asStateFlow()

    private fun updateStatus(status: TorStatus) {
        _status.value = status
    }

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) { lifecycle.withLock { startLocked() } }
    }

    fun stop(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) { lifecycle.withLock { stopLocked() } }
    }

    fun restart(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            lifecycle.withLock {
                AmberLogger.i(TAG, "Restarting built-in Tor")
                stopLocked()
                startLocked()
            }
        }
    }

    /** Blocking stop for application shutdown, so the tor process does not outlive Amber. */
    fun shutdown() {
        runBlocking(Dispatchers.IO) { lifecycle.withLock { stopLocked() } }
    }

    private suspend fun startLocked() {
        if (torRuntime != null) return
        stopRequested = false
        daemonStarted = false
        bootstrapped = false
        updateStatus(TorStatus.Connecting(0))
        try {
            val workDir = File(AppDirs.dataDir, "kmptor").apply { mkdirs() }
            AppDirs.restrictToOwner(workDir)
            val cacheDir = File(workDir, "cache")

            val env = TorRuntime.Environment.Builder(workDir, cacheDir, ResourceLoaderTorExec::getOrCreate)

            val runtime = TorRuntime.Builder(env) {
                RuntimeEvent.entries().forEach { event ->
                    observerStatic(event, OnEvent.Executor.Immediate) { data ->
                        AmberLogger.d(TAG, data.toString())
                    }
                }

                observerStatic(RuntimeEvent.STATE, OnEvent.Executor.Immediate) { state ->
                    when (val daemon = state.daemon) {
                        is TorState.Daemon.Starting -> {
                            daemonStarted = true
                            updateStatus(TorStatus.Connecting(0))
                        }

                        is TorState.Daemon.On -> {
                            daemonStarted = true
                            bootstrapped = daemon.isBootstrapped
                            if (daemon.isBootstrapped && _socksPort.value > 0) {
                                _isRunning.value = true
                                updateStatus(TorStatus.Connected)
                            } else {
                                updateStatus(TorStatus.Connecting(daemon.bootstrap.toInt()))
                            }
                        }

                        is TorState.Daemon.Stopping -> {}

                        is TorState.Daemon.Off -> {
                            if (daemonStarted && !stopRequested) {
                                AmberLogger.e(TAG, "Built-in Tor daemon stopped unexpectedly")
                                bootstrapped = false
                                _isRunning.value = false
                                _socksPort.value = 0
                                updateStatus(TorStatus.Failed(null))
                            }
                        }
                    }
                }

                observerStatic(RuntimeEvent.ERROR, OnEvent.Executor.Immediate) { error ->
                    AmberLogger.e(TAG, "Built-in Tor runtime error", error)
                    if (_status.value !is TorStatus.Connected) {
                        updateStatus(TorStatus.Failed(error.message))
                    }
                }

                observerStatic(RuntimeEvent.LISTENERS, OnEvent.Executor.Immediate) { listeners ->
                    val addr = listeners.socks.firstOrNull()
                    if (addr != null) {
                        val port = addr.port.value
                        AmberLogger.i(TAG, "Built-in Tor SOCKS proxy on port $port")
                        _socksPort.value = port
                        if (bootstrapped) {
                            _isRunning.value = true
                            updateStatus(TorStatus.Connected)
                        }
                    } else {
                        _socksPort.value = 0
                        _isRunning.value = false
                    }
                }

                config {
                    TorOption.__SocksPort.configure { auto() }
                }
            }

            torRuntime = runtime
            runtime.startDaemonAsync()
            AmberLogger.i(TAG, "Built-in Tor started")
        } catch (e: Exception) {
            AmberLogger.e(TAG, "Failed to start built-in Tor", e)
            runCatching { torRuntime?.stopDaemonAsync() }
            torRuntime = null
            _isRunning.value = false
            _socksPort.value = 0
            updateStatus(TorStatus.Failed(e.message))
        }
    }

    private suspend fun stopLocked() {
        stopRequested = true
        bootstrapped = false
        val runtime = torRuntime
        torRuntime = null
        _isRunning.value = false
        _socksPort.value = 0
        updateStatus(TorStatus.Stopped)
        if (runtime == null) return
        try {
            runtime.stopDaemonAsync()
            AmberLogger.i(TAG, "Built-in Tor stopped")
        } catch (e: Exception) {
            AmberLogger.e(TAG, "Error stopping built-in Tor", e)
        }
    }
}

package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.util.Log
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import io.matthewnelson.kmp.tor.resource.exec.tor.ResourceLoaderTorExec
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonAsync
import io.matthewnelson.kmp.tor.runtime.Action.Companion.stopDaemonAsync
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.core.OnEvent
import io.matthewnelson.kmp.tor.runtime.core.config.TorOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object TorManager {
    private const val TAG = "TorManager"

    @Volatile
    private var torRuntime: TorRuntime? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun start(context: Context, scope: CoroutineScope) {
        if (torRuntime != null) return
        scope.launch(Dispatchers.IO) {
            try {
                val workDir = context.filesDir.resolve("kmptor")
                val cacheDir = context.cacheDir.resolve("kmptor")

                val env = TorRuntime.Environment.Builder(workDir, cacheDir, ResourceLoaderTorExec::getOrCreate)

                val runtime = TorRuntime.Builder(env) {
                    // Log all runtime events for debugging
                    RuntimeEvent.entries().forEach { event ->
                        observerStatic(event, OnEvent.Executor.Immediate) { data ->
                            Log.d(TAG, data.toString())
                        }
                    }

                    // Observe SOCKS listeners to get the assigned port
                    observerStatic(RuntimeEvent.LISTENERS, OnEvent.Executor.Immediate) { listeners ->
                        val addr = listeners.socks.firstOrNull()
                        if (addr != null) {
                            try {
                                val port = addr.port.value
                                Log.i(TAG, "Built-in Tor SOCKS proxy on port $port")
                                HttpClientManager.setDefaultProxyOnPort(port)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to read Tor SOCKS port", e)
                            }
                        }
                    }

                    config {
                        TorOption.__SocksPort.configure { auto() }
                    }
                }

                torRuntime = runtime
                runtime.startDaemonAsync()
                _isRunning.value = true
                Log.i(TAG, "Built-in Tor started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start built-in Tor", e)
                torRuntime = null
                _isRunning.value = false
            }
        }
    }

    fun stop() {
        val runtime = torRuntime ?: return
        torRuntime = null
        _isRunning.value = false
        try {
            runBlocking(Dispatchers.IO) { runtime.stopDaemonAsync() }
            Log.i(TAG, "Built-in Tor stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping built-in Tor", e)
        }
    }
}

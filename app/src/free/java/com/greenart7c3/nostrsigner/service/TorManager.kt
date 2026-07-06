package com.greenart7c3.nostrsigner.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.MainActivity
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import io.matthewnelson.kmp.tor.resource.exec.tor.ResourceLoaderTorExec
import io.matthewnelson.kmp.tor.runtime.Action.Companion.startDaemonAsync
import io.matthewnelson.kmp.tor.runtime.Action.Companion.stopDaemonAsync
import io.matthewnelson.kmp.tor.runtime.RuntimeEvent
import io.matthewnelson.kmp.tor.runtime.TorRuntime
import io.matthewnelson.kmp.tor.runtime.TorState
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
    private const val TOR_NOTIFICATION_ID = 3
    private const val TOR_CHANNEL_ID = "TorChannel"
    private const val TOR_CHANNEL_GROUP_ID = "TorGroup"
    private const val TOR_RESTART_REQUEST_CODE = 15

    @Volatile private var appContext: Context? = null

    @Volatile
    private var torRuntime: TorRuntime? = null

    @Volatile
    private var portCollectorStarted = false

    @Volatile
    private var stopRequested = false

    @Volatile
    private var restarting = false

    @Volatile
    private var daemonStarted = false

    @Volatile
    private var bootstrapped = false

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _socksPort = MutableStateFlow(0)
    val socksPort: StateFlow<Int> = _socksPort.asStateFlow()

    private val _status = MutableStateFlow<TorStatus>(TorStatus.Stopped)
    val status: StateFlow<TorStatus> = _status.asStateFlow()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val ctx = appContext ?: return
        val group = NotificationChannelGroupCompat.Builder(TOR_CHANNEL_GROUP_ID)
            .setName(ctx.getString(R.string.builtin_tor_title))
            .setDescription(ctx.getString(R.string.tor_status_channel_description))
            .build()
        val channel = NotificationChannelCompat.Builder(TOR_CHANNEL_ID, NotificationManager.IMPORTANCE_LOW)
            .setName(ctx.getString(R.string.builtin_tor_title))
            .setDescription(ctx.getString(R.string.tor_status_channel_description))
            .setSound(null, null)
            .setGroup(group.id)
            .build()
        val nm = NotificationManagerCompat.from(ctx)
        nm.createNotificationChannelGroup(group)
        nm.createNotificationChannel(channel)
    }

    private fun updateStatus(status: TorStatus) {
        if (_status.value == status) return
        _status.value = status
        val ctx = appContext ?: return
        when (status) {
            is TorStatus.Stopped -> cancelNotification()
            is TorStatus.Connecting -> {
                val text = if (status.percentage <= 0) {
                    ctx.getString(R.string.tor_starting)
                } else {
                    ctx.getString(R.string.tor_connecting, status.percentage)
                }
                showNotification(text, progress = status.percentage)
            }
            is TorStatus.Connected -> showNotification(ctx.getString(R.string.builtin_tor_active))
            is TorStatus.Failed -> {
                val text = if (status.message.isNullOrBlank()) {
                    ctx.getString(R.string.tor_connection_failed)
                } else {
                    "${ctx.getString(R.string.tor_connection_failed)}: ${status.message}"
                }
                showNotification(text, ongoing = false)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(text: String, progress: Int? = null, ongoing: Boolean = true) {
        val ctx = appContext ?: return
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val contentIntent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(ctx, 0, contentIntent, PendingIntent.FLAG_MUTABLE)
        val restartIntent = Intent(ctx, TorRestartReceiver::class.java)
        val restartPendingIntent = PendingIntent.getBroadcast(
            ctx,
            TOR_RESTART_REQUEST_CODE,
            restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(ctx, TOR_CHANNEL_ID)
            .setContentTitle(ctx.getString(R.string.builtin_tor_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_tor)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .addAction(0, ctx.getString(R.string.tor_restart), restartPendingIntent)
        if (progress != null) {
            builder.setProgress(100, progress, progress <= 0)
        }
        NotificationManagerCompat.from(ctx).notify(TOR_NOTIFICATION_ID, builder.build())
    }

    fun cancelNotification() {
        appContext?.let { NotificationManagerCompat.from(it).cancel(TOR_NOTIFICATION_ID) }
    }

    fun showRetrying() {
        appContext?.let { showNotification(it.getString(R.string.tor_retrying)) }
    }

    fun start(context: Context, scope: CoroutineScope) {
        if (appContext == null) {
            appContext = context.applicationContext
            createNotificationChannel()
        }
        if (!portCollectorStarted) {
            portCollectorStarted = true
            scope.launch(Dispatchers.IO) {
                _socksPort.collect { port ->
                    if (port > 0) {
                        HttpClientManager.setDefaultProxyOnPort(port)
                    }
                }
            }
        }
        if (torRuntime != null) return
        stopRequested = false
        daemonStarted = false
        bootstrapped = false
        updateStatus(TorStatus.Connecting(0))
        scope.launch(Dispatchers.IO) {
            try {
                val workDir = context.filesDir.resolve("kmptor")
                val cacheDir = context.cacheDir.resolve("kmptor")

                val env = TorRuntime.Environment.Builder(workDir, cacheDir, ResourceLoaderTorExec::getOrCreate)

                val runtime = TorRuntime.Builder(env) {
                    // Log all runtime events for debugging
                    RuntimeEvent.entries().forEach { event ->
                        observerStatic(event, OnEvent.Executor.Immediate) { data ->
                            AmberLog.d(TAG, data.toString())
                        }
                    }

                    // Observe the daemon state to report bootstrap progress
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
                                    updateStatus(TorStatus.Connected)
                                } else {
                                    updateStatus(TorStatus.Connecting(daemon.bootstrap.toInt()))
                                }
                            }
                            is TorState.Daemon.Stopping -> {}
                            is TorState.Daemon.Off -> {
                                if (daemonStarted && !stopRequested) {
                                    AmberLog.e(TAG, "Built-in Tor daemon stopped unexpectedly")
                                    bootstrapped = false
                                    _isRunning.value = false
                                    updateStatus(TorStatus.Failed(null))
                                }
                            }
                        }
                    }

                    // Surface runtime errors in the status notification
                    observerStatic(RuntimeEvent.ERROR, OnEvent.Executor.Immediate) { error ->
                        AmberLog.e(TAG, "Built-in Tor runtime error", error)
                        if (_status.value !is TorStatus.Connected) {
                            updateStatus(TorStatus.Failed(error.message))
                        }
                    }

                    // Observe SOCKS listeners to get the assigned port
                    observerStatic(RuntimeEvent.LISTENERS, OnEvent.Executor.Immediate) { listeners ->
                        val addr = listeners.socks.firstOrNull()
                        if (addr != null) {
                            try {
                                val port = addr.port.value
                                AmberLog.i(TAG, "Built-in Tor SOCKS proxy on port $port")
                                _socksPort.value = port
                                _isRunning.value = true
                                if (bootstrapped) {
                                    updateStatus(TorStatus.Connected)
                                }
                            } catch (e: Exception) {
                                AmberLog.e(TAG, "Failed to read Tor SOCKS port", e)
                            }
                        }
                    }

                    config {
                        TorOption.__SocksPort.configure { auto() }
                    }
                }

                torRuntime = runtime
                runtime.startDaemonAsync()
                AmberLog.i(TAG, "Built-in Tor started")
            } catch (e: Exception) {
                AmberLog.e(TAG, "Failed to start built-in Tor", e)
                torRuntime = null
                _isRunning.value = false
                updateStatus(TorStatus.Failed(e.message))
            }
        }
    }

    fun restart(context: Context, scope: CoroutineScope) {
        if (restarting) return
        restarting = true
        scope.launch(Dispatchers.IO) {
            try {
                AmberLog.i(TAG, "Restarting built-in Tor")
                stop()
                start(context, scope)
            } finally {
                restarting = false
            }
        }
    }

    fun stop() {
        stopRequested = true
        bootstrapped = false
        val runtime = torRuntime
        torRuntime = null
        _isRunning.value = false
        _socksPort.value = 0
        updateStatus(TorStatus.Stopped)
        cancelNotification()
        if (runtime == null) return
        try {
            runBlocking(Dispatchers.IO) { runtime.stopDaemonAsync() }
            AmberLog.i(TAG, "Built-in Tor stopped")
        } catch (e: Exception) {
            AmberLog.e(TAG, "Error stopping built-in Tor", e)
        }
    }
}

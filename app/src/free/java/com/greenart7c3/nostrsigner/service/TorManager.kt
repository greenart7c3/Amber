package com.greenart7c3.nostrsigner.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.greenart7c3.nostrsigner.MainActivity
import com.greenart7c3.nostrsigner.R
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
    private const val TOR_NOTIFICATION_ID = 3
    private const val TOR_CHANNEL_ID = "TorChannel"
    private const val TOR_CHANNEL_GROUP_ID = "TorGroup"

    @Volatile private var appContext: Context? = null

    @Volatile
    private var torRuntime: TorRuntime? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

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

    @SuppressLint("MissingPermission")
    private fun showNotification(text: String) {
        val ctx = appContext ?: return
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val contentIntent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(ctx, 0, contentIntent, PendingIntent.FLAG_MUTABLE)
        val notification = NotificationCompat.Builder(ctx, TOR_CHANNEL_ID)
            .setContentTitle(ctx.getString(R.string.builtin_tor_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_tor)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(ctx).notify(TOR_NOTIFICATION_ID, notification)
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
        if (torRuntime != null) return
        showNotification(context.getString(R.string.tor_starting))
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
                                _isRunning.value = true
                                appContext?.let { showNotification(it.getString(R.string.builtin_tor_active)) }
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
        cancelNotification()
        try {
            runBlocking(Dispatchers.IO) { runtime.stopDaemonAsync() }
            Log.i(TAG, "Built-in Tor stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping built-in Tor", e)
        }
    }
}

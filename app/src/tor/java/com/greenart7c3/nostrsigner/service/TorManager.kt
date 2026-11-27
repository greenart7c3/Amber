package com.greenart7c3.nostrsigner.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.TrafficStats
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.MainActivity
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.common.tor.TorSocksAuthenticator
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.builtin_tor_active
import com.greenart7c3.nostrsigner.commonsui.builtin_tor_title
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.tor_connecting
import com.greenart7c3.nostrsigner.commonsui.tor_connection_failed
import com.greenart7c3.nostrsigner.commonsui.tor_restart
import com.greenart7c3.nostrsigner.commonsui.tor_retrying
import com.greenart7c3.nostrsigner.commonsui.tor_starting
import com.greenart7c3.nostrsigner.commonsui.tor_status_channel_description
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
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
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Built-in Tor, backed by Arti (Tor in Rust) running in-process through
 * [ArtiNative]. Shared by the `free` and `benchmark` flavors.
 */
object TorManager {
    private const val TAG = "TorManager"
    private const val TOR_NOTIFICATION_ID = 3
    private const val TOR_CHANNEL_ID = "TorChannel"
    private const val TOR_CHANNEL_GROUP_ID = "TorGroup"
    private const val TOR_RESTART_REQUEST_CODE = 15
    private const val TOR_CONTENT_REQUEST_CODE = 16
    private const val BOOTSTRAP_POLL_MS = 500L
    private const val BOOTSTRAP_TIMEOUT_MS = 120_000L
    private const val SOCKS_BIND_ATTEMPTS = 5

    @Volatile private var appContext: Context? = null

    @Volatile private var startAndBootstrapJob: Job? = null

    private val socksListenerLock = Any()

    @Volatile private var nativeLibraryLoaded = false

    @Volatile
    private var portCollectorStarted = false

    @Volatile
    private var restarting = false

    internal var destroyArtiClient: () -> Unit = ::destroyNativeClient
    internal var checkBootstrapped: () -> Int = { ArtiNative.isBootstrapped() }
    internal var readBootstrapProgressPermille: () -> Int = { ArtiNative.bootstrapProgressPermille() }
    internal var bootstrapTimeoutMs: Long = BOOTSTRAP_TIMEOUT_MS
    internal var bootstrapPollMs: Long = BOOTSTRAP_POLL_MS

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _socksPort = MutableStateFlow(0)
    val socksPort: StateFlow<Int> = _socksPort.asStateFlow()

    private val _status = MutableStateFlow<TorStatus>(TorStatus.Stopped)
    val status: StateFlow<TorStatus> = _status.asStateFlow()

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        TorSocksAuthenticator.install()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val ctx = appContext ?: return
        val group = NotificationChannelGroupCompat.Builder(TOR_CHANNEL_GROUP_ID)
            .setName(getStringBlocking(Res.string.builtin_tor_title))
            .setDescription(getStringBlocking(Res.string.tor_status_channel_description))
            .build()
        val channel = NotificationChannelCompat.Builder(TOR_CHANNEL_ID, NotificationManager.IMPORTANCE_LOW)
            .setName(getStringBlocking(Res.string.builtin_tor_title))
            .setDescription(getStringBlocking(Res.string.tor_status_channel_description))
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
        if (appContext == null) return
        when (status) {
            is TorStatus.Stopped -> cancelNotification()
            is TorStatus.Connecting -> {
                val text = if (status.percentage <= 0) {
                    getStringBlocking(Res.string.tor_starting)
                } else {
                    getStringBlocking(Res.string.tor_connecting, status.percentage)
                }
                showNotification(text, progress = status.percentage)
            }
            is TorStatus.Connected -> showNotification(getStringBlocking(Res.string.builtin_tor_active))
            is TorStatus.Failed -> {
                val text = if (status.message.isNullOrBlank()) {
                    getStringBlocking(Res.string.tor_connection_failed)
                } else {
                    "${getStringBlocking(Res.string.tor_connection_failed)}: ${status.message}"
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
        val pendingIntent = PendingIntent.getActivity(ctx, TOR_CONTENT_REQUEST_CODE, contentIntent, PendingIntent.FLAG_IMMUTABLE)
        val restartIntent = Intent(ctx, TorRestartReceiver::class.java)
        val restartPendingIntent = PendingIntent.getBroadcast(
            ctx,
            TOR_RESTART_REQUEST_CODE,
            restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(ctx, TOR_CHANNEL_ID)
            .setContentTitle(getStringBlocking(Res.string.builtin_tor_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_tor)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .addAction(0, getStringBlocking(Res.string.tor_restart), restartPendingIntent)
        if (progress != null) {
            builder.setProgress(100, progress, progress <= 0)
        }
        NotificationManagerCompat.from(ctx).notify(TOR_NOTIFICATION_ID, builder.build())
    }

    fun cancelNotification() {
        appContext?.let { NotificationManagerCompat.from(it).cancel(TOR_NOTIFICATION_ID) }
    }

    fun showRetrying() {
        appContext?.let { showNotification(getStringBlocking(Res.string.tor_retrying)) }
    }

    /**
     * Shows the terminal failure state (used after the bounded startup retries
     * in Amber.runMigrations give up). Non-ongoing so the user can swipe it
     * away; the Restart action is the way back to a connection attempt.
     */
    fun showFailed() {
        updateStatus(TorStatus.Failed(null))
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
        synchronized(this) {
            if (startAndBootstrapJob?.isActive == true) return
            updateStatus(TorStatus.Connecting(0))
            val ctx = context.applicationContext
            startAndBootstrapJob = scope.launch(Dispatchers.IO) { run(ctx) }
        }
    }

    @Suppress("NoComments")
    private suspend fun run(context: Context) {
        try {
            ArtiNative.setLogCallback { line -> AmberLog.d(TAG, line) }
            nativeLibraryLoaded = true
            AmberLog.i(TAG, "Starting built-in Tor: ${ArtiNative.getVersion()}")
            deleteLegacyKmpTorData(context)

            val dataDir = File(context.filesDir, "arti").apply { mkdirs() }
            val result = ArtiNative.initialize(dataDir.absolutePath)
            if (result != 0) {
                fail("Arti failed to initialize ($result)")
                return
            }
            if (!currentCoroutineContext().isActive) {
                destroyArtiClient()
                return
            }

            val port = bindSocksProxy() ?: return
            AmberLog.i(TAG, "Built-in Tor SOCKS proxy on port $port")
            _socksPort.value = port

            reportConnectedOnceBootstrapped()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Throwable: a missing/broken native library surfaces as an Error
            // (UnsatisfiedLinkError, ExceptionInInitializerError).
            AmberLog.e(TAG, "Failed to start built-in Tor", e)
            fail(e.message)
        }
    }

    @Suppress("NoComments")
    internal suspend fun reportConnectedOnceBootstrapped() {
        // The proxy accepts connections right away (each one waits for its
        // circuit), but report Connected only once Arti can carry traffic,
        // like the C tor daemon's bootstrap did.
        val bootstrapped = withTimeoutOrNull(bootstrapTimeoutMs.milliseconds) {
            while (currentCoroutineContext().isActive) {
                if (checkBootstrapped() == 1) {
                    return@withTimeoutOrNull true
                }
                val permille = readBootstrapProgressPermille()
                updateStatus(TorStatus.Connecting((permille / 10).coerceIn(0, 99)))
                delay(bootstrapPollMs.milliseconds)
            }
            false
        } ?: false
        if (bootstrapped) {
            AmberLog.i(TAG, "Built-in Tor bootstrapped")
            _isRunning.value = true
            updateStatus(TorStatus.Connected)
        } else if (currentCoroutineContext().isActive) {
            AmberLog.e(TAG, "Built-in Tor bootstrap timed out")
            fail(null)
        }
    }

    @Suppress("NoComments")
    private suspend fun bindSocksProxy(): Int? {
        val context = currentCoroutineContext()
        tagProbeSocketForStrictMode()
        repeat(SOCKS_BIND_ATTEMPTS) {
            val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
            val (username, password) = TorSocksAuthenticator.newCredentials()
            val result = synchronized(socksListenerLock) {
                val stopRanWhileInitializeBlocked = !context.isActive
                if (stopRanWhileInitializeBlocked) return null
                ArtiNative.startSocksProxy(port, username, password)
            }
            if (result == 0) {
                // Published before the port, so no dial can reach the listener without them.
                TorSocksAuthenticator.set(port, username, password)
                return port
            }
            AmberLog.e(TAG, "Failed to bind the Tor SOCKS proxy on port $port ($result)")
        }
        fail("Could not open the Tor SOCKS port")
        return null
    }

    private fun tagProbeSocketForStrictMode() {
        if (TrafficStats.getThreadStatsTag() == -1) {
            TrafficStats.setThreadStatsTag(0x0001)
        }
    }

    /**
     * Drops the SOCKS port and, while OkHttp still dials through it, replaces
     * the proxy with a fail-closed placeholder. Without this, the released
     * loopback port stays configured until Tor rebinds, and a co-installed app
     * that binds it first receives Amber's proxied dials.
     */
    @Suppress("NoComments")
    private fun releaseSocksPort() {
        val released = _socksPort.value
        _socksPort.value = 0
        TorSocksAuthenticator.clear()
        if (released > 0) {
            HttpClientManager.releaseProxyPort(released)
        }
    }

    private fun fail(message: String?) {
        _isRunning.value = false
        releaseSocksPort()
        destroyArtiClient()
        updateStatus(TorStatus.Failed(message))
    }

    private fun deleteLegacyKmpTorData(context: Context) {
        listOf(File(context.filesDir, "kmptor"), File(context.cacheDir, "kmptor")).forEach { dir ->
            if (dir.exists()) {
                runCatching { dir.deleteRecursively() }
            }
        }
    }

    /**
     * Restarts from scratch: besides the SOCKS listener, the Tor client itself
     * is rebuilt (fresh circuits), as restarting the old tor daemon did.
     */
    fun restart(context: Context, scope: CoroutineScope) {
        if (restarting) return
        restarting = true
        scope.launch(Dispatchers.IO) {
            try {
                AmberLog.i(TAG, "Restarting built-in Tor")
                val previous = startAndBootstrapJob
                cancelJobAndResetState()
                previous?.join()
                destroyArtiClient()
                start(context, scope)
            } finally {
                restarting = false
            }
        }
    }

    /**
     * Stops the SOCKS listener and destroys the Tor client so background guard
     * and directory tasks stop running.
     */
    fun stop() {
        cancelJobAndResetState()
        destroyArtiClient()
        AmberLog.i(TAG, "Built-in Tor stopped")
    }

    private fun cancelJobAndResetState() {
        synchronized(this) {
            startAndBootstrapJob?.cancel()
            startAndBootstrapJob = null
        }
        _isRunning.value = false
        releaseSocksPort()
        updateStatus(TorStatus.Stopped)
        cancelNotification()
    }

    private fun destroyNativeClient() {
        if (!nativeLibraryLoaded) return
        try {
            synchronized(socksListenerLock) { ArtiNative.destroy() }
        } catch (e: Throwable) {
            AmberLog.e(TAG, "Error destroying built-in Tor", e)
        }
    }
}

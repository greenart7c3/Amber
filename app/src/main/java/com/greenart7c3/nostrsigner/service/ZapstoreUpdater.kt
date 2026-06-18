package com.greenart7c3.nostrsigner.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.BuildFlavorChecker
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.models.UpdateChannel
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EoseMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

private const val EOSE_TIMEOUT_MS = 15_000L
private const val RELEASE_KIND = 30063
private val UPDATE_RELAY_URLS = listOf(
    "wss://relay.zapstore.dev",
    "wss://relay.damus.io",
    "wss://nos.lol",
)
private const val APP_IDENTIFIER = "com.greenart7c3.nostrsigner"

enum class DownloadState {
    IDLE,
    DOWNLOADING,
    INSTALLING,
    ERROR,
}

class ZapstoreUpdater(
    private val client: NostrClient,
    private val scope: CoroutineScope,
) : RelayConnectionListener {
    val latestRelease = MutableStateFlow<ZapstoreRelease?>(null)
    val isChecking = MutableStateFlow(false)
    val downloadState = MutableStateFlow(DownloadState.IDLE)
    val downloadProgress = MutableStateFlow(0f)

    private val releaseSubId = UUID.randomUUID().toString()
    private val fileSubId = UUID.randomUUID().toString()
    private val pendingFileEventIds = mutableListOf<String>()
    private val versionByEventId = mutableMapOf<String, Pair<String, Long>>()
    private val updateRelays: List<NormalizedRelayUrl> =
        UPDATE_RELAY_URLS.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
    private var timeoutJob: Job? = null

    // Messages are processed sequentially in arrival order through this channel.
    // Launching a coroutine per message let the release EOSE run before the release
    // EVENT had populated pendingFileEventIds, so the file subscription never opened.
    private val messages = Channel<Message>(Channel.UNLIMITED)

    init {
        client.addConnectionListener(this)
        scope.launch {
            for (msg in messages) {
                when (msg) {
                    is EventMessage if msg.subId == releaseSubId -> processReleaseEvent(msg.event)
                    is EventMessage if msg.subId == fileSubId -> processFileEvent(msg.event)
                    is EoseMessage if msg.subId == releaseSubId -> onReleaseEose()
                    is EoseMessage if msg.subId == fileSubId -> finishCheck()
                    else -> {}
                }
            }
        }
    }

    override fun onIncomingMessage(relay: IRelayClient, msgStr: String, msg: Message) {
        when (msg) {
            is EventMessage if (msg.subId == releaseSubId || msg.subId == fileSubId) -> messages.trySend(msg)
            is EoseMessage if (msg.subId == releaseSubId || msg.subId == fileSubId) -> messages.trySend(msg)
            else -> {}
        }
        super.onIncomingMessage(relay, msgStr, msg)
    }

    suspend fun checkForUpdates() {
        if (BuildFlavorChecker.isOfflineFlavor()) return
        if (updateRelays.isEmpty()) return
        if (isChecking.value) return

        isChecking.value = true
        pendingFileEventIds.clear()
        versionByEventId.clear()
        latestRelease.value = null

        // Subscribe to release events (kind 30063) from the developer
        val releaseFilter = Filter(
            kinds = listOf(RELEASE_KIND),
            authors = listOf(Amber.DEVELOPER_HEX_KEY),
            tags = mapOf("i" to listOf("com.greenart7c3.nostrsigner")),
            limit = 1,
        )

        val settings = withContext(Dispatchers.IO) {
            LocalPreferences.loadSettingsFromEncryptedStorage()
        }
        if (settings.updateChannel == UpdateChannel.STABLE) {
            AmberLog.d("zapstore", "zapstore relay")
            client.subscribe(releaseSubId, listOf(updateRelays.first()).associateWith { listOf(releaseFilter) })
        } else {
            AmberLog.d("zapstore", "other relays")
            client.subscribe(releaseSubId, updateRelays.drop(1).associateWith { listOf(releaseFilter) })
        }

        timeoutJob = scope.launch {
            delay(EOSE_TIMEOUT_MS)
            AmberLog.w(Amber.TAG, "ZapstoreUpdater: timeout waiting for EOSE")
            onReleaseEose()
        }
    }

    private suspend fun processReleaseEvent(event: Event) {
        if (!event.verify()) {
            AmberLog.w(Amber.TAG, "ZapstoreUpdater: invalid release event: ${event.toJson()}")
            return
        }

        val tags = event.tags

        // Only process events for this app
        val identifier = tags.firstOrNull { it.size > 1 && it[0] == "d" }?.getOrNull(1) ?: ""
        val appId = tags.firstOrNull { it.size > 1 && it[0] == "i" }?.getOrNull(1) ?: ""
        val isForThisApp = identifier.startsWith(APP_IDENTIFIER) || appId == APP_IDENTIFIER
        if (!isForThisApp) return

        // Channel filter: stable users skip events tagged c=beta. Events with no `c`
        // tag (legacy releases) are treated as stable and shown on both channels.
        val channelTag = tags.firstOrNull { it.size > 1 && it[0] == "c" }?.getOrNull(1)
        val isBeta = channelTag.equals("beta", ignoreCase = true)
        val settings = withContext(Dispatchers.IO) {
            LocalPreferences.loadSettingsFromEncryptedStorage()
        }
        if (isBeta && settings.updateChannel == UpdateChannel.STABLE) return

        val version = tags.firstOrNull { it.size > 1 && it[0] == "version" }?.getOrNull(1) ?: return
        if (!isNewerVersion(version)) return

        AmberLog.d(Amber.TAG, "ZapstoreUpdater: found release $version (current: ${BuildConfig.VERSION_NAME})")

        // Try direct URL first (some release events include it)
        val directUrl = tags.firstOrNull { it.size > 1 && it[0] == "url" }?.getOrNull(1)
        if (directUrl != null) {
            val hash = tags.firstOrNull { it.size > 1 && (it[0] == "x" || it[0] == "hash") }?.getOrNull(1)
            updateLatestRelease(version, directUrl, hash, event.createdAt)
            return
        }

        // Collect file event IDs from e-tags for second subscription
        tags.filter { it.size > 1 && it[0] == "e" }.forEach { tag ->
            val eventId = tag[1]
            pendingFileEventIds.add(eventId)
            versionByEventId[eventId] = Pair(version, event.createdAt)
        }
    }

    private fun onReleaseEose() {
        if (pendingFileEventIds.isNotEmpty()) {
            fetchFileEvents()
        } else {
            finishCheck()
        }
    }

    private fun fetchFileEvents() {
        if (updateRelays.isEmpty()) {
            finishCheck()
            return
        }
        val fileFilter = Filter(
            ids = pendingFileEventIds.toList(),
        )
        client.subscribe(fileSubId, updateRelays.associateWith { listOf(fileFilter) })
    }

    private fun processFileEvent(event: Event) {
        if (!event.verify()) {
            AmberLog.w(Amber.TAG, "ZapstoreUpdater: invalid file event: ${event.toJson()}")
            return
        }

        val tags = event.tags
        val url = tags.firstOrNull { it.size > 1 && it[0] == "url" }?.getOrNull(1) ?: return
        val mimeType = tags.firstOrNull { it.size > 1 && it[0] == "m" }?.getOrNull(1)
        val platform = tags.firstOrNull { it.size > 1 && it[0] == "platform" }?.getOrNull(1)

        // Only process Android APK files
        val isApk = mimeType?.contains("android") == true || url.endsWith(".apk")
        val isAndroid = platform == null || platform == "android"
        if (!isApk || !isAndroid) return

        // Skip ABI-specific builds that don't match the running device ABI (prefer universal)
        val arch = tags.firstOrNull { it.size > 1 && it[0] == "arch" }?.getOrNull(1)
        if (arch != null && arch != "universal" && !isMatchingArch(arch)) return

        val hash = tags.firstOrNull { it.size > 1 && it[0] == "x" }?.getOrNull(1)
        val versionInfo = versionByEventId[event.id] ?: return
        val (version, createdAt) = versionInfo

        updateLatestRelease(version, url, hash, createdAt)
    }

    private fun isMatchingArch(arch: String): Boolean {
        val supportedAbis = android.os.Build.SUPPORTED_ABIS.toList()
        return supportedAbis.any { it.equals(arch, ignoreCase = true) }
    }

    private fun updateLatestRelease(version: String, url: String, hash: String?, createdAt: Long) {
        val current = latestRelease.value
        if (current == null || createdAt > current.createdAt) {
            latestRelease.value = ZapstoreRelease(version = version, url = url, hash = hash, createdAt = createdAt)
        }
    }

    private fun finishCheck() {
        timeoutJob?.cancel()
        timeoutJob = null
        Amber.instance.intentionalDisconnectTime = System.currentTimeMillis()
        client.unsubscribe(releaseSubId)
        client.unsubscribe(fileSubId)
        isChecking.value = false
        pendingFileEventIds.clear()
        latestRelease.value?.let { release ->
            val ctx = Amber.instance
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            with(NotificationUtils) { nm.sendUpdateAvailableNotification(release.version, ctx) }
        }
    }

    fun downloadAndInstall(context: Context, release: ZapstoreRelease) {
        if (BuildFlavorChecker.isOfflineFlavor()) return
        if (downloadState.value == DownloadState.DOWNLOADING) return

        scope.launch(Dispatchers.IO) {
            downloadState.value = DownloadState.DOWNLOADING
            downloadProgress.value = 0f
            try {
                val apkFile = downloadApk(context, release)
                if (apkFile != null) {
                    downloadState.value = DownloadState.INSTALLING
                    installApk(context, apkFile)
                } else {
                    downloadState.value = DownloadState.ERROR
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AmberLog.e(Amber.TAG, "ZapstoreUpdater: download failed", e)
                downloadState.value = DownloadState.ERROR
            }
        }
    }

    private suspend fun downloadApk(context: Context, release: ZapstoreRelease): File? {
        val settings = withContext(Dispatchers.IO) {
            LocalPreferences.loadSettingsFromEncryptedStorage()
        }
        val useProxy = settings.torMode != com.greenart7c3.nostrsigner.models.TorMode.DISABLED
        val client = HttpClientManager.getHttpClient(useProxy)
        val request = Request.Builder().url(release.url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            AmberLog.e(Amber.TAG, "ZapstoreUpdater: HTTP ${response.code} for ${release.url}")
            return null
        }

        val body = response.body ?: return null
        val contentLength = body.contentLength()
        val apkFile = File(context.cacheDir, "amber-update-${release.version}.apk")

        val digest = if (release.hash != null) MessageDigest.getInstance("SHA-256") else null
        var bytesRead = 0L

        apkFile.outputStream().use { output ->
            body.byteStream().use { input ->
                val buffer = ByteArray(8 * 1024)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    digest?.update(buffer, 0, read)
                    bytesRead += read
                    if (contentLength > 0) {
                        downloadProgress.value = bytesRead.toFloat() / contentLength.toFloat()
                    }
                }
            }
        }

        if (digest != null && release.hash != null) {
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            val expectedHash = release.hash.removePrefix("sha256:")
            if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                AmberLog.e(Amber.TAG, "ZapstoreUpdater: hash mismatch! expected=$expectedHash actual=$actualHash")
                apkFile.delete()
                return null
            }
            AmberLog.d(Amber.TAG, "ZapstoreUpdater: hash verified OK")
        }

        return apkFile
    }

    private fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        scope.launch(Dispatchers.Main) {
            delay(2000)
            downloadState.value = DownloadState.IDLE
        }
    }

    private fun isNewerVersion(version: String): Boolean {
        val current = BuildConfig.VERSION_NAME.substringBefore("-")
        if (version == current) return false
        return try {
            val newParts = version.substringBefore("-").split(".").map { it.toInt() }
            val curParts = current.split(".").map { it.toInt() }
            for (i in 0 until maxOf(newParts.size, curParts.size)) {
                val n = newParts.getOrElse(i) { 0 }
                val c = curParts.getOrElse(i) { 0 }
                if (n > c) return true
                if (n < c) return false
            }
            false
        } catch (_: Exception) {
            version > current
        }
    }
}

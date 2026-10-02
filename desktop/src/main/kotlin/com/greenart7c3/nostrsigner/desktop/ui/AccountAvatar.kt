package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.greenart7c3.nostrsigner.desktop.core.AccountsStore
import com.greenart7c3.nostrsigner.desktop.core.AmberLogger
import com.greenart7c3.nostrsigner.desktop.core.AppDirs
import com.greenart7c3.nostrsigner.desktop.core.ProfileFetcher
import com.greenart7c3.nostrsigner.desktop.core.RelayHttpClients
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jetbrains.skia.Image as SkiaImage

/**
 * Round profile picture of an account (from its kind-0 metadata), falling
 * back to the first letter of its name — the desktop take on the Android
 * `ProfilePictureIcon`. Showing it also kicks off the throttled profile
 * fetch, like Android's `ProfileSubscriptionEffect`.
 */
@Composable
fun AccountAvatar(
    npub: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val accounts by AccountsStore.accounts.collectAsState()
    val record = accounts.firstOrNull { it.npub == npub }
    val name = record?.name.orEmpty()
    val picture = record?.picture.orEmpty()

    LaunchedEffect(npub) { ProfileFetcher.refresh(npub) }

    var bitmap by remember(picture) { mutableStateOf(ProfileImages.cached(picture)) }
    LaunchedEffect(picture) {
        if (picture.isNotBlank() && bitmap == null) bitmap = ProfileImages.load(picture)
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.size(size),
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    name.ifBlank { npub.removePrefix("npub1") }.take(1).uppercase(),
                    style = if (size >= 32.dp) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Downloads and caches profile pictures (memory + an on-disk cache), using
 * the same Tor-aware HTTP routing as the relay sockets.
 */
object ProfileImages {
    private const val MAX_BYTES = 5L * 1024 * 1024

    private val memory = ConcurrentHashMap<String, ImageBitmap>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val dir: File by lazy { File(AppDirs.dataDir, "avatars").apply { mkdirs() } }

    fun cached(url: String): ImageBitmap? = memory[url]

    suspend fun load(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        memory[url]?.let { return@withContext it }
        if (url in failed) return@withContext null
        if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) return@withContext null

        val file = File(dir, sha256(url))
        val bytes = runCatching { if (file.isFile) file.readBytes() else null }.getOrNull()
            ?: download(url)?.also { runCatching { file.writeBytes(it) } }
        val image = bytes?.let { runCatching { SkiaImage.makeFromEncoded(it).toComposeImageBitmap() }.getOrNull() }
        if (image == null) {
            failed.add(url)
            runCatching { file.delete() }
        } else {
            memory[url] = image
        }
        image
    }

    private fun download(url: String): ByteArray? = try {
        RelayHttpClients.clientFor(url).newCall(Request.Builder().url(url).build()).execute().use { response ->
            val body = response.body
            if (!response.isSuccessful || body.contentLength() > MAX_BYTES) {
                null
            } else {
                body.byteStream().use { input ->
                    val bytes = input.readNBytes((MAX_BYTES + 1).toInt())
                    if (bytes.size > MAX_BYTES) null else bytes
                }
            }
        }
    } catch (e: Exception) {
        AmberLogger.d("ProfileImages", "Could not download profile picture: ${e.message}")
        null
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
}

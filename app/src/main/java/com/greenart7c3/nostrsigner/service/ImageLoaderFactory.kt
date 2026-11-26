package com.greenart7c3.nostrsigner.service

import android.content.Context
import coil3.EventListener
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.crossfade
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.models.TorMode
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

private const val MEMORY_CACHE_BYTES_AVOIDING_DMA_BUF_OOM = 32L * 1024 * 1024
private const val DISK_CACHE_BYTES = 10L * 1024 * 1024

/**
 * Coil's singleton loader: capped caches, the relays' Tor routing, and a log line per load.
 * [isSettingsLoaded] is false until the stored settings are loaded; until then every load
 * goes through the proxy.
 */
class ImageLoaderFactory(
    private val settingsStore: SettingsStore,
    private val isSettingsLoaded: () -> Boolean,
    private val scope: CoroutineScope,
) : SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val memCache = MemoryCache.Builder()
            .maxSizeBytes(MEMORY_CACHE_BYTES_AVOIDING_DMA_BUF_OOM)
            .build()
        val diskCache = DiskCache.Builder()
            .directory(context.cacheDir.resolve("coil_image_cache").toOkioPath())
            .maxSizeBytes(DISK_CACHE_BYTES)
            .build()
        val coilCallFactory = okhttp3.Call.Factory { request ->
            val url = request.url.toString()
            if (PrivateAddress.isPrivate(url)) return@Factory HttpClientManager.getLocalHttpClient().newCall(request)
            val useProxy = when {
                !isSettingsLoaded() -> true
                else -> settingsStore.settings.torMode != TorMode.DISABLED
            }
            HttpClientManager.getHttpClient(useProxy).newCall(request)
        }
        return ImageLoader.Builder(context)
            .memoryCache { memCache }
            .diskCache { diskCache }
            .crossfade(true)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { coilCallFactory })) }
            .eventListener(
                object : EventListener() {
                    override fun onStart(request: ImageRequest) {
                        AmberLog.d(Amber.TAG, "Coil: loading ${request.data}")
                    }

                    override fun onSuccess(request: ImageRequest, result: SuccessResult) {
                        scope.launch {
                            AmberLog.d(
                                Amber.TAG,
                                "Coil: loaded ${request.data} from ${result.dataSource}" +
                                    " | mem=${memCache.size / 1024}/${memCache.maxSize / 1024} KB" +
                                    " | disk=${diskCache.size / 1024}/${diskCache.maxSize / 1024} KB",
                            )
                        }
                    }

                    override fun onError(request: ImageRequest, result: ErrorResult) {
                        AmberLog.w(Amber.TAG, "Coil: error loading ${request.data}", result.throwable)
                    }

                    override fun onCancel(request: ImageRequest) {
                        AmberLog.d(Amber.TAG, "Coil: cancelled ${request.data}")
                    }
                },
            )
            .build()
    }

    /**
     * Clears Coil's bitmap memory cache. Coil 3 registers no ComponentCallbacks of its own,
     * so the cache would otherwise stay fully resident in this long-lived signer process even
     * while its UI is not visible. It rebuilds lazily on the next image load.
     */
    fun trimMemoryCache(context: Context) {
        scope.launch {
            runCatching { SingletonImageLoader.get(context).memoryCache?.clear() }
                .onFailure { AmberLog.w(Amber.TAG, "Failed to trim Coil memory cache", it) }
        }
    }
}

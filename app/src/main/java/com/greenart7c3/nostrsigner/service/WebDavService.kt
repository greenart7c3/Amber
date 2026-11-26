package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.data.RelayClient
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.models.TorMode
import com.greenart7c3.nostrsigner.okhttp.DefaultContentTypeInterceptor
import com.greenart7c3.nostrsigner.okhttp.HttpClientManager
import com.greenart7c3.nostrsigner.okhttp.TaggedSocketFactory
import com.greenart7c3.nostrsigner.okhttp.TaggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

@Suppress("NoComments")
internal fun webDavUsesProxy(isPrivateAddress: Boolean, torMode: TorMode): Boolean {
    // A Tor exit cannot reach the user's own NAS, so private/LAN addresses connect directly,
    // as the relay factory and the image loader do.
    return !isPrivateAddress && torMode != TorMode.DISABLED
}

private val MEDIA_TYPE = "application/octet-stream".toMediaType()

@Suppress("UNNECESSARY_SAFE_CALL", "NoComments")
private fun Response.bodyStringOrEmpty(): String {
    // Safe call needed: the offline flavor compiles against OkHttp 4, where body is nullable
    return body?.string() ?: ""
}

private const val TOR_TIMEOUT_MULTIPLIER = 3
private const val METHOD_NOT_ALLOWED_BUT_REACHABLE = 405

class WebDavService(
    private val settingsStore: SettingsStore,
    private val relayClient: RelayClient,
) {
    @Suppress("NoComments")
    internal fun createClient(serverUrl: String): OkHttpClient {
        // Single entry point: the HTTPS policy runs before any client exists,
        // so a new WebDAV method cannot skip it and send Basic auth cleartext.
        WebDavTransportPolicy.requireSecureServerUrl(serverUrl)
        val useProxy = webDavUsesProxy(
            isPrivateAddress = PrivateAddress.isPrivate(serverUrl),
            torMode = settingsStore.settings.torMode,
        )
        val builder = clientBuilderWithoutRelayLoggingOrDecryptCache()
        if (useProxy) {
            routeThroughTor(builder)
        } else {
            useSlowNasTimeouts(builder)
        }
        followSameSchemeRedirectsOnly(builder)
        return builder.build()
    }

    private fun clientBuilderWithoutRelayLoggingOrDecryptCache(): OkHttpClient.Builder = OkHttpClient.Builder()
        .socketFactory(TaggedSocketFactory(SocketFactory.getDefault()))
        .addInterceptor(TaggingInterceptor())
        .addInterceptor(DefaultContentTypeInterceptor("Amber/${BuildConfig.VERSION_NAME}"))

    @Suppress("NoComments")
    private fun routeThroughTor(builder: OkHttpClient.Builder) {
        // A SOCKS proxy that is still bootstrapping would fail later with a
        // generic connect error; fail here with the real condition.
        check(relayClient.isSocksProxyAlive("127.0.0.1", settingsStore.settings.proxyPort)) {
            "Tor is enabled but its SOCKS proxy is not reachable yet"
        }
        builder.proxy(checkNotNull(HttpClientManager.getCurrentProxy()) { "Tor proxy is not configured yet" })
        // Building a Tor circuit plus the TLS handshake needs the larger budget
        // HttpClientManager.buildHttpClient also gives proxied routes.
        val seconds = HttpClientManager.getDefaultTimeout().seconds * TOR_TIMEOUT_MULTIPLIER
        builder.connectTimeout(seconds, TimeUnit.SECONDS)
            .readTimeout(seconds, TimeUnit.SECONDS)
            .writeTimeout(seconds, TimeUnit.SECONDS)
    }

    private fun useSlowNasTimeouts(builder: OkHttpClient.Builder) {
        builder.connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
    }

    @Suppress("NoComments")
    private fun followSameSchemeRedirectsOnly(builder: OkHttpClient.Builder) {
        // Same-origin redirects are ordinary WebDAV behavior (collection URLs without a
        // trailing slash 301 to slash). OkHttp strips the Authorization header when a
        // redirect crosses to another host, so credentials never follow. Scheme changes are
        // refused: the server URL is always HTTPS, so one would be a downgrade to HTTP, and a
        // 307/308 would resend the backup body in cleartext. The 3xx is returned as an error.
        builder.followRedirects(true)
            .followSslRedirects(false)
    }

    fun uploadFile(
        serverUrl: String,
        username: String,
        password: String,
        fileName: String,
        content: String,
    ): Result<Unit> = try {
        val client = createClient(serverUrl)
        val url = WebDavTransportPolicy.buildFileUrl(serverUrl, fileName)
        val credential = Credentials.basic(username, password)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .put(content.toByteArray(Charsets.UTF_8).toRequestBody(MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful || response.code == 201 || response.code == 204) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("Server returned ${response.code}: ${response.message}"))
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun downloadLatestFile(
        serverUrl: String,
        username: String,
        password: String,
        fileName: String,
    ): Result<String> = try {
        val client = createClient(serverUrl)
        val url = WebDavTransportPolicy.buildFileUrl(serverUrl, fileName)
        val credential = Credentials.basic(username, password)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                Result.success(response.bodyStringOrEmpty())
            } else {
                Result.failure(IOException("Server returned ${response.code}: ${response.message}"))
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun testConnection(
        serverUrl: String,
        username: String,
        password: String,
    ): Result<Unit> = try {
        val client = createClient(serverUrl)
        val url = serverUrl.trimEnd('/')
        val credential = Credentials.basic(username, password)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .header("Depth", "0")
            .method("PROPFIND", ByteArray(0).toRequestBody(null))
            .build()

        client.newCall(request).execute().use { response ->
            when {
                response.code == 207 || response.isSuccessful -> Result.success(Unit)
                response.code == 401 -> Result.failure(IOException("Authentication failed: wrong username or password"))
                response.code == METHOD_NOT_ALLOWED_BUT_REACHABLE -> Result.success(Unit)
                else -> Result.failure(IOException("Server returned ${response.code}: ${response.message}"))
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

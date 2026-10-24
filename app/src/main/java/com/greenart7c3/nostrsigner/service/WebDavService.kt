package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.models.TorMode
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

/**
 * Whether WebDAV traffic is routed through the Tor SOCKS proxy: only when the
 * user enabled Tor and the target is not a private/LAN address — a Tor exit
 * cannot reach the user's own NAS, so private addresses connect directly.
 * Mirrors the routing rule used by the relay factory and the image loader.
 */
internal fun webDavUsesProxy(isPrivateAddress: Boolean, torMode: TorMode): Boolean = !isPrivateAddress && torMode != TorMode.DISABLED

object WebDavService {
    private val MEDIA_TYPE = "application/octet-stream".toMediaType()

    internal fun createClient(serverUrl: String): OkHttpClient {
        // Single entry point: the HTTPS policy runs before any client exists,
        // so a new WebDAV method cannot skip it and send Basic auth cleartext.
        WebDavTransportPolicy.requireSecureServerUrl(serverUrl)
        val useProxy = webDavUsesProxy(
            isPrivateAddress = Amber.instance.isPrivateIp(serverUrl),
            torMode = Amber.instance.settings.torMode,
        )
        // Dedicated minimal client: the shared relay stack brings a relay
        // User-Agent interceptor, request logging, and an URL-keyed decrypt
        // cache that must not apply to backup traffic.
        val builder = OkHttpClient.Builder()
            .socketFactory(TaggedSocketFactory(SocketFactory.getDefault()))
            .addInterceptor(TaggingInterceptor())
        if (useProxy) {
            // A SOCKS proxy that is still bootstrapping would fail later with a
            // generic connect error; fail here with the real condition.
            check(Amber.instance.isSocksProxyAlive("127.0.0.1", Amber.instance.settings.proxyPort)) {
                "Tor is enabled but its SOCKS proxy is not reachable yet"
            }
            builder.proxy(checkNotNull(HttpClientManager.getCurrentProxy()) { "Tor proxy is not configured yet" })
            // Timeout scaling matches HttpClientManager.buildHttpClient
            // (proxied routes get 3x): building a Tor circuit plus the TLS
            // handshake needs the larger budget.
            val timeout = HttpClientManager.getDefaultTimeout()
            val seconds = timeout.seconds * 3
            builder.connectTimeout(seconds, TimeUnit.SECONDS)
                .readTimeout(seconds, TimeUnit.SECONDS)
                .writeTimeout(seconds, TimeUnit.SECONDS)
        } else {
            // Direct: keep the long budgets this client always had — large
            // backups to a slow NAS must not time out.
            builder.connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
        }
        // Same-origin redirects are ordinary WebDAV behavior (collection
        // URLs without a trailing slash 301 to slash). OkHttp strips the
        // Authorization header when a redirect crosses to another host, so
        // credentials never follow. Scheme changes are refused: the server URL
        // is always HTTPS, so one would be a downgrade to HTTP, and a 307/308
        // would resend the backup body in cleartext. The 3xx is returned as an
        // error instead.
        builder.followRedirects(true)
            .followSslRedirects(false)
        return builder.build()
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
                // Safe call needed: the offline flavor compiles against OkHttp 4, where body is nullable
                val body = response.body?.string() ?: ""
                Result.success(body)
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

        // PROPFIND with Depth: 0 is the standard WebDAV handshake
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
                response.code == 405 -> Result.success(Unit) // server doesn't support PROPFIND but is reachable
                else -> Result.failure(IOException("Server returned ${response.code}: ${response.message}"))
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

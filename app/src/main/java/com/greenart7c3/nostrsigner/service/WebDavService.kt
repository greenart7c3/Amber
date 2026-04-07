package com.greenart7c3.nostrsigner.service

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object WebDavService {
    private val MEDIA_TYPE = "application/octet-stream".toMediaType()

    private fun createClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun uploadFile(
        serverUrl: String,
        username: String,
        password: String,
        fileName: String,
        content: String,
    ): Result<Unit> = try {
        val client = createClient()
        val url = serverUrl.trimEnd('/') + "/" + fileName
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
        val client = createClient()
        val url = serverUrl.trimEnd('/') + "/" + fileName
        val credential = Credentials.basic(username, password)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
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
        val client = createClient()
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

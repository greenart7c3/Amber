package com.greenart7c3.nostrsigner.service

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object WebDavTransportPolicy {
    /**
     * Parses with OkHttp's HttpUrl — the same parser the requests use — so URLs
     * OkHttp accepts keep working: trailing whitespace, spaces in the path,
     * underscore LAN hosts, IDN names, uppercase schemes. The previous
     * java.net.URI check rejected several of those as "requires an HTTPS server
     * URL" even though the requests would have been fine.
     */
    fun requireSecureServerUrl(serverUrl: String) {
        val url = serverUrl.trim().toHttpUrlOrNull()
        require(url != null && url.host.isNotBlank()) { "WebDAV server URL could not be parsed" }
        require(url.isHttps) { "WebDAV requires an HTTPS server URL" }
        require(url.username.isEmpty() && url.password.isEmpty()) {
            "WebDAV server URLs must not contain credentials — use the username and password fields"
        }
        require(url.query == null && url.fragment == null) {
            "WebDAV server URLs must not contain a query or a fragment"
        }
    }

    /**
     * Builds the request URL for [fileName] relative to [serverUrl]. The name is
     * a single encoded path segment (never a directory escape), and the final
     * URL is checked against the same policy, so a hand-typed name cannot
     * inject a query, fragment or unexpected path past the HTTPS/credential
     * rules.
     */
    fun buildFileUrl(serverUrl: String, fileName: String): HttpUrl {
        require(
            fileName.isNotBlank() &&
                fileName != "." &&
                fileName != ".." &&
                !fileName.contains('/') &&
                !fileName.contains('\\'),
        ) {
            "Invalid WebDAV file name"
        }
        val base = serverUrl.trim().toHttpUrlOrNull()
            ?: throw IllegalArgumentException("WebDAV server URL could not be parsed")
        val url = base.newBuilder().addPathSegment(fileName).build()
        requireSecureServerUrl(url.toString())
        return url
    }
}

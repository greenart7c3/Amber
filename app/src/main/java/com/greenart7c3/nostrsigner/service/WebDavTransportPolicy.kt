package com.greenart7c3.nostrsigner.service

import java.net.URI

object WebDavTransportPolicy {
    fun requireSecureServerUrl(serverUrl: String) {
        val uri = URI(serverUrl)
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
            "WebDAV requires an HTTPS server URL"
        }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "WebDAV server URLs must not contain credentials, a query, or a fragment"
        }
    }
}

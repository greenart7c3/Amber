package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Browser
import androidx.core.net.toUri
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Hex
import java.net.URLDecoder

// How a nostrsigner: request is read: its data, the keys in it, and whether it came from the web.

// Compiled once at class load time; avoids recompiling the pattern on every isUrlEncoded() call.
private val URL_ENCODED_REGEX = Regex("%[0-9a-fA-F]{2}")

fun decodeData(data: String, replace: Boolean = true, decodeData: Boolean = true): String {
    val stripped = data.replace("nostrsigner:", "")
    // replace keeps a literal '+' instead of letting URLDecoder turn it into a space.
    val raw = if (replace) stripped.replace("+", "%2b") else stripped
    return if (decodeData) URLDecoder.decode(raw, "utf-8") else raw
}

fun isUrlEncoded(input: String): Boolean = URL_ENCODED_REGEX.containsMatchIn(input)

fun parsePubKey(key: String): String? {
    if (key.startsWith("npub1")) {
        return key
    }
    return try {
        Hex.decode(key).toNpub()
    } catch (_: Exception) {
        null
    }
}

/**
 * Returns true when the launching app is a web browser. [referrer] is the value of
 * `Activity.getReferrer()`: an `http(s)` referrer is the web page itself, an
 * `android-app://<package>` referrer is the launching app.
 */
fun isBrowserReferrer(context: Context, referrer: Uri?): Boolean = when (referrer?.scheme) {
    "http", "https" -> true
    "android-app" -> isBrowserPackage(context, referrer.host)
    else -> false
}

private fun isBrowserPackage(context: Context, packageName: String?): Boolean {
    if (packageName.isNullOrBlank()) return false
    val browserIntent = Intent(Intent.ACTION_VIEW, "https://example.com".toUri())
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .setPackage(packageName)
    return try {
        context.packageManager.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL).isNotEmpty()
    } catch (_: Exception) {
        false
    }
}

/**
 * Decides whether a `nostrsigner:` request carries its parameters in the URI query
 * (web apps) instead of intent extras (native NIP-55 apps). Browsers don't agree on
 * how they mark external intents: Chrome adds [Browser.EXTRA_APPLICATION_ID], others
 * (e.g. DuckDuckGo) only add [Intent.CATEGORY_BROWSABLE] or nothing at all. A `type`
 * extra means the caller used the native extras API, so only then do we ignore the
 * weaker signals (BROWSABLE category, browser referrer, `type=` query parameter).
 */
internal fun isWebRequest(
    hasApplicationIdExtra: Boolean,
    hasBrowsableCategory: Boolean,
    referrerIsBrowser: Boolean,
    typeExtra: String?,
    dataString: String?,
): Boolean {
    if (hasApplicationIdExtra) return true
    if (!typeExtra.isNullOrBlank()) return false
    return hasBrowsableCategory || referrerIsBrowser || hasTypeQueryParameter(dataString)
}

private fun hasTypeQueryParameter(dataString: String?): Boolean {
    if (dataString.isNullOrBlank()) return false
    val decoded = try {
        URLDecoder.decode(dataString.replace("+", "%2b"), "utf-8")
    } catch (_: Exception) {
        dataString
    }
    return listOf(dataString, decoded).any { data ->
        data.substringAfter('?', "")
            .split('?', '&')
            .any { it.startsWith("type=") }
    }
}

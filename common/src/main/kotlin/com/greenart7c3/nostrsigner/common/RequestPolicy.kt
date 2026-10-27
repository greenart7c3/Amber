package com.greenart7c3.nostrsigner.common

import java.net.URLDecoder

/**
 * Whether a request reached a per-connection key ([routedPrivKey]; empty when
 * it was addressed to the account key) that is not the sending app's own
 * ([appLocalKey]). A connect is never treated so: it is how a client attaches
 * to a new connection key.
 */
fun connectsThroughOtherKey(appLocalKey: String, routedPrivKey: String, type: SignerType): Boolean = routedPrivKey.isNotEmpty() && type != SignerType.CONNECT && appLocalKey != routedPrivKey

/**
 * NIP-42 names exactly one relay per auth event: authorization, the auth
 * whitelist and the consent screen all decide on the FIRST relay tag, so a
 * second relay tag would be signed without ever being granted — the signed
 * event would also vouch for a relay the user never approved.
 */
fun hasAmbiguousRelayTags(kind: Int, tags: Array<Array<String>>): Boolean = kind == 22242 && tags.count { it.size > 1 && it[0] == "relay" } > 1

/** Longest client-chosen app name Amber shows, in characters. */
const val APP_NAME_MAX = 64

/**
 * The client-chosen [name] made safe to show: control, format (bidi
 * overrides, zero-width) and separator characters are dropped, whitespace
 * runs and line breaks become single spaces, and it is cut to [max]
 * characters, so a client cannot fake extra lines or Amber's own text on an
 * approval sheet, notification or popup.
 */
fun sanitizeAppName(name: String, max: Int = APP_NAME_MAX): String {
    val cleaned = buildString {
        name.codePoints().forEach { cp ->
            when (Character.getType(cp).toByte()) {
                Character.CONTROL, Character.FORMAT, Character.PRIVATE_USE, Character.UNASSIGNED, Character.SURROGATE -> {
                    if (Character.isWhitespace(cp)) append(' ')
                }
                Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> append(' ')
                else -> appendCodePoint(cp)
            }
        }
    }.replace(Regex("\\s+"), " ").trim()
    if (cleaned.codePointCount(0, cleaned.length) <= max) return cleaned
    return cleaned.substring(0, cleaned.offsetByCodePoints(0, max - 1)).trimEnd() + "…"
}

/**
 * A single query-string value form-decoded exactly once: '+' means a space
 * (application/x-www-form-urlencoded, how clients like URLSearchParams encode
 * a name) and a literal '+' arrives as '%2B', so "My+App" shows its space
 * while "C%2B%2B" keeps its pluses. A stray '%' that is not an escape leaves
 * the value in its raw form. Decode the raw query per value, never as a whole:
 * an encoded '&' (%26) inside a value must not split the param in two.
 */
fun decodeQueryParamValue(value: String): String = try {
    URLDecoder.decode(value, "utf-8")
} catch (_: Exception) {
    value
}

package com.greenart7c3.nostrsigner.common

import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

/** A permission a client asks for in its connect URI: a type and, for some, a kind or NIP number. */
data class RequestedPermission(val type: String, val kind: Int?)

/**
 * What a `nostrconnect://` URI asks for, the same on Android and desktop. The URI is
 * untrusted input: [clientPubKey] becomes the connection's identity and [secret] must go
 * back to the client byte for byte.
 */
data class NostrConnectUri(
    val clientPubKey: String,
    val relays: List<NormalizedRelayUrl>,
    // Client-chosen, sanitized for display.
    val name: String,
    val url: String,
    val image: String,
    val secret: String,
    val permissions: List<RequestedPermission>,
) {
    companion object {
        private val HEX_PUBKEY = Regex("[0-9a-f]{64}")

        /**
         * Parses [uri]. [isKnownNip] tells whether a number is a NIP Amber knows: a `nip:N`
         * permission for any other number is dropped, as is a `sign_event` or `nip`
         * permission without one. Throws [IllegalArgumentException] only for a client key
         * that is not a lowercase hex public key; anything else malformed is skipped.
         */
        fun parse(uri: String, isKnownNip: (Int) -> Boolean): NostrConnectUri {
            val data = uri.trim().removePrefix("nostrconnect://")
            val clientPubKey = data.substringBefore("?")
            require(clientPubKey.matches(HEX_PUBKEY)) { "Invalid public key in URI" }

            val query = readQuery(data.substringAfter("?", ""))
            // The metadata JSON only overrides what it sets; malformed metadata is ignored.
            query.metadata?.let { applyMetadata(it, query) }
            return NostrConnectUri(
                clientPubKey = clientPubKey,
                relays = query.relays,
                name = sanitizeAppName(query.fields["name"].orEmpty()),
                url = query.fields["url"].orEmpty(),
                image = query.fields["image"].orEmpty(),
                secret = query.fields["secret"].orEmpty(),
                permissions = query.permissions.filter { it.grantsSomething(isKnownNip) },
            )
        }

        private class Query {
            val relays = mutableListOf<NormalizedRelayUrl>()
            val fields = mutableMapOf<String, String>()
            val permissions = mutableListOf<RequestedPermission>()
            var metadata: String? = null
        }

        // Split the RAW query and form-decode each value exactly once: a client that
        // encodes spaces as '+' then shows "My App", while a literal '+' arrives as
        // '%2B'. A value is split from its name on the first '=' only, so base64
        // padding in a secret ("…yg==") survives.
        private fun readQuery(rawQuery: String): Query = Query().apply {
            rawQuery.split("&").forEach { param ->
                val value = decodeQueryParamValue(param.substringAfter("=", ""))
                when (val name = param.substringBefore("=")) {
                    "relay" -> RelayUrlNormalizer.normalizeOrNull(value)?.let { relays.add(it) }
                    "name", "url", "image", "secret" -> fields[name] = value
                    "perms" -> permissions.addAll(parsePermissionEntries(value))
                    "metadata" -> metadata = value
                }
            }
        }

        private fun applyMetadata(json: String, query: Query) {
            val node = runCatching { JacksonMapper.mapper.readTree(json) }.getOrNull() ?: return
            listOf("name", "url", "image").forEach { key ->
                node.get(key)?.asText()?.trim()?.takeIf { it.isNotEmpty() }?.let { query.fields[key] = it }
            }
            node.get("perms")?.asText()?.let { query.permissions.addAll(parsePermissionEntries(it)) }
        }
    }
}

/**
 * Parses a list of requested permissions ("type:kind,type,…"), as a nostrconnect:// URI's
 * `perms` and a bunker `connect`'s third parameter carry it. Drops what grants nothing: an
 * empty entry, a `sign_event` or `nip` without a kind, a `nip` Amber does not know
 * ([isKnownNip]), and an entry whose kind is not a number (read as kind-less it would ask for
 * every kind of a NIP-44 v3 type).
 */
fun parseRequestedPermissions(value: String, isKnownNip: (Int) -> Boolean): List<RequestedPermission> = parsePermissionEntries(value).filter { it.grantsSomething(isKnownNip) }

// "type:kind,type,…"; an empty entry and one with a non-numeric kind are skipped.
private fun parsePermissionEntries(value: String): List<RequestedPermission> = value.split(",").mapNotNull { entry ->
    val type = entry.substringBefore(":").trim()
    val kindText = entry.substringAfter(":", "").trim()
    val kind = kindText.toIntOrNull()
    if (type.isEmpty() || (kindText.isNotEmpty() && kind == null)) null else RequestedPermission(type, kind)
}

// A sign_event or nip permission needs a kind, and a nip one a NIP Amber knows.
private fun RequestedPermission.grantsSomething(isKnownNip: (Int) -> Boolean): Boolean = when (type) {
    "sign_event" -> kind != null
    "nip" -> kind?.let(isKnownNip) == true
    else -> true
}

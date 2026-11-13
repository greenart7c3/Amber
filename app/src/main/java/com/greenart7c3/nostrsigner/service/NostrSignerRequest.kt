package com.greenart7c3.nostrsigner.service

import com.fasterxml.jackson.module.kotlin.readValue
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.decodeQueryParamValue
import com.greenart7c3.nostrsigner.common.sanitizeAppName
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.containsNip
import com.greenart7c3.nostrsigner.models.CompressionType
import com.greenart7c3.nostrsigner.models.ReturnType

/**
 * A `nostrsigner:` request as the caller sent it, before any key is used:
 * parsed by [NostrSignerRequestParser], checked by [rejection] and turned into
 * an `IntentData` by `IntentUtils`.
 */
data class NostrSignerRequest(
    val type: SignerType,
    /** The raw `type` value, for the error shown when it is unknown. */
    val rawType: String?,
    val data: String,
    val pubKey: String,
    val callbackUrl: String?,
    val appName: String,
    val compression: CompressionType,
    val returnType: ReturnType,
    /** Requested permissions (extras only; the URI form has none). */
    val permissions: List<Permission>?,
    /** The `current_user` extra as an npub, or null when absent or unparsable. */
    val currentUser: String?,
    val nip44v3Kind: Int?,
    val nip44v3Scope: String,
    /** True when the parameters came in the URI query (web apps), false for intent extras. */
    val fromUri: Boolean,
) {
    /** Why this request can never be served, or null when it can. */
    fun rejection(): String? = when {
        type == SignerType.INVALID -> "Unknown signer type: $rawType"
        fromUri && type == SignerType.DECRYPT_ZAP_EVENT -> "decrypt_zap_event is not supported in a URL request"
        else -> null
    }
}

object NostrSignerRequestParser {
    fun parseSignerType(type: String?): SignerType = when (type) {
        "sign_event" -> SignerType.SIGN_EVENT
        "get_public_key" -> SignerType.GET_PUBLIC_KEY
        "nip04_encrypt" -> SignerType.NIP04_ENCRYPT
        "nip04_decrypt" -> SignerType.NIP04_DECRYPT
        "nip44_encrypt" -> SignerType.NIP44_ENCRYPT
        "nip44_decrypt" -> SignerType.NIP44_DECRYPT
        "nip44v3_encrypt" -> SignerType.NIP44_V3_ENCRYPT
        "nip44v3_decrypt" -> SignerType.NIP44_V3_DECRYPT
        "decrypt_zap_event" -> SignerType.DECRYPT_ZAP_EVENT
        "sign_psbt" -> SignerType.SIGN_PSBT
        else -> SignerType.INVALID
    }

    /**
     * Parses a web app's request, whose parameters are in the URI query
     * (`nostrsigner:<data>?type=…&pubkey=…`). Returns null when the URI carries
     * no parameters, in which case the caller reads the intent extras instead.
     */
    fun fromUri(uri: String): NostrSignerRequest? {
        val stripped = uri.replace("nostrsigner:", "")
        // NIP-04 ciphertexts carry their own "?iv=", which is part of the data.
        val content = if (uri.contains("?iv")) stripped.split("?").let { it[0] + "?" + it[1] } else ""
        val data = content.ifEmpty { IntentUtils.decodeData(uri).split("?").first() }
        val parameters = if (content.isEmpty()) {
            // Split the RAW query and decode each value once (see
            // decodeQueryParamValue): a form-encoded appName "My+App" shows its
            // space, and an encoded '&' (%26) inside a value no longer ends up
            // splitting the param in two.
            stripped.split("?").drop(1)
        } else {
            IntentUtils.decodeData(content, false).split("?").drop(1)
        }.filter { it.isNotBlank() }
        if (parameters.isEmpty()) return null

        val values = mutableMapOf<String, String>()
        parameters.flatMap { it.split("&") }.forEach {
            val name = it.substringBefore("=")
            // pubkey is matched in any case; the other names are case-sensitive.
            val key = if (name.lowercase() == "pubkey") "pubkey" else name
            values[key] = decodeQueryParamValue(it.substringAfter("=", ""))
        }
        return NostrSignerRequest(
            type = parseSignerType(values["type"]),
            rawType = values["type"],
            data = data,
            pubKey = values["pubkey"] ?: "",
            callbackUrl = values["callbackUrl"],
            appName = sanitizeAppName(values["appName"] ?: ""),
            compression = if (values["compressionType"] == "gzip") CompressionType.GZIP else CompressionType.NONE,
            returnType = if (values["returnType"] == "event") ReturnType.EVENT else ReturnType.SIGNATURE,
            permissions = emptyList(),
            currentUser = null,
            nip44v3Kind = values["kind"]?.toIntOrNull(),
            nip44v3Scope = values["scope"] ?: "",
            fromUri = true,
        )
    }

    /**
     * Parses a native NIP-55 app's request: the data is the intent URI and the
     * parameters are the intent extras, read through [extra]. [packageName] is
     * the calling app, null for an unknown caller.
     */
    fun fromExtras(uri: String, packageName: String?, extra: (String) -> String?): NostrSignerRequest {
        val data = try {
            IntentUtils.decodeData(uri, packageName == null, IntentUtils.isUrlEncoded(uri))
        } catch (_: Exception) {
            uri.replace("nostrsigner:", "")
        }
        val type = extra("type")
        return NostrSignerRequest(
            type = parseSignerType(type),
            rawType = type,
            data = data,
            pubKey = extra("pubKey") ?: extra("pubkey") ?: "",
            callbackUrl = extra("callbackUrl"),
            appName = sanitizeAppName(extra("appName") ?: ""),
            compression = if (extra("compression") == "gzip") CompressionType.GZIP else CompressionType.NONE,
            returnType = if (extra("returnType") == "event") ReturnType.EVENT else ReturnType.SIGNATURE,
            permissions = extra("permissions")?.let(::parsePermissions),
            currentUser = extra("current_user")?.let(IntentUtils::parsePubKey),
            nip44v3Kind = extra("kind")?.toIntOrNull(),
            nip44v3Scope = extra("scope") ?: "",
            fromUri = false,
        )
    }

    /** The requested permissions, without kind-less `sign_event`/`nip` entries or unknown NIPs; null if [json] is invalid. */
    fun parsePermissions(json: String): List<Permission>? {
        val permissions = try {
            Permission.mapper.readValue<List<Permission>>(json)
        } catch (e: Exception) {
            AmberLog.e("IntentUtils", "Error parsing permissions: $json", e)
            return null
        }
        return permissions
            .filterNot { it.kind == null && (it.type == "sign_event" || it.type == "nip") }
            .filterNot { it.type == "nip" && it.kind?.containsNip() != true }
            .map { Permission(it.type.trim(), it.kind, true) }
    }
}

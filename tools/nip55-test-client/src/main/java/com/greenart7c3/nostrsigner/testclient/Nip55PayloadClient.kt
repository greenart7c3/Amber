package com.greenart7c3.nostrsigner.testclient

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.util.Base64
import java.io.FileNotFoundException
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

object Nip55PayloadClient {
    const val PAYLOADS_SUFFIX = "PAYLOADS"
    const val EXTRA_PAYLOAD_URI = "payload_uri"
    const val EXTRA_ACCEPT_RESULT_URI = "accept_result_uri"
    const val EXTRA_RESULT_URI = "result_uri"
    const val EXTRA_EVENT_URI = "event_uri"

    const val NIP44_V3_KIND = 30078
    const val NIP44_V3_SCOPE = "nip55-test-client"

    const val REQUESTED_PERMISSIONS_JSON =
        """[{"type":"sign_event","kind":1},{"type":"nip44v3_encrypt"},{"type":"nip44v3_decrypt"},{"type":"nip44_encrypt"},{"type":"nip44_decrypt"}]"""

    private const val PREFERRED_SIGNER = "com.greenart7c3.nostrsigner.debug"

    data class SignEventResolverResult(
        val signatureHex: String,
        val resultValue: String,
        val columns: List<String>,
        val eventTransport: String,
        val eventJsonBytes: Int?,
        val verifiedEventId: String?,
        val verifiedContentLength: Int?,
        val singleUseDeleted: Boolean?,
        val elapsedMs: Long,
    )

    data class Nip44v3RoundTripResult(
        val rawPlaintextBytes: Int,
        val base64PlaintextBytes: Int,
        val ciphertextBytes: Int,
        val encryptTransport: String,
        val decryptTransport: String,
        val encryptSingleUseDeleted: Boolean?,
        val decryptSingleUseDeleted: Boolean?,
        val byteForByteMatch: Boolean,
        val elapsedMs: Long,
    )

    fun discoverSigners(context: Context): List<String> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:"))
        val pm = context.packageManager
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return resolved
            .map { it.activityInfo.packageName }
            .distinct()
            .sortedWith(
                compareBy<String>(
                    { it != PREFERRED_SIGNER },
                    { !it.startsWith("com.greenart7c3.nostrsigner") },
                    { it },
                ),
            )
    }

    fun supportsLargePayloads(context: Context, signerPackage: String): Boolean {
        val authority = "$signerPackage.$PAYLOADS_SUFFIX"
        val pm = context.packageManager
        val providerInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveContentProvider(authority, PackageManager.ComponentInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.resolveContentProvider(authority, 0)
        }
        return providerInfo != null
    }

    fun stagePayload(
        resolver: ContentResolver,
        signerPackage: String,
        bytes: ByteArray,
    ): Uri {
        val collectionUri = Uri.parse("content://$signerPackage.$PAYLOADS_SUFFIX")
        val handle = resolver.insert(collectionUri, ContentValues())
            ?: throw IOException("Signer did not allocate a payload handle at $collectionUri")
        try {
            val stream = resolver.openOutputStream(handle, "w")
                ?: throw IOException("openOutputStream returned null for $handle")
            stream.use { out ->
                out.write(bytes)
                out.flush()
            }
            return handle
        } catch (t: Throwable) {
            runCatching { resolver.delete(handle, null, null) }
            throw t
        }
    }

    fun readStreamBytes(resolver: ContentResolver, uriString: String): ByteArray {
        val uri = Uri.parse(uriString)
        val input = resolver.openInputStream(uri)
            ?: throw IOException("openInputStream returned null for $uriString")
        return input.use { it.readBytes() }
    }

    fun readStreamUtf8(resolver: ContentResolver, uriString: String): String =
        readStreamBytes(resolver, uriString).toString(Charsets.UTF_8)

    fun verifySingleUseConsumed(resolver: ContentResolver, uriString: String): Boolean =
        try {
            resolver.openInputStream(Uri.parse(uriString))?.close()
            false
        } catch (_: FileNotFoundException) {
            true
        }

    fun buildUnsignedEventJson(
        pubkeyHex: String,
        contentSizeBytes: Int,
        tagLabel: String = "nip55-large-payload-test",
    ): Pair<String, String> {
        val content = repeatingAscii(contentSizeBytes)
        val event = JSONObject().apply {
            put("id", "")
            put("pubkey", pubkeyHex)
            put("created_at", System.currentTimeMillis() / 1000L)
            put("kind", 1)
            put("tags", JSONArray().put(JSONArray().put("t").put(tagLabel)))
            put("content", content)
            put("sig", "")
        }
        return content to event.toString()
    }

    fun buildRawBinaryBase64(rawSizeBytes: Int): Pair<ByteArray, String> {
        val raw = ByteArray(rawSizeBytes) { ((it * 31 + 7) % 251).toByte() }
        val wireBase64 = Base64.encodeToString(raw, Base64.NO_WRAP)
        return raw to wireBase64
    }

    fun buildGetPublicKeyIntent(signerPackage: String, id: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:")).apply {
            setPackage(signerPackage)
            putExtra("type", "get_public_key")
            putExtra("permissions", REQUESTED_PERMISSIONS_JSON)
            putExtra("id", id)
        }

    fun buildSignEventIntent(
        signerPackage: String,
        id: String,
        currentUserHex: String,
        payloadOrUri: String,
        useStreaming: Boolean,
        returnType: String?,
    ): Intent = Intent(
        Intent.ACTION_VIEW,
        if (useStreaming) Uri.parse("nostrsigner:") else Uri.parse("nostrsigner:$payloadOrUri"),
    ).apply {
        setPackage(signerPackage)
        putExtra("type", "sign_event")
        putExtra("id", id)
        putExtra("current_user", currentUserHex)
        if (returnType != null) {
            putExtra("returnType", returnType)
        }
        if (useStreaming) {
            putExtra(EXTRA_PAYLOAD_URI, payloadOrUri)
            putExtra(EXTRA_ACCEPT_RESULT_URI, true)
        }
    }

    fun buildNip44v3Intent(
        signerPackage: String,
        type: String,
        id: String,
        currentUserHex: String,
        peerHex: String,
        kind: Int,
        scope: String,
        payloadUri: String,
    ): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:")).apply {
        setPackage(signerPackage)
        putExtra("type", type)
        putExtra("id", id)
        putExtra("pubkey", peerHex)
        putExtra("kind", kind.toString())
        putExtra("scope", scope)
        putExtra("current_user", currentUserHex)
        putExtra(EXTRA_PAYLOAD_URI, payloadUri)
        putExtra(EXTRA_ACCEPT_RESULT_URI, true)
    }

    fun querySignEvent(
        resolver: ContentResolver,
        signerPackage: String,
        currentUserHex: String,
        contentSizeBytes: Int,
        returnType: String?,
    ): SignEventResolverResult {
        val startedMs = System.currentTimeMillis()
        val (expectedContent, unsignedJson) = buildUnsignedEventJson(currentUserHex, contentSizeBytes)
        val uploadUri = stagePayload(resolver, signerPackage, unsignedJson.toByteArray(Charsets.UTF_8))
        val suffix = if (returnType != null) "&return_type=$returnType" else ""
        val queryUri = Uri.parse("content://$signerPackage.SIGN_EVENT?payload_in_uri=1&accept_result_uri=1$suffix")
        val cursor = resolver.query(
            queryUri,
            arrayOf(uploadUri.toString(), "", currentUserHex),
            null,
            null,
            null,
        ) ?: error("Query returned null cursor (run step 1 Login & approve permissions in Amber first)")

        cursor.use { c ->
            checkRejectedOrEmpty(c, "SIGN_EVENT")
            val cols = c.columnNames.toList()
            val sig = c.getString(c.getColumnIndexOrThrow("signature"))
            val result = c.getString(c.getColumnIndexOrThrow("result"))
            require(sig.length == 128 && sig == result) {
                "Invalid signature/result returned (sig=${sig.length}, same=${sig == result})"
            }

            val eventUriIdx = c.getColumnIndex(EXTRA_EVENT_URI)
            val inlineEventIdx = c.getColumnIndex("event")
            if (returnType == "signature") {
                require(eventUriIdx == -1 && inlineEventIdx == -1) {
                    "Expected event and event_uri columns to be omitted for return_type=signature, got $cols"
                }
                return SignEventResolverResult(
                    signatureHex = sig,
                    resultValue = result,
                    columns = cols,
                    eventTransport = "omitted (signature-only)",
                    eventJsonBytes = null,
                    verifiedEventId = null,
                    verifiedContentLength = null,
                    singleUseDeleted = null,
                    elapsedMs = System.currentTimeMillis() - startedMs,
                )
            }

            val eventUri = if (eventUriIdx >= 0) c.getString(eventUriIdx) else null
            val inlineEvent = if (inlineEventIdx >= 0) c.getString(inlineEventIdx) else null
            val (transport, signedJson, singleUseDeleted) = when {
                !eventUri.isNullOrBlank() -> {
                    val text = readStreamUtf8(resolver, eventUri)
                    val deleted = verifySingleUseConsumed(resolver, eventUri)
                    Triple("streamed (event_uri)", text, deleted)
                }
                !inlineEvent.isNullOrBlank() -> Triple("inline (event)", inlineEvent, null)
                else -> error("Expected event or event_uri in cursor columns $cols")
            }

            val signedObj = JSONObject(signedJson)
            val verifiedId = signedObj.getString("id")
            val verifiedPubkey = signedObj.getString("pubkey")
            val verifiedSig = signedObj.getString("sig")
            val verifiedContent = signedObj.getString("content")
            require(verifiedId.length == 64) { "Unexpected event id length: ${verifiedId.length}" }
            require(verifiedPubkey == currentUserHex) { "Pubkey mismatch: $verifiedPubkey != $currentUserHex" }
            require(verifiedSig == sig) { "Event sig != cursor signature" }
            require(verifiedContent == expectedContent) {
                "Signed content mismatch (len=${verifiedContent.length} vs ${expectedContent.length})"
            }

            return SignEventResolverResult(
                signatureHex = sig,
                resultValue = result,
                columns = cols,
                eventTransport = transport,
                eventJsonBytes = signedJson.toByteArray(Charsets.UTF_8).size,
                verifiedEventId = verifiedId,
                verifiedContentLength = verifiedContent.length,
                singleUseDeleted = singleUseDeleted,
                elapsedMs = System.currentTimeMillis() - startedMs,
            )
        }
    }

    fun queryNip44v3RoundTrip(
        resolver: ContentResolver,
        signerPackage: String,
        currentUserHex: String,
        rawSizeBytes: Int,
    ): Nip44v3RoundTripResult {
        val startedMs = System.currentTimeMillis()
        val (rawPlaintext, wirePlaintext) = buildRawBinaryBase64(rawSizeBytes)

        val plainUri = stagePayload(resolver, signerPackage, wirePlaintext.toByteArray(Charsets.UTF_8))
        val encryptUri = Uri.parse("content://$signerPackage.NIP44_V3_ENCRYPT?payload_in_uri=1&accept_result_uri=1")
        val encryptCursor = resolver.query(
            encryptUri,
            arrayOf(plainUri.toString(), currentUserHex, currentUserHex, NIP44_V3_KIND.toString(), NIP44_V3_SCOPE),
            null,
            null,
            null,
        ) ?: error("NIP44_V3_ENCRYPT returned null cursor (run Login & approve permissions first)")

        val (ciphertext, encryptTransport, encryptDeleted) = encryptCursor.use { c ->
            readSingleValueFromCursor(resolver, c, "NIP44_V3_ENCRYPT")
        }

        val cipherUri = stagePayload(resolver, signerPackage, ciphertext.toByteArray(Charsets.UTF_8))
        val decryptUri = Uri.parse("content://$signerPackage.NIP44_V3_DECRYPT?payload_in_uri=1&accept_result_uri=1")
        val decryptCursor = resolver.query(
            decryptUri,
            arrayOf(cipherUri.toString(), currentUserHex, currentUserHex, NIP44_V3_KIND.toString(), NIP44_V3_SCOPE),
            null,
            null,
            null,
        ) ?: error("NIP44_V3_DECRYPT returned null cursor (run Login & approve permissions first)")

        val (decryptedWire, decryptTransport, decryptDeleted) = decryptCursor.use { c ->
            readSingleValueFromCursor(resolver, c, "NIP44_V3_DECRYPT")
        }

        val decodedBytes = Base64.decode(decryptedWire, Base64.DEFAULT)
        val matches = decryptedWire == wirePlaintext && rawPlaintext.contentEquals(decodedBytes)
        require(matches) {
            "NIP44_V3 decrypted bytes did not match original (${decodedBytes.size} vs ${rawPlaintext.size})"
        }

        return Nip44v3RoundTripResult(
            rawPlaintextBytes = rawPlaintext.size,
            base64PlaintextBytes = wirePlaintext.length,
            ciphertextBytes = ciphertext.toByteArray(Charsets.UTF_8).size,
            encryptTransport = encryptTransport,
            decryptTransport = decryptTransport,
            encryptSingleUseDeleted = encryptDeleted,
            decryptSingleUseDeleted = decryptDeleted,
            byteForByteMatch = true,
            elapsedMs = System.currentTimeMillis() - startedMs,
        )
    }

    private fun readSingleValueFromCursor(
        resolver: ContentResolver,
        c: Cursor,
        opName: String,
    ): Triple<String, String, Boolean?> {
        checkRejectedOrEmpty(c, opName)
        val uriIdx = c.getColumnIndex(EXTRA_RESULT_URI)
        if (uriIdx >= 0) {
            val resultUri = c.getString(uriIdx)
            val value = readStreamUtf8(resolver, resultUri)
            val deleted = verifySingleUseConsumed(resolver, resultUri)
            return Triple(value, "streamed (result_uri)", deleted)
        }
        val resultIdx = c.getColumnIndex("result")
        if (resultIdx >= 0) {
            return Triple(c.getString(resultIdx), "inline (result)", null)
        }
        error("$opName cursor had neither result_uri nor result: ${c.columnNames.toList()}")
    }

    private fun checkRejectedOrEmpty(c: Cursor, opName: String) {
        require(c.moveToFirst()) { "$opName cursor is empty" }
        val rejectedIdx = c.getColumnIndex("rejected")
        if (rejectedIdx >= 0 && c.getString(rejectedIdx) == "true") {
            error("$opName was rejected by signer")
        }
    }

    private fun repeatingAscii(length: Int): String {
        val pattern = "AmberNIP55LargePayloadTest-"
        val sb = StringBuilder(length)
        while (sb.length + pattern.length <= length) {
            sb.append(pattern)
        }
        val rem = length - sb.length
        if (rem > 0) {
            sb.append(pattern, 0, rem)
        }
        return sb.toString()
    }
}

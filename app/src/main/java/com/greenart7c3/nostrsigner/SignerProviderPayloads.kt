package com.greenart7c3.nostrsigner

import com.greenart7c3.nostrsigner.common.decodeQueryParamValue
import com.greenart7c3.nostrsigner.service.EphemeralPayloadStore

internal data class SignerQueryOptions(
    val baseUri: String,
    val payloadInUri: Boolean,
    val acceptResultUri: Boolean,
    val returnType: String?,
)

internal class SignerCursorPayload(
    val columns: Array<String>,
    val values: Array<Any>,
)

internal fun parseSignerQueryOptions(uriString: String): SignerQueryOptions {
    val baseUri = uriString.substringBefore('?')
    val query = uriString.substringAfter('?', "")
    if (query.isEmpty()) {
        return SignerQueryOptions(baseUri, payloadInUri = false, acceptResultUri = false, returnType = null)
    }
    val params = HashMap<String, String>()
    for (pair in query.split('&')) {
        val idx = pair.indexOf('=')
        if (idx <= 0) continue
        val key = decodeQueryParamValue(pair.substring(0, idx))
        val value = decodeQueryParamValue(pair.substring(idx + 1))
        params[key] = value
    }
    return SignerQueryOptions(
        baseUri = baseUri,
        payloadInUri = params[EphemeralPayloadStore.QUERY_PARAM_PAYLOAD_IN_URI].isTruthyParam(),
        acceptResultUri = params[EphemeralPayloadStore.QUERY_PARAM_ACCEPT_RESULT_URI].isTruthyParam(),
        returnType = params[EphemeralPayloadStore.QUERY_PARAM_RETURN_TYPE],
    )
}

internal fun buildSignEventCursorPayload(
    result: String,
    signedEventJson: String,
    isAnonymousZap: Boolean,
    acceptResultUri: Boolean,
    returnType: String?,
    inlineThresholdBytes: Int,
    spillResponse: ((ByteArray) -> String)?,
): SignerCursorPayload {
    val omitEvent = returnType.equals("signature", ignoreCase = true) && !isAnonymousZap
    val columns = ArrayList<String>(4)
    val values = ArrayList<Any>(4)
    appendPrimaryResult(columns, values, result, acceptResultUri, inlineThresholdBytes, spillResponse)
    if (!omitEvent) {
        appendEventResult(columns, values, signedEventJson, acceptResultUri, inlineThresholdBytes, spillResponse)
    }
    return SignerCursorPayload(columns.toTypedArray(), values.toTypedArray())
}

internal fun buildSingleValueCursorPayload(
    result: String,
    acceptResultUri: Boolean,
    inlineThresholdBytes: Int,
    spillResponse: ((ByteArray) -> String)?,
): SignerCursorPayload {
    val bytes = result.toByteArray(Charsets.UTF_8)
    if (acceptResultUri && spillResponse != null && bytes.size > inlineThresholdBytes) {
        return SignerCursorPayload(
            columns = arrayOf(EphemeralPayloadStore.EXTRA_RESULT_URI),
            values = arrayOf(spillResponse(bytes)),
        )
    }
    return SignerCursorPayload(
        columns = arrayOf("signature", "result"),
        values = arrayOf(result, result),
    )
}

private fun appendPrimaryResult(
    columns: MutableList<String>,
    values: MutableList<Any>,
    result: String,
    acceptResultUri: Boolean,
    inlineThresholdBytes: Int,
    spillResponse: ((ByteArray) -> String)?,
) {
    val bytes = result.toByteArray(Charsets.UTF_8)
    if (acceptResultUri && spillResponse != null && bytes.size > inlineThresholdBytes) {
        columns += EphemeralPayloadStore.EXTRA_RESULT_URI
        values += spillResponse(bytes)
    } else {
        columns += "signature"
        values += result
        columns += "result"
        values += result
    }
}

private fun appendEventResult(
    columns: MutableList<String>,
    values: MutableList<Any>,
    signedEventJson: String,
    acceptResultUri: Boolean,
    inlineThresholdBytes: Int,
    spillResponse: ((ByteArray) -> String)?,
) {
    val bytes = signedEventJson.toByteArray(Charsets.UTF_8)
    if (acceptResultUri && spillResponse != null && bytes.size > inlineThresholdBytes) {
        columns += EphemeralPayloadStore.EXTRA_EVENT_URI
        values += spillResponse(bytes)
    } else {
        columns += "event"
        values += signedEventJson
    }
}

private fun String?.isTruthyParam(): Boolean = this == "1" || equals("true", ignoreCase = true)

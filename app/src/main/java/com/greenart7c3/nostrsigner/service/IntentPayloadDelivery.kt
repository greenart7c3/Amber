package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.models.IntentData
import kotlinx.coroutines.CancellationException

internal fun resolvePackageUid(context: Context, packageName: String): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    context.packageManager.getPackageUid(packageName, PackageManager.PackageInfoFlags.of(0))
} else {
    @Suppress("DEPRECATION")
    context.packageManager.getPackageUid(packageName, 0)
}

internal suspend fun consumeStagedIntentPayload(
    context: Context?,
    packageName: String?,
    payloadUri: String,
    payloadStore: EphemeralPayloadStore,
): StagedPayloadResult {
    if (context == null || packageName.isNullOrBlank()) {
        return StagedPayloadResult.Invalid("Missing caller context for payload_uri")
    }
    val token = EphemeralPayloadStore.extractToken(payloadUri, BuildConfig.APPLICATION_ID)
        ?: return StagedPayloadResult.Invalid("Invalid payload_uri")
    return try {
        val callerUid = resolvePackageUid(context, packageName)
        val payload = payloadStore.consumeUpload(token, callerUid, packageName).toString(Charsets.UTF_8)
        StagedPayloadResult.Ready(payload)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        StagedPayloadResult.Invalid(e.message ?: "Failed to read staged payload", e)
    }
}

internal sealed interface StagedPayloadResult {
    data class Ready(val payload: String) : StagedPayloadResult
    data class Invalid(val message: String, val cause: Throwable? = null) : StagedPayloadResult
}

internal fun createResponseSpiller(
    context: Context,
    packageName: String,
    payloadStore: EphemeralPayloadStore,
): (ByteArray) -> String {
    val callerUid = resolvePackageUid(context, packageName)
    return { bytes: ByteArray ->
        EphemeralPayloadStore.buildPayloadUri(
            BuildConfig.APPLICATION_ID,
            payloadStore.putResponse(callerUid, packageName, bytes),
        )
    }
}

internal fun applyNativeResultExtras(
    target: Intent,
    context: Context,
    packageName: String,
    intentData: IntentData,
    event: String,
    value: String,
    payloadStore: EphemeralPayloadStore?,
) {
    val spillResponse = if (intentData.acceptResultUri && payloadStore != null) {
        createResponseSpiller(context, packageName, payloadStore)
    } else {
        null
    }
    val threshold = payloadStore?.inlineThresholdBytes ?: EphemeralPayloadStore.INLINE_THRESHOLD_BYTES
    buildNativeResultExtras(intentData, event, value, threshold, spillResponse).forEach { (k, v) ->
        target.putExtra(k, v)
    }
}

internal fun buildNativeResultExtras(
    intentData: IntentData,
    event: String,
    value: String,
    inlineThresholdBytes: Int = EphemeralPayloadStore.INLINE_THRESHOLD_BYTES,
    spillResponse: ((ByteArray) -> String)? = null,
): Map<String, String> {
    val extras = LinkedHashMap<String, String>()
    val valueBytes = value.toByteArray(Charsets.UTF_8)
    val spilledValue = intentData.acceptResultUri && spillResponse != null && valueBytes.size > inlineThresholdBytes
    if (spilledValue) {
        extras[EphemeralPayloadStore.EXTRA_RESULT_URI] = spillResponse(valueBytes)
    } else {
        extras["signature"] = value
        extras["result"] = value
    }
    extras["id"] = intentData.id

    val isNormalSignEvent = intentData.type == SignerType.SIGN_EVENT && value != event
    val omitEvent = isNormalSignEvent && intentData.explicitSignatureOnly
    if (!omitEvent && event.isNotEmpty()) {
        appendIntentEventExtra(
            extras = extras,
            type = intentData.type,
            event = event,
            acceptResultUri = intentData.acceptResultUri,
            spilledValue = spilledValue,
            inlineThresholdBytes = inlineThresholdBytes,
            spillResponse = spillResponse,
        )
    }
    if (intentData.type == SignerType.GET_PUBLIC_KEY) {
        extras["package"] = BuildConfig.APPLICATION_ID
    }
    return extras
}

private fun appendIntentEventExtra(
    extras: MutableMap<String, String>,
    type: SignerType,
    event: String,
    acceptResultUri: Boolean,
    spilledValue: Boolean,
    inlineThresholdBytes: Int,
    spillResponse: ((ByteArray) -> String)?,
) {
    if (type != SignerType.SIGN_EVENT) {
        if (!spilledValue) {
            extras["event"] = event
        }
        return
    }
    val eventBytes = event.toByteArray(Charsets.UTF_8)
    if (acceptResultUri && spillResponse != null && eventBytes.size > inlineThresholdBytes) {
        extras[EphemeralPayloadStore.EXTRA_EVENT_URI] = spillResponse(eventBytes)
    } else {
        extras["event"] = event
    }
}

internal fun buildBatchResultExtras(
    resultsJson: String,
    acceptResultUri: Boolean,
    inlineThresholdBytes: Int = EphemeralPayloadStore.INLINE_THRESHOLD_BYTES,
    spillResponse: ((ByteArray) -> String)? = null,
): Map<String, String> {
    val bytes = resultsJson.toByteArray(Charsets.UTF_8)
    if (acceptResultUri && spillResponse != null && bytes.size > inlineThresholdBytes) {
        return mapOf(EphemeralPayloadStore.EXTRA_RESULTS_URI to spillResponse(bytes))
    }
    return mapOf("results" to resultsJson)
}

package com.greenart7c3.nostrsigner

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import com.greenart7c3.nostrsigner.service.EphemeralPayloadStore
import java.io.FileNotFoundException
import java.io.IOException

class SignerProvider : ContentProvider() {
    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int {
        val ctx = context ?: return 0
        val binderCallingPackage = callingPackage ?: return 0
        val callingUid = Binder.getCallingUid()
        val token = EphemeralPayloadStore.extractToken(uri.toString(), BuildConfig.APPLICATION_ID) ?: return 0
        return if (ctx.amber.payloadStore.delete(token, callingUid, binderCallingPackage)) 1 else 0
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? {
        val ctx = context ?: return null
        val binderCallingPackage = callingPackage ?: return null
        val callingUid = Binder.getCallingUid()
        if (!EphemeralPayloadStore.isPayloadsCollectionUri(uri.toString(), BuildConfig.APPLICATION_ID)) {
            return null
        }
        return try {
            val token = ctx.amber.payloadStore.allocateUpload(callingUid, binderCallingPackage)
            EphemeralPayloadStore.buildPayloadUri(BuildConfig.APPLICATION_ID, token).toUri()
        } catch (e: IOException) {
            AmberLog.d(Amber.TAG, "Failed to allocate payload handle for $binderCallingPackage", e)
            null
        }
    }

    override fun onCreate(): Boolean = true

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException("No context")
        val binderCallingPackage = callingPackage ?: throw SecurityException("No calling package")
        val callingUid = Binder.getCallingUid()
        val token = EphemeralPayloadStore.extractToken(uri.toString(), BuildConfig.APPLICATION_ID)
            ?: throw FileNotFoundException("Invalid payload URI")
        return openPipeForMode(ctx.amber.payloadStore, token, callingUid, binderCallingPackage, mode)
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        val ctx = context ?: run {
            AmberLog.d(Amber.TAG, "No context")
            return null
        }
        val binderCallingPackage = callingPackage ?: run {
            AmberLog.d(Amber.TAG, "No calling package")
            return null
        }
        val callingUid = Binder.getCallingUid()
        val amber = ctx.amber
        val options = parseSignerQueryOptions(uri.toString())
        val resolvedArguments = if (options.payloadInUri) {
            resolveStagedArguments(
                payloadStore = amber.payloadStore,
                callingUid = callingUid,
                callingPackage = binderCallingPackage,
                projection = projection,
            ) ?: return null
        } else {
            projection
        }
        val cursor = SignerProviderQuery(amber, amber.accountDatabases, amber.applicationIOScope).query(
            context = ctx,
            requesterId = binderCallingPackage,
            callerPackageName = binderCallingPackage,
            operationUri = uri,
            arguments = resolvedArguments,
            acceptResultUri = options.acceptResultUri,
            returnType = options.returnType,
            inlineThresholdBytes = amber.payloadStore.inlineThresholdBytes,
            spillResponse = { bytes ->
                EphemeralPayloadStore.buildPayloadUri(
                    BuildConfig.APPLICATION_ID,
                    amber.payloadStore.putResponse(callingUid, binderCallingPackage, bytes),
                )
            },
        )
        if (cursor != null || !options.payloadInUri || !options.retainPayload) return cursor
        val payload = resolvedArguments?.firstOrNull() ?: return null
        return retainForIntent(amber.payloadStore, callingUid, binderCallingPackage, payload)
    }

    private fun retainForIntent(
        payloadStore: EphemeralPayloadStore,
        callingUid: Int,
        callingPackage: String,
        payload: String,
    ): Cursor? = try {
        val token = payloadStore.retainUpload(callingUid, callingPackage, payload.toByteArray(Charsets.UTF_8))
        MatrixCursor(arrayOf(EphemeralPayloadStore.COLUMN_RETAINED_PAYLOAD_URI)).apply {
            addRow(arrayOf<Any>(EphemeralPayloadStore.buildPayloadUri(BuildConfig.APPLICATION_ID, token)))
        }
    } catch (e: IOException) {
        AmberLog.d(Amber.TAG, "Could not retain the staged payload for $callingPackage", e)
        null
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0

    private fun openPipeForMode(
        store: EphemeralPayloadStore,
        token: String,
        callingUid: Int,
        callingPackage: String,
        mode: String,
    ): ParcelFileDescriptor = when (mode) {
        "r" -> store.openReadPipe(token, callingUid, callingPackage)
        "w", "wt" -> store.openWritePipe(token, callingUid, callingPackage)
        else -> throw IllegalArgumentException("Unsupported mode: $mode")
    }

    private fun resolveStagedArguments(
        payloadStore: EphemeralPayloadStore,
        callingUid: Int,
        callingPackage: String,
        projection: Array<String>?,
    ): Array<String>? {
        val rawUri = projection?.firstOrNull() ?: return null
        val token = EphemeralPayloadStore.extractToken(rawUri, BuildConfig.APPLICATION_ID) ?: return null
        val payload = try {
            payloadStore.consumeUploadBlocking(token, callingUid, callingPackage).toString(Charsets.UTF_8)
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            AmberLog.d(Amber.TAG, "Failed to consume staged payload for $callingPackage", e)
            return null
        }
        return projection.copyOf().apply { this[0] = payload }
    }
}

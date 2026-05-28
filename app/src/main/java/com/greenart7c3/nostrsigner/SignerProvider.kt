package com.greenart7c3.nostrsigner

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.greenart7c3.nostrsigner.models.SignerType
import com.vitorpamplona.quartz.utils.Hex

class SignerProvider : ContentProvider() {

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0

    override fun getType(uri: Uri): String? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        Log.d(Amber.TAG, "Querying $uri has context ${context != null}")

        val appId = BuildConfig.APPLICATION_ID
        val uriString = uri.toString()
        val packageName = if (sortOrder.isNullOrBlank()) {
            callingPackage
        } else {
            if (Hex.isHex(sortOrder)) {
                sortOrder
            } else {
                null
            }
        }
        if (packageName == null) {
            Log.d(Amber.TAG, "No package name")
            return null
        }
        val localContext = context ?: return null
        return try {
            when (uriString) {
                "content://$appId.SIGN_MESSAGE" -> {
                    val message = projection?.first() ?: run {
                        Log.d(Amber.TAG, "No message")
                        return null
                    }
                    SignerCore.signMessage(localContext, packageName, message, projection[2])?.toCursor()
                }
                "content://$appId.SIGN_EVENT" -> {
                    val json = projection?.first() ?: run {
                        Log.d(Amber.TAG, "No json")
                        return null
                    }
                    SignerCore.signEvent(localContext, packageName, json, projection[2])?.toCursor()
                }
                "content://$appId.NIP04_DECRYPT",
                "content://$appId.NIP44_DECRYPT",
                "content://$appId.NIP04_ENCRYPT",
                "content://$appId.NIP44_ENCRYPT",
                "content://$appId.DECRYPT_ZAP_EVENT",
                -> {
                    val content = projection?.first() ?: return null
                    val pubkey = projection[1]
                    val type = when (uriString.replace("content://$appId.", "")) {
                        "NIP04_DECRYPT" -> SignerType.NIP04_DECRYPT
                        "NIP44_DECRYPT" -> SignerType.NIP44_DECRYPT
                        "NIP04_ENCRYPT" -> SignerType.NIP04_ENCRYPT
                        "NIP44_ENCRYPT" -> SignerType.NIP44_ENCRYPT
                        "DECRYPT_ZAP_EVENT" -> SignerType.DECRYPT_ZAP_EVENT
                        else -> null
                    } ?: return null
                    SignerCore.encryptOrDecrypt(localContext, packageName, type, content, pubkey, projection[2])?.toCursor()
                }
                "content://$appId.SIGN_PSBT" -> {
                    val psbtHex = projection?.first() ?: run {
                        Log.d(Amber.TAG, "No psbt")
                        return null
                    }
                    SignerCore.signPsbt(localContext, packageName, psbtHex, projection[2])?.toCursor()
                }
                "content://$appId.PING" -> {
                    val npub = if (projection != null && projection.isNotEmpty()) projection[0] else null
                    when (val result = SignerCore.ping(localContext, packageName, npub)) {
                        null -> null
                        is SignerCore.SignResult.Rejected -> result.toCursor()
                        // PING keeps its legacy two-column shape for backwards compatibility.
                        is SignerCore.SignResult.Reply ->
                            MatrixCursor(arrayOf("signature", "result")).also {
                                it.addRow(arrayOf<Any?>(result.result, result.result))
                            }
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            SignerCore.logError(localContext, packageName, uriString.replace("content://$appId.", ""), e.message ?: "Error from $callingPackage $uri")
            return null
        }
    }

    /** Maps a [SignerCore.SignResult] onto the standard cursor columns shared by most operations. */
    private fun SignerCore.SignResult.toCursor(): Cursor = when (this) {
        is SignerCore.SignResult.Rejected ->
            MatrixCursor(arrayOf("rejected")).also { it.addRow(arrayOf("true")) }
        is SignerCore.SignResult.Reply ->
            MatrixCursor(arrayOf("signature", "event", "result")).also {
                it.addRow(arrayOf<Any?>(signature, event, result))
            }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0
}

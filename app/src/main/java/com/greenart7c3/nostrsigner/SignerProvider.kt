package com.greenart7c3.nostrsigner

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class SignerProvider : ContentProvider() {
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        return 0
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        LocalPreferences.appDatabase = runBlocking {
            withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(context!!)
            }
        }
        val appId = BuildConfig.APPLICATION_ID
        return when (uri.toString()) {
            "content://$appId.SIGN_EVENT" -> {
                val packageName = callingPackage ?: return null
                val json = projection?.first() ?: return null
                if (!LocalPreferences.containsAccount(projection[2])) return null
                val account = LocalPreferences.loadFromEncryptedStorage(projection[2]) ?: return null
                val event = Event.fromJson(json)
                val currentSelection = selection ?: "0"
                val permission = LocalPreferences.appDatabase!!
                    .applicationDao()
                    .getPermission(
                        packageName,
                        "SIGN_EVENT",
                        event.kind
                    )
                if (currentSelection == "1") {
                    val isRemembered = permission?.acceptable ?: return null
                    if (!isRemembered) {
                        val cursor = MatrixCursor(arrayOf("rejected")).also {
                            it.addRow(arrayOf("true"))
                        }
                        return cursor
                    }
                } else {
                    val isRemembered = permission?.acceptable ?: false
                    if (!isRemembered) return null
                }

                var cursor: MatrixCursor? = null

                account.signer.sign<Event>(event.createdAt, event.kind, event.tags, event.content) { signedEvent ->
                    val localCursor = MatrixCursor(arrayOf("signature", "event")).also {
                        val signature = if (event is LnZapRequestEvent && event.tags.any { tag -> tag.any { t -> t == "anon" } }) signedEvent.toJson() else signedEvent.sig
                        it.addRow(arrayOf(signature, signedEvent.toJson()))
                    }
                    cursor = localCursor
                }

                while (cursor == null) {
                    // do nothing
                }

                return cursor
            }
            "content://$appId.NIP04_DECRYPT",
            "content://$appId.NIP44_DECRYPT",
            "content://$appId.NIP04_ENCRYPT",
            "content://$appId.NIP44_ENCRYPT",
            "content://$appId.DECRYPT_ZAP_EVENT" -> {
                val packageName = callingPackage ?: return null
                val content = projection?.first() ?: return null
                if (!LocalPreferences.containsAccount(projection[2])) return null
                val stringType = uri.toString().replace("content://$appId.", "")
                val pubkey = projection[1]
                val account = LocalPreferences.loadFromEncryptedStorage(projection[2]) ?: return null
                val currentSelection = selection ?: "0"

                val permission = LocalPreferences.appDatabase!!
                    .applicationDao()
                    .getPermission(
                        packageName,
                        uri.toString().replace("content://$appId.", "")
                    )

                if (currentSelection == "1") {
                    val isRemembered = permission?.acceptable ?: return null
                    if (!isRemembered) {
                        val cursor = MatrixCursor(arrayOf("rejected")).also {
                            it.addRow(arrayOf("true"))
                        }
                        return cursor
                    }
                } else {
                    val isRemembered = permission?.acceptable ?: false
                    if (!isRemembered) return null
                }

                val type = when (stringType) {
                    "NIP04_DECRYPT" -> SignerType.NIP04_DECRYPT
                    "NIP44_DECRYPT" -> SignerType.NIP44_DECRYPT
                    "NIP04_ENCRYPT" -> SignerType.NIP04_ENCRYPT
                    "NIP44_ENCRYPT" -> SignerType.NIP44_ENCRYPT
                    "DECRYPT_ZAP_EVENT" -> SignerType.DECRYPT_ZAP_EVENT
                    else -> null
                } ?: return null

                val result = try {
                    AmberUtils.encryptOrDecryptData(
                        content,
                        type,
                        account,
                        pubkey
                    ) ?: "Could not decrypt the message"
                } catch (e: Exception) {
                    "Could not decrypt the message"
                }

                if (type == SignerType.NIP04_ENCRYPT && result == "Could not decrypt the message") {
                    return null
                } else {
                    val cursor = MatrixCursor(arrayOf("signature"))
                    cursor.addRow(arrayOf<Any>(result))
                    return cursor
                }
            }

            "content://$appId.GET_PUBLIC_KEY" -> {
                val packageName = callingPackage ?: return null
                val account = LocalPreferences.loadFromEncryptedStorage() ?: return null
                val currentSelection = selection ?: "0"

                val permission = LocalPreferences.appDatabase!!
                    .applicationDao()
                    .getPermission(
                        packageName,
                        "GET_PUBLIC_KEY"
                    )

                if (currentSelection == "1") {
                    val isRemembered = permission?.acceptable ?: return null
                    if (!isRemembered) {
                        val cursor = MatrixCursor(arrayOf("rejected")).also {
                            it.addRow(arrayOf("true"))
                        }
                        return cursor
                    }
                } else {
                    val isRemembered = permission?.acceptable ?: false
                    if (!isRemembered) return null
                }

                val cursor = MatrixCursor(arrayOf("signature"))
                cursor.addRow(arrayOf<Any>(account.keyPair.pubKey.toNpub()))
                return cursor
            }

            else -> null
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        return 0
    }
}

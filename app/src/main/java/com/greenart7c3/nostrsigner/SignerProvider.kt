package com.greenart7c3.nostrsigner

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.greenart7c3.nostrsigner.database.HistoryEntity2
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.kindToNip
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip55AndroidSigner.signString
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SignerProvider : ContentProvider() {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int {
        return 0
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? {
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
        sortOrder: String?,
    ): Cursor? {
        val appId = BuildConfig.APPLICATION_ID
        return try {
            when (uri.toString()) {
                "content://$appId.SIGN_MESSAGE" -> {
                    val packageName = callingPackage ?: return null
                    val message = projection?.first() ?: return null
                    val npub = IntentUtils.parsePubKey(projection[2]) ?: return null
                    if (!LocalPreferences.containsAccount(context!!, npub)) return null
                    val account = LocalPreferences.loadFromEncryptedStorage(context!!, npub) ?: return null
                    val database = NostrSigner.instance.getDatabase(account.npub)
                    val signPolicy = database.applicationDao().getSignPolicy(sortOrder ?: packageName)
                    val permission =
                        database
                            .applicationDao()
                            .getPermission(
                                sortOrder ?: packageName,
                                "SIGN_MESSAGE",
                            )
                    val isRemembered = if (signPolicy == 2) true else permission?.acceptable ?: return null
                    if (!isRemembered) {
                        scope.launch {
                            database.applicationDao().addHistory(
                                HistoryEntity2(
                                    0,
                                    sortOrder ?: packageName,
                                    uri.toString().replace("content://$appId.", ""),
                                    null,
                                    TimeUtils.now(),
                                    false,
                                ),
                            )
                        }
                        val cursor =
                            MatrixCursor(arrayOf("rejected")).also {
                                it.addRow(arrayOf("true"))
                            }

                        return cursor
                    }

                    val result = signString(message, account.signer.keyPair.privKey!!).toHexKey()
                    scope.launch {
                        database.applicationDao().addHistory(
                            HistoryEntity2(
                                0,
                                sortOrder ?: packageName,
                                "SIGN_MESSAGE",
                                null,
                                TimeUtils.now(),
                                true,
                            ),
                        )
                    }

                    val localCursor = MatrixCursor(arrayOf("signature", "event", "result")).also {
                        it.addRow(arrayOf(result, result, result))
                    }

                    return localCursor
                }
                "content://$appId.SIGN_EVENT" -> {
                    val packageName = callingPackage ?: return null
                    val json = projection?.first() ?: return null
                    val npub = IntentUtils.parsePubKey(projection[2]) ?: return null
                    if (!LocalPreferences.containsAccount(context!!, npub)) return null
                    val account = LocalPreferences.loadFromEncryptedStorage(context!!, npub) ?: return null
                    val event = try {
                        IntentUtils.getUnsignedEvent(json, account)
                    } catch (e: Exception) {
                        Log.d("SignerProvider", "Failed to parse event from $packageName", e)
                        return null
                    }

                    val database = NostrSigner.instance.getDatabase(account.npub)
                    var permission =
                        database
                            .applicationDao()
                            .getPermission(
                                sortOrder ?: packageName,
                                "SIGN_EVENT",
                                event.kind,
                            )
                    if (permission == null) {
                        event.kind.kindToNip()?.let {
                            val nipNumber = it.toIntOrNull()
                            permission = if (nipNumber == null) {
                                null
                            } else {
                                database
                                    .applicationDao()
                                    .getPermission(
                                        sortOrder ?: packageName,
                                        "NIP",
                                        nipNumber,
                                    )
                            }
                        }
                    }
                    val signPolicy = database.applicationDao().getSignPolicy(sortOrder ?: packageName)
                    val isRemembered = if (signPolicy == 2) true else permission?.acceptable ?: return null
                    if (!isRemembered) {
                        scope.launch {
                            database.applicationDao().addHistory(
                                HistoryEntity2(
                                    0,
                                    sortOrder ?: packageName,
                                    uri.toString().replace("content://$appId.", ""),
                                    event.kind,
                                    TimeUtils.now(),
                                    false,
                                ),
                            )
                        }

                        val cursor =
                            MatrixCursor(arrayOf("rejected")).also {
                                it.addRow(arrayOf("true"))
                            }

                        return cursor
                    }

                    val signedEvent = account.signer.signerSync.sign<Event>(event.createdAt, event.kind, event.tags, event.content)

                    if (signedEvent == null) {
                        Log.d("SignerProvider", "Failed to sign event from $packageName")
                        scope.launch {
                            database.applicationDao().addHistory(
                                HistoryEntity2(
                                    0,
                                    sortOrder ?: packageName,
                                    "SIGN_EVENT",
                                    event.kind,
                                    TimeUtils.now(),
                                    false,
                                ),
                            )
                        }
                        return null
                    }

                    scope.launch {
                        database.applicationDao().addHistory(
                            HistoryEntity2(
                                0,
                                sortOrder ?: packageName,
                                "SIGN_EVENT",
                                event.kind,
                                TimeUtils.now(),
                                true,
                            ),
                        )
                    }

                    val cursor =
                        MatrixCursor(arrayOf("signature", "event", "result")).also {
                            val signature =
                                if (event.kind == LnZapRequestEvent.KIND &&
                                    event.tags.any {
                                            tag ->
                                        tag.any { t -> t == "anon" }
                                    }
                                ) {
                                    signedEvent.toJson()
                                } else {
                                    signedEvent.sig
                                }
                            it.addRow(arrayOf(signature, signedEvent.toJson(), signature))
                        }

                    return cursor
                }
                "content://$appId.NIP04_DECRYPT",
                "content://$appId.NIP44_DECRYPT",
                "content://$appId.NIP04_ENCRYPT",
                "content://$appId.NIP44_ENCRYPT",
                "content://$appId.DECRYPT_ZAP_EVENT",
                -> {
                    val packageName = callingPackage ?: return null
                    val content = projection?.first() ?: return null
                    val npub = IntentUtils.parsePubKey(projection[2]) ?: return null
                    if (!LocalPreferences.containsAccount(context!!, npub)) return null
                    val stringType = uri.toString().replace("content://$appId.", "")
                    val pubkey = projection[1]
                    val account = LocalPreferences.loadFromEncryptedStorage(context!!, npub) ?: return null
                    val database = NostrSigner.instance.getDatabase(account.npub)
                    var permission =
                        database
                            .applicationDao()
                            .getPermission(
                                sortOrder ?: packageName,
                                uri.toString().replace("content://$appId.", ""),
                            )
                    if (permission == null) {
                        val nip = when (stringType) {
                            "NIP04_DECRYPT" -> 4
                            "NIP44_DECRYPT" -> 44
                            "NIP04_ENCRYPT" -> 4
                            "NIP44_ENCRYPT" -> 44
                            "DECRYPT_ZAP_EVENT" -> null
                            else -> null
                        }
                        nip?.let {
                            permission =
                                database
                                    .applicationDao()
                                    .getPermission(
                                        sortOrder ?: packageName,
                                        "NIP",
                                        it,
                                    )
                        }
                    }
                    val signPolicy = database.applicationDao().getSignPolicy(sortOrder ?: packageName)
                    val isRemembered = if (signPolicy == 2) true else permission?.acceptable ?: return null
                    if (!isRemembered) {
                        scope.launch {
                            database.applicationDao().addHistory(
                                HistoryEntity2(
                                    0,
                                    sortOrder ?: packageName,
                                    uri.toString().replace("content://$appId.", ""),
                                    null,
                                    TimeUtils.now(),
                                    false,
                                ),
                            )
                        }

                        val cursor =
                            MatrixCursor(arrayOf("rejected")).also {
                                it.addRow(arrayOf("true"))
                            }

                        return cursor
                    }

                    val type =
                        when (stringType) {
                            "NIP04_DECRYPT" -> SignerType.NIP04_DECRYPT
                            "NIP44_DECRYPT" -> SignerType.NIP44_DECRYPT
                            "NIP04_ENCRYPT" -> SignerType.NIP04_ENCRYPT
                            "NIP44_ENCRYPT" -> SignerType.NIP44_ENCRYPT
                            "DECRYPT_ZAP_EVENT" -> SignerType.DECRYPT_ZAP_EVENT
                            else -> null
                        } ?: return null

                    val result =
                        try {
                            AmberUtils.encryptOrDecryptData(
                                content,
                                type,
                                account,
                                pubkey,
                            ) ?: "Could not decrypt the message"
                        } catch (e: Exception) {
                            scope.launch {
                                database.applicationDao().insertLog(
                                    LogEntity(
                                        0,
                                        sortOrder ?: packageName,
                                        uri.toString().replace("content://$appId.", ""),
                                        e.message ?: "Could not decrypt the message",
                                        System.currentTimeMillis(),
                                    ),
                                )
                            }
                            "Could not decrypt the message"
                        }

                    scope.launch {
                        database.applicationDao().addHistory(
                            HistoryEntity2(
                                0,
                                sortOrder ?: packageName,
                                uri.toString().replace("content://$appId.", ""),
                                null,
                                TimeUtils.now(),
                                true,
                            ),
                        )
                    }

                    val cursor = MatrixCursor(arrayOf("signature", "event", "result"))
                    cursor.addRow(arrayOf<Any>(result, result, result))
                    return cursor
                }

                "content://$appId.GET_PUBLIC_KEY" -> {
                    val packageName = callingPackage ?: return null
                    val account = LocalPreferences.loadFromEncryptedStorage(context!!) ?: return null
                    val database = NostrSigner.instance.getDatabase(account.npub)
                    val permission =
                        database
                            .applicationDao()
                            .getPermission(
                                sortOrder ?: packageName,
                                "GET_PUBLIC_KEY",
                            )

                    val signPolicy = database.applicationDao().getSignPolicy(sortOrder ?: packageName)
                    val isRemembered = if (signPolicy == 2) true else permission?.acceptable ?: return null
                    if (!isRemembered) {
                        scope.launch {
                            database.applicationDao().addHistory(
                                HistoryEntity2(
                                    0,
                                    sortOrder ?: packageName,
                                    uri.toString().replace("content://$appId.", ""),
                                    null,
                                    TimeUtils.now(),
                                    false,
                                ),
                            )
                        }

                        val cursor =
                            MatrixCursor(arrayOf("rejected")).also {
                                it.addRow(arrayOf("true"))
                            }

                        return cursor
                    }

                    scope.launch {
                        database.applicationDao().addHistory(
                            HistoryEntity2(
                                0,
                                sortOrder ?: packageName,
                                uri.toString().replace("content://$appId.", ""),
                                null,
                                TimeUtils.now(),
                                true,
                            ),
                        )
                    }

                    val cursor = MatrixCursor(arrayOf("signature", "result"))
                    val result = if (sortOrder == null) account.npub else account.hexKey
                    cursor.addRow(arrayOf<Any>(result, result))
                    return cursor
                }

                else -> null
            }
        } catch (e: Exception) {
            Log.e("SignerProvider", "Error from $callingPackage $uri", e)
            return null
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int {
        return 0
    }
}

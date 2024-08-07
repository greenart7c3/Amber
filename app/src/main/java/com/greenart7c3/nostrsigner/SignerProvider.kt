package com.greenart7c3.nostrsigner

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.models.kindToNip
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.LnZapRequestEvent

class SignerProvider : ContentProvider() {
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
        return when (uri.toString()) {
            "content://$appId.SIGN_MESSAGE" -> {
                val packageName = callingPackage ?: return null
                val message = projection?.first() ?: return null
                val npub = IntentUtils.parsePubKey(projection[2]) ?: return null
                if (!LocalPreferences.containsAccount(context!!, npub)) return null
                val account = LocalPreferences.loadFromEncryptedStorage(context!!, npub) ?: return null
                val currentSelection = selection ?: "0"
                val database = NostrSigner.getInstance().getDatabase(account.keyPair.pubKey.toNpub())
                val permission =
                    database
                        .applicationDao()
                        .getPermission(
                            sortOrder ?: packageName,
                            "SIGN_MESSAGE",
                        )
                val isRemembered = permission?.acceptable ?: return null
                if (!isRemembered) {
                    database.applicationDao().addHistory(
                        HistoryEntity(
                            0,
                            sortOrder ?: packageName,
                            uri.toString().replace("content://$appId.", ""),
                            null,
                            TimeUtils.now(),
                            false,
                        ),
                    )
                    if (currentSelection == "1") {
                        val cursor =
                            MatrixCursor(arrayOf("rejected")).also {
                                it.addRow(arrayOf("true"))
                            }

                        return cursor
                    } else {
                        return null
                    }
                }

                val result = CryptoUtils.signString(message, account.keyPair.privKey!!).toHexKey()
                database.applicationDao().addHistory(
                    HistoryEntity(
                        0,
                        sortOrder ?: packageName,
                        "SIGN_MESSAGE",
                        null,
                        TimeUtils.now(),
                        true,
                    ),
                )

                val localCursor = MatrixCursor(arrayOf("signature", "event")).also {
                    it.addRow(arrayOf(result, result))
                }

                return localCursor
            }
            "content://$appId.SIGN_EVENT" -> {
                val packageName = callingPackage ?: return null
                val json = projection?.first() ?: return null
                val npub = IntentUtils.parsePubKey(projection[2]) ?: return null
                if (!LocalPreferences.containsAccount(context!!, npub)) return null
                val account = LocalPreferences.loadFromEncryptedStorage(context!!, npub) ?: return null
                val event = Event.fromJson(json)
                val currentSelection = selection ?: "0"
                val database = NostrSigner.getInstance().getDatabase(account.keyPair.pubKey.toNpub())
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
                        permission =
                            database
                                .applicationDao()
                                .getPermission(
                                    sortOrder ?: packageName,
                                    "NIP",
                                    it.toInt(),
                                )
                    }
                }
                val isRemembered = permission?.acceptable ?: return null
                if (!isRemembered) {
                    database.applicationDao().addHistory(
                        HistoryEntity(
                            0,
                            sortOrder ?: packageName,
                            uri.toString().replace("content://$appId.", ""),
                            event.kind,
                            TimeUtils.now(),
                            false,
                        ),
                    )
                    if (currentSelection == "1") {
                        val cursor =
                            MatrixCursor(arrayOf("rejected")).also {
                                it.addRow(arrayOf("true"))
                            }

                        return cursor
                    } else {
                        return null
                    }
                }

                var cursor: MatrixCursor? = null

                account.signer.sign<Event>(event.createdAt, event.kind, event.tags, event.content) { signedEvent ->
                    database.applicationDao().addHistory(
                        HistoryEntity(
                            0,
                            sortOrder ?: packageName,
                            "SIGN_EVENT",
                            event.kind,
                            TimeUtils.now(),
                            true,
                        ),
                    )

                    val localCursor =
                        MatrixCursor(arrayOf("signature", "event")).also {
                            val signature =
                                if (event is LnZapRequestEvent &&
                                    event.tags.any {
                                            tag ->
                                        tag.any { t -> t == "anon" }
                                    }
                                ) {
                                    signedEvent.toJson()
                                } else {
                                    signedEvent.sig
                                }
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
            "content://$appId.DECRYPT_ZAP_EVENT",
            -> {
                val packageName = callingPackage ?: return null
                val content = projection?.first() ?: return null
                val npub = IntentUtils.parsePubKey(projection[2]) ?: return null
                if (!LocalPreferences.containsAccount(context!!, npub)) return null
                val stringType = uri.toString().replace("content://$appId.", "")
                val pubkey = projection[1]
                val account = LocalPreferences.loadFromEncryptedStorage(context!!, npub) ?: return null
                val currentSelection = selection ?: "0"
                val database = NostrSigner.getInstance().getDatabase(account.keyPair.pubKey.toNpub())
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

                val isRemembered = permission?.acceptable ?: return null
                if (!isRemembered) {
                    database.applicationDao().addHistory(
                        HistoryEntity(
                            0,
                            sortOrder ?: packageName,
                            uri.toString().replace("content://$appId.", ""),
                            null,
                            TimeUtils.now(),
                            false,
                        ),
                    )

                    if (currentSelection == "1") {
                        val cursor =
                            MatrixCursor(arrayOf("rejected")).also {
                                it.addRow(arrayOf("true"))
                            }

                        return cursor
                    } else {
                        return null
                    }
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
                        "Could not decrypt the message"
                    }

                database.applicationDao().addHistory(
                    HistoryEntity(
                        0,
                        sortOrder ?: packageName,
                        uri.toString().replace("content://$appId.", ""),
                        null,
                        TimeUtils.now(),
                        true,
                    ),
                )

                val cursor = MatrixCursor(arrayOf("signature", "event"))
                cursor.addRow(arrayOf<Any>(result, result))
                return cursor
            }

            "content://$appId.GET_PUBLIC_KEY" -> {
                val packageName = callingPackage ?: return null
                val account = LocalPreferences.loadFromEncryptedStorage(context!!) ?: return null
                val currentSelection = selection ?: "0"
                val database = NostrSigner.getInstance().getDatabase(account.keyPair.pubKey.toNpub())
                val permission =
                    database
                        .applicationDao()
                        .getPermission(
                            sortOrder ?: packageName,
                            "GET_PUBLIC_KEY",
                        )

                val isRemembered = permission?.acceptable ?: return null
                if (!isRemembered) {
                    database.applicationDao().addHistory(
                        HistoryEntity(
                            0,
                            sortOrder ?: packageName,
                            uri.toString().replace("content://$appId.", ""),
                            null,
                            TimeUtils.now(),
                            false,
                        ),
                    )

                    if (currentSelection == "1") {
                        val cursor =
                            MatrixCursor(arrayOf("rejected")).also {
                                it.addRow(arrayOf("true"))
                            }

                        return cursor
                    } else {
                        return null
                    }
                }

                database.applicationDao().addHistory(
                    HistoryEntity(
                        0,
                        sortOrder ?: packageName,
                        uri.toString().replace("content://$appId.", ""),
                        null,
                        TimeUtils.now(),
                        true,
                    ),
                )

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
        selectionArgs: Array<String>?,
    ): Int {
        return 0
    }
}

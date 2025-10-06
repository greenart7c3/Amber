package com.greenart7c3.nostrsigner

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
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
import com.vitorpamplona.quartz.utils.Hex
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
        Log.d(Amber.TAG, "Querying $uri has context ${context != null}")

        while (Amber.instance.isStartingAppState.value) {
            Log.d(Amber.TAG, "Waiting for Amber to start")
            Thread.sleep(1000)
        }

        val appId = BuildConfig.APPLICATION_ID
        val packageName = if (sortOrder.isNullOrBlank()) {
            callingPackage
        } else {
            if (Hex.isHex(sortOrder)) {
                sortOrder
            }
            null
        }
        if (packageName == null) {
            Log.d(Amber.TAG, "No package name")
            return null
        }
        return try {
            when (uri.toString()) {
                "content://$appId.SIGN_MESSAGE" -> {
                    val message = projection?.first()
                    if (message == null) {
                        Log.d(Amber.TAG, "No message")
                        return null
                    }
                    val npub = IntentUtils.parsePubKey(projection[2])
                    if (npub == null) {
                        Log.d(Amber.TAG, "No npub")
                        return null
                    }
                    if (!LocalPreferences.containsAccount(context!!, npub)) {
                        Log.d(Amber.TAG, "No account")
                        return null
                    }
                    val account = LocalPreferences.loadFromEncryptedStorageSync(context!!, npub)
                    if (account == null) {
                        Log.d(Amber.TAG, "No account from storage")
                        return null
                    }
                    val database = Amber.instance.getDatabase(account.npub)
                    val permission =
                        database
                            .applicationDao()
                            .getPermission(
                                packageName,
                                "SIGN_MESSAGE",
                            )
                    val signPolicy = database.applicationDao().getSignPolicy(packageName)
                    val isRemembered = isRemembered(signPolicy, permission)
                    if (isRemembered == null) {
                        return null
                    }
                    if (!isRemembered) {
                        scope.launch {
                            database.applicationDao().addHistory(
                                HistoryEntity2(
                                    0,
                                    packageName,
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
                                packageName,
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
                    val json = projection?.first()
                    if (json == null) {
                        Log.d(Amber.TAG, "No json")
                        return null
                    }
                    val npub = IntentUtils.parsePubKey(projection[2])
                    if (npub == null) {
                        Log.d(Amber.TAG, "No npub")
                        return null
                    }
                    if (!LocalPreferences.containsAccount(context!!, npub)) {
                        Log.d(Amber.TAG, "No account")
                        return null
                    }
                    val account = LocalPreferences.loadFromEncryptedStorageSync(context!!, npub)
                    if (account == null) {
                        Log.d(Amber.TAG, "No account from storage")
                        return null
                    }
                    val event = try {
                        IntentUtils.getUnsignedEvent(json, account)
                    } catch (e: Exception) {
                        Log.d(Amber.TAG, "Failed to parse event from $packageName", e)
                        return null
                    }

                    val database = Amber.instance.getDatabase(account.npub)
                    var permission =
                        database
                            .applicationDao()
                            .getPermission(
                                packageName,
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
                                        packageName,
                                        "NIP",
                                        nipNumber,
                                    )
                            }
                        }
                    }
                    val signPolicy = database.applicationDao().getSignPolicy(packageName)
                    val isRemembered = isRemembered(signPolicy, permission)
                    if (isRemembered == null) {
                        return null
                    }
                    if (!isRemembered) {
                        scope.launch {
                            database.applicationDao().addHistory(
                                HistoryEntity2(
                                    0,
                                    packageName,
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

                    scope.launch {
                        database.applicationDao().addHistory(
                            HistoryEntity2(
                                0,
                                packageName,
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
                    val content = projection?.first() ?: return null
                    val npub = IntentUtils.parsePubKey(projection[2]) ?: return null
                    if (!LocalPreferences.containsAccount(context!!, npub)) return null
                    val stringType = uri.toString().replace("content://$appId.", "")
                    val pubkey = projection[1]
                    val account = LocalPreferences.loadFromEncryptedStorageSync(context!!, npub) ?: return null
                    val database = Amber.instance.getDatabase(account.npub)
                    var permission =
                        database
                            .applicationDao()
                            .getPermission(
                                packageName,
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
                                        packageName,
                                        "NIP",
                                        it,
                                    )
                        }
                    }
                    val signPolicy = database.applicationDao().getSignPolicy(packageName)
                    val isRemembered = isRemembered(signPolicy, permission)
                    if (isRemembered == null) {
                        return null
                    }
                    if (!isRemembered) {
                        scope.launch {
                            database.applicationDao().addHistory(
                                HistoryEntity2(
                                    0,
                                    packageName,
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
                                        packageName,
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
                                packageName,
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
                    val npub = if (projection != null && projection.isNotEmpty()) IntentUtils.parsePubKey(projection[0]) else null
                    val account = if (npub != null) {
                        LocalPreferences.loadFromEncryptedStorageSync(context!!, npub)
                    } else {
                        LocalPreferences.allSavedAccounts(context!!).firstNotNullOfOrNull { accountInfo ->
                            val localDatabase = Amber.instance.getDatabase(accountInfo.npub)
                            val hasAccount = localDatabase.applicationDao().getByKeySync(packageName) != null
                            if (hasAccount) {
                                LocalPreferences.loadFromEncryptedStorageSync(context!!, accountInfo.npub)
                            } else {
                                null
                            }
                        }
                    }
                    if (account == null) {
                        return null
                    }
                    val database = Amber.instance.getDatabase(account.npub)
                    val permission =
                        database
                            .applicationDao()
                            .getPermission(
                                packageName,
                                "GET_PUBLIC_KEY",
                            )

                    val signPolicy = database.applicationDao().getSignPolicy(packageName)
                    val isRemembered = isRemembered(signPolicy, permission)
                    if (isRemembered == null) {
                        return null
                    }
                    if (!isRemembered) {
                        scope.launch {
                            database.applicationDao().addHistory(
                                HistoryEntity2(
                                    0,
                                    packageName,
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
                                packageName,
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
            scope.launch {
                LocalPreferences.allSavedAccounts(context!!).forEach { accInfo ->
                    val database = Amber.instance.getDatabase(accInfo.npub)
                    database.applicationDao().insertLog(
                        LogEntity(
                            0,
                            packageName,
                            uri.toString().replace("content://$appId.", ""),
                            e.message ?: "Error from $callingPackage $uri",
                            System.currentTimeMillis(),
                        ),
                    )
                }
            }
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

    fun isRemembered(signPolicy: Int?, permission: ApplicationPermissionsEntity?): Boolean? {
        val rejectUntil = permission?.rejectUntil ?: 0
        val acceptUntil = permission?.acceptUntil ?: 0
        if (signPolicy == 2) {
            return true
        }
        if (rejectUntil == 0L && acceptUntil == 0L) return null
        return if (rejectUntil > TimeUtils.now() && rejectUntil > 0 && permission?.acceptable == false) {
            false
        } else if (acceptUntil > TimeUtils.now() && acceptUntil > 0 && permission?.acceptable == true) {
            true
        } else {
            null
        }
    }
}

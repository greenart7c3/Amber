package com.greenart7c3.nostrsigner

import android.content.Context
import android.util.Log
import com.greenart7c3.nostrsigner.database.HistoryDatabase
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.database.LogDatabase
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.kindToNip
import com.greenart7c3.nostrsigner.models.permissionTypeFromContent
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.RelayUrlUtils
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Shared signing logic backing both request paths that reach the signer
 * synchronously: the [SignerProvider] ContentProvider and the [SignerService]
 * AIDL bound service.
 *
 * Each operation queries the per-account permission database, records history,
 * and either performs the operation or reports that explicit user approval is
 * still required. The two transports differ only in how they marshal the
 * returned [SignResult] (cursor columns vs. a [android.os.Bundle]).
 */
object SignerCore {
    private val scope get() = Amber.instance.applicationIOScope

    /** Outcome of a synchronous signer operation. `null` (not modelled here) means error. */
    sealed interface SignResult {
        /** No auto-accept rule matched; the request must be confirmed by the user. */
        data object Rejected : SignResult

        /** Operation completed. Field meanings mirror the legacy cursor columns. */
        data class Reply(
            val signature: String?,
            val event: String?,
            val result: String?,
        ) : SignResult
    }

    fun signMessage(
        context: Context,
        packageName: String,
        message: String,
        npubInput: String,
    ): SignResult? {
        val npub = IntentUtils.parsePubKey(npubInput) ?: run {
            Log.d(Amber.TAG, "No npub")
            return null
        }
        val account = LocalPreferences.loadFromEncryptedStorageSync(context, npub) ?: run {
            Log.d(Amber.TAG, "No account from storage")
            return null
        }
        val historyDatabase = Amber.instance.getHistoryDatabase(account.npub)
        val permDao = Amber.instance.dao(account.npub)
        val permission = permDao.getPermission(packageName, "SIGN_MESSAGE")
        val signPolicy = permDao.getSignPolicy(packageName)
        val isRemembered = IntentUtils.isRemembered(signPolicy, permission) ?: return null
        if (!isRemembered) {
            recordHistory(historyDatabase, account.npub, packageName, "SIGN_MESSAGE", null, false, message)
            return SignResult.Rejected
        }

        val result = runBlocking { account.signString(message) }
        recordHistory(historyDatabase, account.npub, packageName, "SIGN_MESSAGE", null, true, message)
        return SignResult.Reply(result, result, result)
    }

    fun signEvent(
        context: Context,
        packageName: String,
        json: String,
        npubInput: String,
    ): SignResult? {
        val npub = IntentUtils.parsePubKey(npubInput) ?: run {
            Log.d(Amber.TAG, "No npub")
            return null
        }
        val account = LocalPreferences.loadFromEncryptedStorageSync(context, npub) ?: run {
            Log.d(Amber.TAG, "No account from storage")
            return null
        }
        val event = try {
            IntentUtils.getUnsignedEvent(json, account)
        } catch (e: Exception) {
            Log.d(Amber.TAG, "Failed to parse event from $packageName", e)
            return null
        }

        val historyDatabase = Amber.instance.getHistoryDatabase(account.npub)
        val permDao = Amber.instance.dao(account.npub)

        // For kind 22242 (NIP-42 relay auth), extract relay host once for both whitelist and permission checks
        val relayHost = if (event.kind == 22242) {
            RelayUrlUtils.extractHostAndPort(AmberEvent.relay(event))
        } else {
            ""
        }

        val whitelistAutoAccept = if (event.kind == 22242) {
            val authWhitelist = Amber.instance.settings.authWhitelist
            when {
                authWhitelist.isEmpty() -> false
                relayHost in authWhitelist -> true
                else -> {
                    recordHistory(historyDatabase, account.npub, packageName, "SIGN_EVENT", event.kind, false, event.toJson())
                    return SignResult.Rejected
                }
            }
        } else {
            false
        }

        var permission = if (event.kind == 22242) {
            // Kind 22242 = relay client auth (NIP-42): check relay-specific permission first
            permDao.getPermissionForRelay(packageName, "SIGN_EVENT", 22242, relayHost)
                ?: permDao.getWildcardRelayPermission(packageName, "SIGN_EVENT", 22242)
        } else {
            permDao.getPermission(packageName, "SIGN_EVENT", event.kind)
        }
        if (permission == null && event.kind != 22242) {
            event.kind.kindToNip()?.let {
                val nipNumber = it.toIntOrNull()
                permission = if (nipNumber == null) {
                    null
                } else {
                    permDao.getPermission(packageName, "NIP", nipNumber)
                }
            }
        }
        val signPolicy = permDao.getSignPolicy(packageName)
        val isRemembered = whitelistAutoAccept || IntentUtils.isRemembered(signPolicy, permission) ?: return null
        if (!isRemembered) {
            recordHistory(historyDatabase, account.npub, packageName, "SIGN_EVENT", event.kind, false, event.toJson())
            return SignResult.Rejected
        }

        val signedEvent = account.signSync<Event>(event.createdAt, event.kind, event.tags, event.content)
        recordHistory(historyDatabase, account.npub, packageName, "SIGN_EVENT", event.kind, true, signedEvent.toJson())

        val signature =
            if (event.kind == LnZapRequestEvent.KIND &&
                event.tags.any { tag -> tag.any { t -> t == "anon" } }
            ) {
                signedEvent.toJson()
            } else {
                signedEvent.sig
            }
        return SignResult.Reply(signature, signedEvent.toJson(), signature)
    }

    fun encryptOrDecrypt(
        context: Context,
        packageName: String,
        type: SignerType,
        content: String,
        pubKey: String,
        npubInput: String,
    ): SignResult? {
        val npub = IntentUtils.parsePubKey(npubInput) ?: return null
        val stringType = type.toString()
        val account = LocalPreferences.loadFromEncryptedStorageSync(context, npub) ?: return null
        val logDatabase = Amber.instance.getLogDatabase(account.npub)
        val historyDatabase = Amber.instance.getHistoryDatabase(account.npub)
        val permDao = Amber.instance.dao(account.npub)

        val isEncrypt = type == SignerType.NIP04_ENCRYPT || type == SignerType.NIP44_ENCRYPT

        // For ENCRYPT: classify plaintext input; for DECRYPT: perform operation first then classify result
        val result =
            if (isEncrypt) {
                null // defer until after permission check
            } else {
                try {
                    runBlocking {
                        AmberUtils.encryptOrDecryptData(content, type, account, pubKey)
                            ?: "Could not decrypt the message"
                    }
                } catch (e: Exception) {
                    recordLog(logDatabase, packageName, stringType, e.message ?: "Could not decrypt the message")
                    "Could not decrypt the message"
                }
            }

        // Classify the content to determine EncryptedDataKind-based permission type
        val classifyContent = if (isEncrypt) content else (result ?: content)
        val permType = permissionTypeFromContent(classifyContent, isEncrypt, type)

        var permission = permDao.getPermission(packageName, permType)
        if (permission == null) {
            permission = permDao.getPermission(packageName, type.toString())
        }
        if (permission == null) {
            val nip = when (type) {
                SignerType.NIP04_DECRYPT, SignerType.NIP04_ENCRYPT -> 4
                SignerType.NIP44_DECRYPT, SignerType.NIP44_ENCRYPT -> 44
                else -> null
            }
            nip?.let {
                permission = permDao.getPermission(packageName, "NIP", it)
            }
        }
        val signPolicy = permDao.getSignPolicy(packageName)
        val isRemembered = IntentUtils.isRemembered(signPolicy, permission) ?: return null
        if (!isRemembered) {
            recordHistory(historyDatabase, account.npub, packageName, stringType, null, false, content)
            return SignResult.Rejected
        }

        // For encrypt: perform the operation now after permission is confirmed
        val finalResult =
            result ?: try {
                runBlocking {
                    AmberUtils.encryptOrDecryptData(content, type, account, pubKey)
                        ?: "Could not decrypt the message"
                }
            } catch (e: Exception) {
                recordLog(logDatabase, packageName, stringType, e.message ?: "Could not decrypt the message")
                "Could not decrypt the message"
            }

        recordHistory(historyDatabase, account.npub, packageName, stringType, null, true, if (!isEncrypt) finalResult else content)
        return SignResult.Reply(finalResult, finalResult, finalResult)
    }

    fun signPsbt(
        context: Context,
        packageName: String,
        psbtHex: String,
        npubInput: String,
    ): SignResult? {
        val npub = IntentUtils.parsePubKey(npubInput) ?: run {
            Log.d(Amber.TAG, "No npub")
            return null
        }
        val account = LocalPreferences.loadFromEncryptedStorageSync(context, npub) ?: run {
            Log.d(Amber.TAG, "No account from storage")
            return null
        }
        val historyDatabase = Amber.instance.getHistoryDatabase(account.npub)
        val permDao = Amber.instance.dao(account.npub)
        val permission = permDao.getPermission(packageName, "SIGN_PSBT")
        val signPolicy = permDao.getSignPolicy(packageName)
        val isRemembered = IntentUtils.isRemembered(signPolicy, permission) ?: return null
        if (!isRemembered) {
            recordHistory(historyDatabase, account.npub, packageName, "SIGN_PSBT", null, false, psbtHex)
            return SignResult.Rejected
        }

        val result = try {
            runBlocking { account.signPsbt(psbtHex) }
        } catch (e: Exception) {
            Log.d(Amber.TAG, "Failed to sign psbt", e)
            recordLog(Amber.instance.getLogDatabase(account.npub), packageName, "SIGN_PSBT", e.message ?: "Could not sign the psbt")
            return null
        }

        recordHistory(historyDatabase, account.npub, packageName, "SIGN_PSBT", null, true, psbtHex)
        return SignResult.Reply(result, result, result)
    }

    fun ping(
        context: Context,
        packageName: String,
        npubInput: String?,
    ): SignResult? {
        val npub = if (!npubInput.isNullOrBlank()) IntentUtils.parsePubKey(npubInput) else null
        val account = if (npub != null) {
            LocalPreferences.loadFromEncryptedStorageSync(context, npub)
        } else {
            LocalPreferences.allSavedAccounts(context).firstNotNullOfOrNull { accountInfo ->
                val localDatabase = Amber.instance.getDatabase(accountInfo.npub)
                val hasAccount = localDatabase.dao().getByKeySync(packageName) != null
                if (hasAccount) {
                    LocalPreferences.loadFromEncryptedStorageSync(context, accountInfo.npub)
                } else {
                    null
                }
            }
        } ?: return null

        val historyDatabase = Amber.instance.getHistoryDatabase(account.npub)
        val permDao = Amber.instance.dao(account.npub)
        val permission = permDao.getPermission(packageName, "PING")
        val signPolicy = permDao.getSignPolicy(packageName)
        val isRemembered = IntentUtils.isRemembered(signPolicy, permission) ?: return null
        if (!isRemembered) {
            recordHistory(historyDatabase, account.npub, packageName, "PING", null, false)
            return SignResult.Rejected
        }

        recordHistory(historyDatabase, account.npub, packageName, "PING", null, true)
        return SignResult.Reply("pong", null, "pong")
    }

    /** Logs an unexpected error against every saved account, matching the legacy catch-all. */
    fun logError(
        context: Context,
        packageName: String,
        type: String,
        message: String,
    ) {
        scope.launch {
            LocalPreferences.allSavedAccounts(context).forEach { accInfo ->
                recordLog(Amber.instance.getLogDatabase(accInfo.npub), packageName, type, message)
            }
        }
    }

    private fun recordHistory(
        historyDatabase: HistoryDatabase,
        npub: String,
        packageName: String,
        type: String,
        kind: Int?,
        accepted: Boolean,
        content: String = "",
    ) {
        scope.launch {
            historyDatabase.dao().addHistory(
                listOf(
                    HistoryEntity(
                        0,
                        packageName,
                        type,
                        kind,
                        TimeUtils.now(),
                        accepted,
                        content = content,
                    ),
                ),
                npub,
            )
        }
    }

    private fun recordLog(
        logDatabase: LogDatabase,
        packageName: String,
        type: String,
        message: String,
    ) {
        scope.launch {
            logDatabase.dao().insertLog(
                LogEntity(
                    0,
                    packageName,
                    type,
                    message,
                    System.currentTimeMillis(),
                ),
            )
        }
    }
}

package com.greenart7c3.nostrsigner

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.encryptDecryptSignerTypes
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
 * Holds the signing/encryption logic that backs [SignerProvider].
 *
 * The same logic is reached two ways:
 *  - [SignerProvider] (a [android.content.ContentProvider]) for external IPC callers, and
 *  - in-process callers such as [com.greenart7c3.nostrsigner.service.EventNotificationConsumer]
 *    (the NIP-46 relay path), which call [query] directly instead of going through
 *    a same-app [android.content.ContentResolver] round-trip.
 */
object SignerProviderQuery {
    private val scope get() = Amber.instance.applicationIOScope

    private fun rejectedCursor(): Cursor = MatrixCursor(arrayOf("rejected")).also { it.addRow(arrayOf("true")) }

    /**
     * Capture the calling app's launcher icon/name onto its saved app while it is
     * visible to us. Dispatched off the binder thread so it adds no latency to the
     * signing response, and gated to run once per app (see [IntentUtils.persistNativeAppMetadata]).
     */
    private fun captureCallerMetadata(
        context: Context,
        callerPackageName: String?,
        account: Account,
    ) {
        val pkg = callerPackageName ?: return
        scope.launch {
            IntentUtils.persistNativeAppMetadata(context, account, pkg)
        }
    }

    /**
     * Runs a signer operation and returns its result as a [Cursor], mirroring the
     * column layout the [SignerProvider] ContentProvider exposes to external apps.
     *
     * @param context an application [Context] used for storage and PackageManager lookups.
     * @param requesterId who the request is attributed to for permission checks,
     *   history and logs. For IPC requests this is the caller's Android package name;
     *   for NIP-46 relay requests it is the client's pubkey (hex). This is always
     *   derived by Amber, never taken from caller-supplied arguments, so external
     *   apps cannot impersonate another requester.
     * @param callerPackageName the installed Android package whose launcher icon/name
     *   should be captured onto the saved app, or null when there is no installed
     *   caller (e.g. NIP-46 relay requests).
     * @param operationUri identifies the operation to run, e.g. `content://<appId>.SIGN_EVENT`.
     * @param arguments the operation's input values; their meaning depends on
     *   [operationUri] (e.g. for SIGN_EVENT: [0] = event json, [2] = npub). See callers.
     */
    fun query(
        context: Context,
        requesterId: String,
        callerPackageName: String?,
        operationUri: Uri,
        arguments: Array<String>?,
    ): Cursor? {
        AmberLog.d(Amber.TAG, "Querying $operationUri")

        val appId = BuildConfig.APPLICATION_ID
        val uriString = operationUri.toString()
        return try {
            when (uriString) {
                "content://$appId.SIGN_EVENT" -> {
                    val json = arguments?.first()
                    if (json == null) {
                        AmberLog.d(Amber.TAG, "No json")
                        return null
                    }
                    val npub = IntentUtils.parsePubKey(arguments[2])
                    if (npub == null) {
                        AmberLog.d(Amber.TAG, "No npub")
                        return null
                    }
                    val account = LocalPreferences.loadFromEncryptedStorageSync(context, npub)
                    if (account == null) {
                        AmberLog.d(Amber.TAG, "No account from storage")
                        return null
                    }
                    captureCallerMetadata(context, callerPackageName, account)
                    val event = try {
                        IntentUtils.getUnsignedEvent(json, account)
                    } catch (e: Exception) {
                        AmberLog.d(Amber.TAG, "Failed to parse event from $requesterId", e)
                        return null
                    }

                    val historyDatabase = Amber.instance.getHistoryDatabase(account.npub)
                    val permDao = Amber.instance.dao(account.npub)

                    // For kind 22242 (NIP-42 relay auth), extract relay host once for the
                    // whitelist gate and the requester-scoped permission checks below.
                    val relayHost = if (event.kind == 22242) {
                        RelayUrlUtils.extractHostAndPort(AmberEvent.relay(event))
                    } else {
                        ""
                    }

                    // The auth whitelist is only a relay constraint: when non-empty, relays
                    // outside the list are auto-rejected. Membership never authorizes signing
                    // by itself — a requester-scoped permission (or user prompt) is still
                    // required, otherwise any app/NIP-46 client could silently obtain
                    // relay-auth signatures (GHSA-vx4h-56qj-wcp7).
                    if (event.kind == 22242) {
                        val authWhitelist = Amber.instance.settings.authWhitelist
                        if (authWhitelist.isNotEmpty() && relayHost !in authWhitelist) {
                            scope.launch {
                                historyDatabase.dao().addHistory(
                                    listOf(
                                        HistoryEntity(
                                            0,
                                            requesterId,
                                            uriString.replace("content://$appId.", ""),
                                            event.kind,
                                            TimeUtils.now(),
                                            false,
                                            content = event.toJson(),
                                        ),
                                    ),
                                    account.npub,
                                )
                            }
                            return rejectedCursor()
                        }
                    }

                    var permission = if (event.kind == 22242) {
                        // Kind 22242 = relay client auth (NIP-42): check relay-specific permission first
                        permDao.getPermissionForRelay(requesterId, "SIGN_EVENT", 22242, relayHost)
                            ?: permDao.getWildcardRelayPermission(requesterId, "SIGN_EVENT", 22242)
                    } else {
                        permDao
                            .getPermission(
                                requesterId,
                                "SIGN_EVENT",
                                event.kind,
                            )
                    }
                    if (permission == null && event.kind != 22242) {
                        event.kind.kindToNip()?.let {
                            val nipNumber = it.toIntOrNull()
                            permission = if (nipNumber == null) {
                                null
                            } else {
                                permDao
                                    .getPermission(
                                        requesterId,
                                        "NIP",
                                        nipNumber,
                                    )
                            }
                        }
                    }
                    val signPolicy = permDao.getSignPolicy(requesterId)
                    val isRemembered = IntentUtils.isRemembered(signPolicy, permission) ?: return null
                    if (!isRemembered) {
                        scope.launch {
                            historyDatabase.dao().addHistory(
                                listOf(
                                    HistoryEntity(
                                        0,
                                        requesterId,
                                        uriString.replace("content://$appId.", ""),
                                        event.kind,
                                        TimeUtils.now(),
                                        false,
                                        content = event.toJson(),
                                    ),
                                ),
                                account.npub,
                            )
                        }

                        val cursor =
                            MatrixCursor(arrayOf("rejected")).also {
                                it.addRow(arrayOf("true"))
                            }

                        return cursor
                    }

                    val signedEvent = account.signSync<Event>(event.createdAt, event.kind, event.tags, event.content)

                    scope.launch {
                        historyDatabase.dao().addHistory(
                            listOf(
                                HistoryEntity(
                                    0,
                                    requesterId,
                                    "SIGN_EVENT",
                                    event.kind,
                                    TimeUtils.now(),
                                    true,
                                    content = signedEvent.toJson(),
                                ),
                            ),
                            account.npub,
                        )
                    }

                    val cursor =
                        MatrixCursor(arrayOf("signature", "event", "result")).also {
                            val signature =
                                if (event.kind == LnZapRequestEvent.KIND &&
                                    event.tags.any { tag ->
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
                "content://$appId.NIP44_V3_DECRYPT",
                "content://$appId.NIP44_V3_ENCRYPT",
                "content://$appId.DECRYPT_ZAP_EVENT",
                -> {
                    val content = arguments?.first() ?: return null
                    val npub = IntentUtils.parsePubKey(arguments[2]) ?: return null
                    val stringType = uriString.replace("content://$appId.", "")
                    val pubkey = arguments[1]
                    val account = LocalPreferences.loadFromEncryptedStorageSync(context, npub) ?: return null
                    captureCallerMetadata(context, callerPackageName, account)
                    val logDatabase = Amber.instance.getLogDatabase(account.npub)
                    val historyDatabase = Amber.instance.getHistoryDatabase(account.npub)
                    val permDao = Amber.instance.dao(account.npub)
                    val type =
                        when (stringType) {
                            "NIP04_DECRYPT" -> SignerType.NIP04_DECRYPT
                            "NIP44_DECRYPT" -> SignerType.NIP44_DECRYPT
                            "NIP04_ENCRYPT" -> SignerType.NIP04_ENCRYPT
                            "NIP44_ENCRYPT" -> SignerType.NIP44_ENCRYPT
                            "NIP44_V3_DECRYPT" -> SignerType.NIP44_V3_DECRYPT
                            "NIP44_V3_ENCRYPT" -> SignerType.NIP44_V3_ENCRYPT
                            "DECRYPT_ZAP_EVENT" -> SignerType.DECRYPT_ZAP_EVENT
                            else -> null
                        } ?: return null

                    val isEncrypt = type == SignerType.NIP04_ENCRYPT ||
                        type == SignerType.NIP44_ENCRYPT ||
                        type == SignerType.NIP44_V3_ENCRYPT
                    val isV3 = type == SignerType.NIP44_V3_ENCRYPT || type == SignerType.NIP44_V3_DECRYPT

                    // V3 carries kind and scope as arguments[3] and arguments[4].
                    // A missing/invalid kind is a malformed request: auto-reject
                    // instead of prompting the user.
                    val (v3Kind, v3Scope) = if (isV3) {
                        val kindStr = arguments.getOrNull(3)
                        val scopeStr = arguments.getOrNull(4) ?: ""
                        val parsedKind = kindStr?.toIntOrNull()
                        if (parsedKind == null) {
                            AmberLog.d(Amber.TAG, "NIP-44 v3 request missing/invalid kind")
                            return rejectedCursor()
                        }
                        parsedKind to scopeStr
                    } else {
                        null to ""
                    }

                    // Pre-authorization (GHSA-8844-q5vh-9j8f, I1): do not invoke
                    // the decrypt key before we know the caller has *any*
                    // relevant permission, otherwise an unauthorized caller
                    // triggers a key-use / timing / error oracle even though
                    // the result is withheld. If no relevant grant exists and
                    // the per-app signPolicy is not "accept all" (== 2),
                    // short-circuit: the existing no-permission behavior is to
                    // return null (relay path → prompt; IPC path → caller
                    // surfaces its own approval flow), so returning null here
                    // is externally identical, but the private key is never
                    // touched for an unknown caller. The classified-specific
                    // grant case (e.g. a grant for DECRYPT_EVENT only) is
                    // preserved: ANY row of a relevant type counts as "has a
                    // relevant grant", so we still proceed to decrypt +
                    // classify, and the final per-type check below honors
                    // accept/reject precisely.
                    if (!isEncrypt) {
                        val preSignPolicy = permDao.getSignPolicy(requesterId)
                        val preAuthorized = preSignPolicy == 2 ||
                            hasAnyDecryptPermission(permDao, requesterId, type, v3Kind, stringType)
                        if (!preAuthorized) {
                            AmberLog.d(
                                Amber.TAG,
                                "Skipping decrypt for $requesterId: no relevant permission and signPolicy != 2 (I1 pre-auth)",
                            )
                            return null
                        }
                    }

                    // For ENCRYPT: classify plaintext input; for DECRYPT: perform operation first then classify result
                    val result =
                        if (isEncrypt) {
                            null // defer until after permission check
                        } else {
                            try {
                                runBlocking {
                                    AmberUtils.encryptOrDecryptData(
                                        content,
                                        type,
                                        account,
                                        pubkey,
                                        v3Kind,
                                        v3Scope,
                                    ) ?: "Could not decrypt the message"
                                }
                            } catch (e: Exception) {
                                scope.launch {
                                    logDatabase.dao().insertLogIfEnabled(
                                        LogEntity(
                                            0,
                                            requesterId,
                                            uriString.replace("content://$appId.", ""),
                                            e.message ?: "Could not decrypt the message",
                                            System.currentTimeMillis(),
                                        ),
                                    )
                                }
                                // A V3 decrypt that throws cannot succeed (wrong
                                // version/context, bad MAC, corrupt padding, ...):
                                // reject it rather than prompting the user.
                                if (isV3) return rejectedCursor()
                                "Could not decrypt the message"
                            }
                        }

                    // Permission lookup. V3 grants are scoped by (packageName,
                    // SignerType, kind); fall back to a kind=null "all kinds"
                    // grant. V3 grants do NOT satisfy V2 requests and vice versa.
                    var permission = if (isV3) {
                        // V3 grants are kind-scoped; fall back to the explicit
                        // "all kinds" (kind IS NULL) grant only, never to any
                        // other kind — otherwise e.g. a kind-A reject would
                        // leak to a kind-B request.
                        permDao.getPermission(requesterId, type.toString(), v3Kind!!)
                            ?: permDao.getPermissionAllKinds(requesterId, type.toString())
                    } else {
                        // Classify the content to determine EncryptedDataKind-based permission type
                        val classifyContent = if (isEncrypt) content else (result ?: content)
                        val permType = permissionTypeFromContent(classifyContent, isEncrypt, type)
                        permDao.getPermission(requesterId, permType)
                            ?: permDao.getPermission(requesterId, type.toString())
                    }
                    if (permission == null && !isV3) {
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
                                permDao
                                    .getPermission(
                                        requesterId,
                                        "NIP",
                                        it,
                                    )
                        }
                    }
                    val signPolicy = permDao.getSignPolicy(requesterId)
                    val isRemembered = IntentUtils.isRemembered(signPolicy, permission) ?: return null
                    if (!isRemembered) {
                        // A rejected encrypt request was never performed, so no
                        // ciphertext exists — store nothing rather than leaking the
                        // plaintext input. Decrypt requests carry their ciphertext as
                        // input, so persist it and decrypt on demand.
                        val rejectedContent = if (isEncrypt) "" else content
                        scope.launch {
                            historyDatabase.dao().addHistory(
                                listOf(
                                    HistoryEntity(
                                        0,
                                        requesterId,
                                        uriString.replace("content://$appId.", ""),
                                        v3Kind,
                                        TimeUtils.now(),
                                        false,
                                        content = rejectedContent,
                                        encryptionPubKey = if (rejectedContent.isNotEmpty() && type in encryptDecryptSignerTypes && type != SignerType.DECRYPT_ZAP_EVENT) pubkey else "",
                                        encryptionScope = if (isV3) v3Scope else "",
                                    ),
                                ),
                                account.npub,
                            )
                        }

                        val cursor =
                            MatrixCursor(arrayOf("rejected")).also {
                                it.addRow(arrayOf("true"))
                            }

                        return cursor
                    }

                    // For encrypt: perform the operation now after permission is confirmed
                    val finalResult =
                        result ?: try {
                            runBlocking {
                                AmberUtils.encryptOrDecryptData(
                                    content,
                                    type,
                                    account,
                                    pubkey,
                                    v3Kind,
                                    v3Scope,
                                ) ?: "Could not decrypt the message"
                            }
                        } catch (e: Exception) {
                            scope.launch {
                                logDatabase.dao().insertLogIfEnabled(
                                    LogEntity(
                                        0,
                                        requesterId,
                                        uriString.replace("content://$appId.", ""),
                                        e.message ?: "Could not decrypt the message",
                                        System.currentTimeMillis(),
                                    ),
                                )
                            }
                            "Could not decrypt the message"
                        }

                    // Persist the encrypted form (ciphertext), never the plaintext:
                    // encrypt requests store their ciphertext output (`finalResult`),
                    // decrypt requests store the ciphertext input (`content`). The
                    // plaintext is recovered on demand in the activity/history UI.
                    val historyContent = if (isEncrypt) finalResult else content
                    scope.launch {
                        historyDatabase.dao().addHistory(
                            listOf(
                                HistoryEntity(
                                    0,
                                    requesterId,
                                    uriString.replace("content://$appId.", ""),
                                    v3Kind,
                                    TimeUtils.now(),
                                    true,
                                    content = historyContent,
                                    encryptionPubKey = if (type in encryptDecryptSignerTypes && type != SignerType.DECRYPT_ZAP_EVENT) pubkey else "",
                                    encryptionScope = if (isV3) v3Scope else "",
                                ),
                            ),
                            account.npub,
                        )
                    }

                    val cursor = MatrixCursor(arrayOf("signature", "event", "result"))
                    cursor.addRow(arrayOf<Any>(finalResult, finalResult, finalResult))
                    return cursor
                }

                "content://$appId.SIGN_PSBT" -> {
                    val psbtHex = arguments?.first()
                    if (psbtHex == null) {
                        AmberLog.d(Amber.TAG, "No psbt")
                        return null
                    }
                    val npub = IntentUtils.parsePubKey(arguments[2])
                    if (npub == null) {
                        AmberLog.d(Amber.TAG, "No npub")
                        return null
                    }
                    val account = LocalPreferences.loadFromEncryptedStorageSync(context, npub)
                    if (account == null) {
                        AmberLog.d(Amber.TAG, "No account from storage")
                        return null
                    }
                    captureCallerMetadata(context, callerPackageName, account)
                    val historyDatabase = Amber.instance.getHistoryDatabase(account.npub)
                    val permDao = Amber.instance.dao(account.npub)
                    val permission =
                        permDao
                            .getPermission(
                                requesterId,
                                "SIGN_PSBT",
                            )
                    val signPolicy = permDao.getSignPolicy(requesterId)
                    val isRemembered = IntentUtils.isRemembered(signPolicy, permission) ?: return null
                    if (!isRemembered) {
                        scope.launch {
                            historyDatabase.dao().addHistory(
                                listOf(
                                    HistoryEntity(
                                        0,
                                        requesterId,
                                        uriString.replace("content://$appId.", ""),
                                        null,
                                        TimeUtils.now(),
                                        false,
                                        content = psbtHex,
                                    ),
                                ),
                                account.npub,
                            )
                        }
                        val cursor =
                            MatrixCursor(arrayOf("rejected")).also {
                                it.addRow(arrayOf("true"))
                            }

                        return cursor
                    }

                    val result = try {
                        runBlocking { account.signPsbt(psbtHex) }
                    } catch (e: Exception) {
                        AmberLog.d(Amber.TAG, "Failed to sign psbt", e)
                        scope.launch {
                            val logDb = Amber.instance.getLogDatabase(account.npub)
                            logDb.dao().insertLogIfEnabled(
                                LogEntity(
                                    0,
                                    requesterId,
                                    "SIGN_PSBT",
                                    e.message ?: "Could not sign the psbt",
                                    System.currentTimeMillis(),
                                ),
                            )
                        }
                        return null
                    }

                    scope.launch {
                        historyDatabase.dao().addHistory(
                            listOf(
                                HistoryEntity(
                                    0,
                                    requesterId,
                                    "SIGN_PSBT",
                                    null,
                                    TimeUtils.now(),
                                    true,
                                    content = psbtHex,
                                ),
                            ),
                            account.npub,
                        )
                    }

                    val localCursor = MatrixCursor(arrayOf("signature", "event", "result")).also {
                        it.addRow(arrayOf(result, result, result))
                    }

                    return localCursor
                }
                "content://$appId.PING" -> {
                    val npub = if (arguments != null && arguments.isNotEmpty()) IntentUtils.parsePubKey(arguments[0]) else null
                    val account = if (npub != null) {
                        LocalPreferences.loadFromEncryptedStorageSync(context, npub)
                    } else {
                        LocalPreferences.allSavedAccounts(context).firstNotNullOfOrNull { accountInfo ->
                            val localDatabase = Amber.instance.getDatabase(accountInfo.npub)
                            val hasAccount = localDatabase.dao().getByKeySync(requesterId) != null
                            if (hasAccount) {
                                LocalPreferences.loadFromEncryptedStorageSync(context, accountInfo.npub)
                            } else {
                                null
                            }
                        }
                    }
                    if (account == null) {
                        return null
                    }
                    captureCallerMetadata(context, callerPackageName, account)
                    val historyDatabase = Amber.instance.getHistoryDatabase(account.npub)
                    val permDao = Amber.instance.dao(account.npub)
                    val permission =
                        permDao
                            .getPermission(
                                requesterId,
                                "PING",
                            )

                    val signPolicy = permDao.getSignPolicy(requesterId)
                    val isRemembered = IntentUtils.isRemembered(signPolicy, permission) ?: return null
                    if (!isRemembered) {
                        scope.launch {
                            historyDatabase.dao().addHistory(
                                listOf(
                                    HistoryEntity(
                                        0,
                                        requesterId,
                                        uriString.replace("content://$appId.", ""),
                                        null,
                                        TimeUtils.now(),
                                        false,
                                    ),
                                ),
                                account.npub,
                            )
                        }

                        val cursor =
                            MatrixCursor(arrayOf("rejected")).also {
                                it.addRow(arrayOf("true"))
                            }

                        return cursor
                    }

                    scope.launch {
                        historyDatabase.dao().addHistory(
                            listOf(
                                HistoryEntity(
                                    0,
                                    requesterId,
                                    uriString.replace("content://$appId.", ""),
                                    null,
                                    TimeUtils.now(),
                                    true,
                                ),
                            ),
                            account.npub,
                        )
                    }

                    val cursor = MatrixCursor(arrayOf("signature", "result"))
                    cursor.addRow(arrayOf<Any>("pong", "pong"))
                    return cursor
                }
                else -> null
            }
        } catch (e: Exception) {
            scope.launch {
                LocalPreferences.allSavedAccounts(context).forEach { accInfo ->
                    val database = Amber.instance.getLogDatabase(accInfo.npub)
                    database.dao().insertLogIfEnabled(
                        LogEntity(
                            0,
                            requesterId,
                            uriString.replace("content://$appId.", ""),
                            e.message ?: "Error from ${callerPackageName ?: requesterId} $operationUri",
                            System.currentTimeMillis(),
                        ),
                    )
                }
            }
            return null
        }
    }

    /**
     * I1 pre-authorization gate (GHSA-8844-q5vh-9j8f): returns true when the
     * requester has *any* permission row that could decide a decrypt of this
     * type — generic ([type.toString()]), any of the V2 classified content
     * types, the NIP-level grant, or (for V3) the kind-scoped / all-kinds rows.
     * Used to short-circuit a decrypt request from a totally unknown caller
     * *before* the private key is invoked, closing a key-use / timing / error
     * oracle. Does not inspect accept/reject state: a row that auto-rejects
     * still counts as "relevant" so the existing classify-and-decide path
     * below runs and returns [rejectedCursor] as appropriate.
     */
    private fun hasAnyDecryptPermission(
        permDao: ApplicationDao,
        requesterId: String,
        type: SignerType,
        v3Kind: Int?,
        stringType: String,
    ): Boolean {
        if (permDao.getPermission(requesterId, type.toString()) != null) return true

        if (v3Kind != null) {
            if (permDao.getPermission(requesterId, type.toString(), v3Kind) != null) return true
            if (permDao.getPermissionAllKinds(requesterId, type.toString()) != null) return true
        } else {
            // V2 classified content-type grants. The set of possible types
            // comes from permissionTypeFromContent / EncryptedDataKind.
            if (permDao.getPermission(requesterId, "DECRYPT_CLEAR_TEXT") != null) return true
            if (permDao.getPermission(requesterId, "DECRYPT_EVENT") != null) return true
            if (permDao.getPermission(requesterId, "DECRYPT_TAG_ARRAY") != null) return true
            if (permDao.getPermission(requesterId, "DECRYPT_ZAP_EVENT") != null) return true

            // NIP-level fallback grant (matches the lookup at line ~341-358).
            val nip = when (stringType) {
                "NIP04_DECRYPT" -> 4
                "NIP44_DECRYPT" -> 44
                else -> null
            }
            nip?.let {
                if (permDao.getPermission(requesterId, "NIP", it) != null) return true
            }
        }
        return false
    }
}

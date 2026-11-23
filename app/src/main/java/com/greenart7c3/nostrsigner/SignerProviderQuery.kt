package com.greenart7c3.nostrsigner

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.greenart7c3.nostrsigner.common.PermissionKey
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.hasAmbiguousRelayTags
import com.greenart7c3.nostrsigner.common.permissionLookupOrder
import com.greenart7c3.nostrsigner.commonsui.kindToNip
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.database.ApplicationDao
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.encryptDecryptSignerTypes
import com.greenart7c3.nostrsigner.models.permissionTypeFromContent
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.RelayUrlUtils
import com.greenart7c3.nostrsigner.service.isBlockedByAuthWhitelist
import com.greenart7c3.nostrsigner.service.isRemembered
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.service.parsePubKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
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
class SignerProviderQuery(
    private val settingsStore: SettingsStore,
    private val accountRepository: AccountRepository,
    private val scope: CoroutineScope,
) {

    private fun rejectedCursor(): Cursor = MatrixCursor(arrayOf("rejected")).also { it.addRow(arrayOf("true")) }

    /** A performed operation's answer: [result] in the signature and result columns, [event] in the event column. */
    private fun resultCursor(
        result: String,
        event: String = result,
    ): Cursor = MatrixCursor(arrayOf("signature", "event", "result")).also { it.addRow(arrayOf<Any>(result, event, result)) }

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
     *   [operationUri] (e.g. for SIGN_EVENT: [0] = event JSON, [2] = npub). See callers.
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
        // The operation name history and logs record, e.g. SIGN_EVENT.
        val operation = uriString.replace("content://$appId.", "")
        val caller = Caller(context, requesterId, callerPackageName, operation)
        return try {
            when (uriString) {
                "content://$appId.SIGN_EVENT" -> signEvent(caller, arguments)

                "content://$appId.NIP04_DECRYPT",
                "content://$appId.NIP44_DECRYPT",
                "content://$appId.NIP04_ENCRYPT",
                "content://$appId.NIP44_ENCRYPT",
                "content://$appId.NIP44_V3_DECRYPT",
                "content://$appId.NIP44_V3_ENCRYPT",
                "content://$appId.DECRYPT_ZAP_EVENT",
                -> encryptOrDecrypt(caller, arguments)

                "content://$appId.SIGN_PSBT" -> signPsbt(caller, arguments)

                "content://$appId.PING" -> ping(caller, arguments)

                else -> null
            }
        } catch (e: Exception) {
            scope.launch {
                SavedAccounts.all(context).forEach { accInfo ->
                    val database = accountRepository.getLogDatabase(accInfo.npub)
                    database.dao().insertLogIfEnabled(
                        LogEntity(
                            0,
                            requesterId,
                            operation,
                            e.message ?: "Error from ${callerPackageName ?: requesterId} $operationUri",
                            System.currentTimeMillis(),
                        ),
                    )
                }
            }
            null
        }
    }

    /** Who asked for [operation] (see [query]'s parameters). */
    private class Caller(
        val context: Context,
        val requesterId: String,
        val packageName: String?,
        val operation: String,
    )

    /** The saved account [npubArgument] names, with the caller's icon and name captured for it; null when there is none. */
    private fun requestAccount(
        caller: Caller,
        npubArgument: String,
    ): Account? {
        val npub = parsePubKey(npubArgument)
        if (npub == null) {
            AmberLog.d(Amber.TAG, "No npub")
            return null
        }
        val account = AccountStorage.loadSync(caller.context, npub)
        if (account == null) {
            AmberLog.d(Amber.TAG, "No account from storage")
            return null
        }
        captureCallerMetadata(caller.context, caller.packageName, account)
        return account
    }

    /** The remembered decision on the requester's [type] grant: true performs it silently, false refuses it, null leaves it to the user. */
    private fun isAllowed(
        account: Account,
        caller: Caller,
        type: String,
    ): Boolean? {
        val permDao = accountRepository.dao(account.npub)
        val permission = permDao.getPermission(caller.requesterId, type)
        return isRemembered(permDao.getSignPolicy(caller.requesterId), permission)
    }

    private fun history(
        caller: Caller,
        kind: Int?,
        accepted: Boolean,
        content: String = "",
    ) = HistoryEntity(0, caller.requesterId, caller.operation, kind, TimeUtils.now(), accepted, content = content)

    private fun recordHistory(
        account: Account,
        entry: HistoryEntity,
    ) {
        val historyDatabase = accountRepository.getHistoryDatabase(account.npub)
        scope.launch {
            historyDatabase.dao().addHistory(listOf(entry), account.npub)
        }
    }

    /** Records the refused request and answers "rejected". */
    private fun rejected(
        account: Account,
        entry: HistoryEntity,
    ): Cursor {
        recordHistory(account, entry)
        return rejectedCursor()
    }

    private fun logFailure(
        account: Account,
        caller: Caller,
        message: String,
    ) {
        val logDatabase = accountRepository.getLogDatabase(account.npub)
        scope.launch {
            logDatabase.dao().insertLogIfEnabled(LogEntity(0, caller.requesterId, caller.operation, message, System.currentTimeMillis()))
        }
    }

    /** SIGN_EVENT: [arguments] [0] = event JSON, [2] = npub. */
    private fun signEvent(
        caller: Caller,
        arguments: Array<String>?,
    ): Cursor? {
        val json = arguments?.first() ?: run {
            AmberLog.d(Amber.TAG, "No json")
            return null
        }
        val account = requestAccount(caller, arguments[2]) ?: return null
        val event = try {
            IntentUtils.getUnsignedEvent(json, account)
        } catch (e: Exception) {
            AmberLog.d(Amber.TAG, "Failed to parse event from ${caller.requesterId}", e)
            return null
        }
        val allowed = isSignAllowed(caller, account, event) ?: return null
        if (!allowed) return rejected(account, history(caller, event.kind, accepted = false, content = event.toJson()))

        val signedEvent = account.signSync<Event>(event.createdAt, event.kind, event.tags, event.content)
        recordHistory(account, history(caller, event.kind, accepted = true, content = signedEvent.toJson()))
        // An anonymous zap request is answered with the whole event: the client cannot rebuild it from the signature.
        val isAnonymousZap = event.kind == ZapRequestEvent.KIND && event.tags.any { tag -> tag.any { it == "anon" } }
        val signature = if (isAnonymousZap) signedEvent.toJson() else signedEvent.sig
        return resultCursor(signature, event = signedEvent.toJson())
    }

    /** Whether [event] may be signed silently: false refuses it, null leaves it to the user. */
    private fun isSignAllowed(
        caller: Caller,
        account: Account,
        event: Event,
    ): Boolean? {
        // For kind 22242 (NIP-42 relay auth), extract relay host once for the
        // whitelist gate and the requester-scoped permission checks below.
        val relayHost = if (event.kind == 22242) {
            RelayUrlUtils.extractHostAndPort(AmberEvent.relay(event))
        } else {
            ""
        }

        // Authorization below decides on the first relay tag alone; a second
        // relay tag would be signed without ever being granted. NIP-42
        // defines exactly one relay per auth event, so refuse the rest.
        if (hasAmbiguousRelayTags(event.kind, event.tags)) return false

        // The auth whitelist is only a relay constraint: when non-empty, relays
        // outside the list are auto-rejected. Membership never authorizes signing
        // by itself — a requester-scoped permission (or user prompt) is still
        // required, otherwise any app/NIP-46 client could silently obtain
        // relay-auth signatures (GHSA-vx4h-56qj-wcp7).
        if (event.kind == 22242 && isBlockedByAuthWhitelist(event.kind, relayHost, settingsStore.settings.authWhitelist)) return false

        val requesterId = caller.requesterId
        val permDao = accountRepository.dao(account.npub)
        val permission = if (event.kind == 22242) {
            // Kind 22242 = relay client auth (NIP-42): check relay-specific permission first
            permDao.getPermissionForRelay(requesterId, "SIGN_EVENT", 22242, relayHost)
                ?: permDao.getWildcardRelayPermission(requesterId, "SIGN_EVENT", 22242)
        } else {
            permDao.firstGrant(requesterId, permissionLookupOrder(SignerType.SIGN_EVENT, event.kind, signNip = event.kind.kindToNip()?.toIntOrNull()))
        }
        return isRemembered(permDao.getSignPolicy(requesterId), permission)
    }

    /** One encrypt or decrypt request; [v3Kind] and [v3Scope] are the NIP-44 v3 context (null and empty otherwise). */
    private class CryptoRequest(
        val type: SignerType,
        val content: String,
        val peerPubKey: String,
        val v3Kind: Int?,
        val v3Scope: String,
    ) {
        val isV3 get() = type == SignerType.NIP44_V3_ENCRYPT || type == SignerType.NIP44_V3_DECRYPT
    }

    /**
     * The NIP-04 / NIP-44 (v2 and v3) encrypt and decrypt operations and DECRYPT_ZAP_EVENT:
     * [arguments] [0] = content, [1] = peer pubkey, [2] = npub; for v3 also [3] = kind, [4] = scope.
     */
    private fun encryptOrDecrypt(
        caller: Caller,
        arguments: Array<String>?,
    ): Cursor? {
        val content = arguments?.first() ?: return null
        val account = requestAccount(caller, arguments[2]) ?: return null
        val type = cryptoSignerTypes[caller.operation] ?: return null
        val isV3 = type == SignerType.NIP44_V3_ENCRYPT || type == SignerType.NIP44_V3_DECRYPT
        // V3 carries kind and scope as arguments[3] and arguments[4].
        // A missing/invalid kind is a malformed request: auto-reject
        // instead of prompting the user.
        val v3Kind = if (isV3) arguments.getOrNull(3)?.toIntOrNull() else null
        if (isV3 && v3Kind == null) {
            AmberLog.d(Amber.TAG, "NIP-44 v3 request missing/invalid kind")
            return rejectedCursor()
        }
        val request = CryptoRequest(type, content, arguments[1], v3Kind, if (isV3) arguments.getOrNull(4) ?: "" else "")
        val isEncrypt = type == SignerType.NIP04_ENCRYPT || type == SignerType.NIP44_ENCRYPT || type == SignerType.NIP44_V3_ENCRYPT
        return if (isEncrypt) encrypt(caller, account, request) else decrypt(caller, account, request)
    }

    private fun encrypt(
        caller: Caller,
        account: Account,
        request: CryptoRequest,
    ): Cursor? {
        // Encrypt requests are classified by their plaintext input.
        val allowed = isCryptoAllowed(caller, account, request, plaintext = request.content, isEncrypt = true) ?: return null
        // A rejected encrypt request was never performed, so no
        // ciphertext exists — store nothing rather than leaking the
        // plaintext input.
        if (!allowed) return rejected(account, cryptoHistory(caller, request, accepted = false, content = ""))
        // Performed only now that the permission is confirmed.
        val ciphertext = runCrypto(caller, account, request) ?: COULD_NOT_DECRYPT
        // Persist the encrypted form (the ciphertext output), never the plaintext.
        recordHistory(account, cryptoHistory(caller, request, accepted = true, content = ciphertext))
        return resultCursor(ciphertext)
    }

    private fun decrypt(
        caller: Caller,
        account: Account,
        request: CryptoRequest,
    ): Cursor? {
        if (!isDecryptPreAuthorized(caller, account, request)) return null
        // Decrypt requests are performed first, then classified by their result.
        // A V3 decrypt that throws cannot succeed (wrong
        // version/context, bad MAC, corrupt padding, ...):
        // reject it rather than prompting the user.
        val plaintext = runCrypto(caller, account, request) ?: if (request.isV3) return rejectedCursor() else COULD_NOT_DECRYPT
        val allowed = isCryptoAllowed(caller, account, request, plaintext, isEncrypt = false) ?: return null
        // Decrypt requests carry their ciphertext as input: persist it (accepted
        // or not) and decrypt on demand in the activity/history UI.
        val entry = cryptoHistory(caller, request, accepted = allowed, content = request.content)
        return if (allowed) {
            recordHistory(account, entry)
            resultCursor(plaintext)
        } else {
            rejected(account, entry)
        }
    }

    /**
     * Pre-authorization (GHSA-8844-q5vh-9j8f, I1): do not invoke
     * the decrypt key before we know the caller has *any*
     * relevant permission, otherwise an unauthorized caller
     * triggers a key-use / timing / error oracle even though
     * the result is withheld. If no relevant grant exists and
     * the per-app signPolicy is not "accept all" (== 2),
     * short-circuit: the existing no-permission behavior is to
     * return null (relay path → prompt; IPC path → caller
     * surfaces its own approval flow), so returning null here
     * is externally identical, but the private key is never
     * touched for an unknown caller. The classified-specific
     * grant case (e.g. an accept for DECRYPT_EVENT only) is
     * preserved: any row of a relevant type that can accept
     * counts, so we still decrypt + classify, and the final
     * per-type check honors accept/reject precisely.
     * Rows that can only reject do not count: decrypting for
     * them could only end in "rejected" or "no grant", and
     * which one it is would tell the caller how the plaintext
     * classified. Returning null without decrypting keeps that
     * hidden; the intent flow still applies the rejection.
     */
    private fun isDecryptPreAuthorized(
        caller: Caller,
        account: Account,
        request: CryptoRequest,
    ): Boolean {
        val permDao = accountRepository.dao(account.npub)
        val preAuthorized = permDao.getSignPolicy(caller.requesterId) == 2 ||
            hasAnyAcceptingDecryptPermission(permDao, caller.requesterId, request.type, request.v3Kind, caller.operation)
        if (!preAuthorized) {
            AmberLog.d(
                Amber.TAG,
                "Skipping decrypt for ${caller.requesterId}: no relevant permission and signPolicy != 2 (I1 pre-auth)",
            )
        }
        return preAuthorized
    }

    /**
     * The remembered decision for [request] (see [isAllowed]). V3 grants do NOT
     * satisfy V2 requests and vice versa. The v3 context scope is shown in the
     * prompt but is not part of a grant: a remembered kind-K grant covers every
     * scope. V2 grants are per content type, classified from [plaintext] (the
     * encrypt input or the decrypt result).
     */
    private fun isCryptoAllowed(
        caller: Caller,
        account: Account,
        request: CryptoRequest,
        plaintext: String,
        isEncrypt: Boolean,
    ): Boolean? {
        val permDao = accountRepository.dao(account.npub)
        val contentType = if (request.isV3) null else permissionTypeFromContent(plaintext, isEncrypt, request.type)
        val permission = permDao.firstGrant(caller.requesterId, permissionLookupOrder(request.type, request.v3Kind, contentType))
        return isRemembered(permDao.getSignPolicy(caller.requesterId), permission)
    }

    /** Encrypts or decrypts [request]'s content; null (logged) when that throws. */
    // A ContentProvider query answers synchronously, so it blocks on the signer.
    @Suppress("RunBlocking")
    private fun runCrypto(
        caller: Caller,
        account: Account,
        request: CryptoRequest,
    ): String? = try {
        runBlocking {
            AmberUtils.encryptOrDecryptData(
                request.content,
                request.type,
                account,
                request.peerPubKey,
                request.v3Kind,
                request.v3Scope,
            ) ?: COULD_NOT_DECRYPT
        }
    } catch (e: Exception) {
        logFailure(account, caller, e.message ?: COULD_NOT_DECRYPT)
        null
    }

    /**
     * The history entry of an encrypt or decrypt request. [content] is always the
     * ciphertext; the counterparty key (for decrypting it on demand) is kept with
     * it, except for zap events and an empty rejected entry.
     */
    private fun cryptoHistory(
        caller: Caller,
        request: CryptoRequest,
        accepted: Boolean,
        content: String,
    ): HistoryEntity {
        val keepsPeerKey = (accepted || content.isNotEmpty()) && request.type in encryptDecryptSignerTypes && request.type != SignerType.DECRYPT_ZAP_EVENT
        return HistoryEntity(
            0,
            caller.requesterId,
            caller.operation,
            request.v3Kind,
            TimeUtils.now(),
            accepted,
            content = content,
            encryptionPubKey = if (keepsPeerKey) request.peerPubKey else "",
            encryptionScope = if (request.isV3) request.v3Scope else "",
        )
    }

    /** SIGN_PSBT: [arguments] [0] = PSBT hex, [2] = npub. */
    // A ContentProvider query answers synchronously, so it blocks on the signer.
    @Suppress("RunBlocking")
    private fun signPsbt(
        caller: Caller,
        arguments: Array<String>?,
    ): Cursor? {
        val psbtHex = arguments?.first() ?: run {
            AmberLog.d(Amber.TAG, "No psbt")
            return null
        }
        val account = requestAccount(caller, arguments[2]) ?: return null
        val allowed = isAllowed(account, caller, "SIGN_PSBT") ?: return null
        if (!allowed) return rejected(account, history(caller, null, accepted = false, content = psbtHex))

        val result = try {
            runBlocking { account.signPsbt(psbtHex) }
        } catch (e: Exception) {
            AmberLog.d(Amber.TAG, "Failed to sign psbt", e)
            logFailure(account, caller, e.message ?: "Could not sign the psbt")
            return null
        }
        recordHistory(account, history(caller, null, accepted = true, content = psbtHex))
        return resultCursor(result)
    }

    /** PING: [arguments] [0] = npub, optional; without it, the first account that knows the requester. */
    private fun ping(
        caller: Caller,
        arguments: Array<String>?,
    ): Cursor? {
        val account = pingAccount(caller, arguments) ?: return null
        captureCallerMetadata(caller.context, caller.packageName, account)
        val allowed = isAllowed(account, caller, "PING") ?: return null
        recordHistory(account, history(caller, null, accepted = allowed))
        return if (allowed) MatrixCursor(arrayOf("signature", "result")).also { it.addRow(arrayOf<Any>("pong", "pong")) } else rejectedCursor()
    }

    private fun pingAccount(
        caller: Caller,
        arguments: Array<String>?,
    ): Account? {
        val npub = if (!arguments.isNullOrEmpty()) parsePubKey(arguments[0]) else null
        if (npub != null) return AccountStorage.loadSync(caller.context, npub)
        return SavedAccounts.all(caller.context).firstNotNullOfOrNull { accountInfo ->
            val localDatabase = accountRepository.getDatabase(accountInfo.npub)
            val hasAccount = localDatabase.dao().getByKeySync(caller.requesterId) != null
            if (hasAccount) AccountStorage.loadSync(caller.context, accountInfo.npub) else null
        }
    }

    /**
     * I1 pre-authorization gate (GHSA-8844-q5vh-9j8f): returns true when the
     * requester has *any* permission row that could decide a decrypt of this
     * type — generic ([type.toString()]), any of the V2 classified content
     * types, the NIP-level grant, or (for V3) the kind-scoped / all-kinds rows.
     * Used to short-circuit a decrypt request *before* the private key is
     * invoked, closing a key-use / timing / error oracle. Only rows whose
     * active decision is accept count: a caller that holds nothing but
     * rejections (or always-ask rows) can never receive a result, and
     * decrypting for it would reveal the plaintext's classification through
     * the rejected-versus-null response.
     */
    private fun hasAnyAcceptingDecryptPermission(
        permDao: ApplicationDao,
        requesterId: String,
        type: SignerType,
        v3Kind: Int?,
        stringType: String,
    ): Boolean {
        val now = TimeUtils.now()
        // Looked up lazily, in this order, until one accepts.
        val lookups: List<() -> ApplicationPermissionsEntity?> = if (v3Kind != null) {
            // Kind-scoped like the final V3 lookup: the kind-agnostic
            // getPermission(key, type) would match a grant for any other kind,
            // and a kind-B request then decrypted only to answer "rejected" on
            // failure versus null on success, a ciphertext-validity oracle.
            listOf(
                { permDao.getPermission(requesterId, type.toString(), v3Kind) },
                { permDao.getPermissionAllKinds(requesterId, type.toString()) },
            )
        } else {
            // The generic grant, the V2 classified content-type grants (the set of
            // possible types comes from permissionTypeFromContent / EncryptedDataKind),
            // then the NIP-level fallback grant the final lookup also consults.
            val nip = when (stringType) {
                "NIP04_DECRYPT" -> 4
                "NIP44_DECRYPT" -> 44
                else -> null
            }
            listOf<() -> ApplicationPermissionsEntity?>(
                { permDao.getPermission(requesterId, type.toString()) },
                { permDao.getPermission(requesterId, "DECRYPT_CLEAR_TEXT") },
                { permDao.getPermission(requesterId, "DECRYPT_EVENT") },
                { permDao.getPermission(requesterId, "DECRYPT_TAG_ARRAY") },
                { permDao.getPermission(requesterId, "DECRYPT_ZAP_EVENT") },
            ) + listOfNotNull(nip?.let { { permDao.getPermission(requesterId, "NIP", it) } })
        }
        return lookups.any { it()?.activeDecision(now) == true }
    }

    private companion object {
        /** The answer to an encrypt or decrypt that produced nothing. */
        const val COULD_NOT_DECRYPT = "Could not decrypt the message"

        /** The encrypt and decrypt operations by name. */
        val cryptoSignerTypes = listOf(
            SignerType.NIP04_DECRYPT,
            SignerType.NIP44_DECRYPT,
            SignerType.NIP04_ENCRYPT,
            SignerType.NIP44_ENCRYPT,
            SignerType.NIP44_V3_DECRYPT,
            SignerType.NIP44_V3_ENCRYPT,
            SignerType.DECRYPT_ZAP_EVENT,
        ).associateBy { it.name }
    }
}

/**
 * The first stored grant of [order] (`permissionLookupOrder`). A kind-less key
 * is the kind IS NULL row: for NIP-44 v3 that is the "all kinds" grant.
 */
private fun ApplicationDao.firstGrant(requesterId: String, order: List<PermissionKey>): ApplicationPermissionsEntity? = order.firstNotNullOfOrNull { (type, kind) ->
    when {
        kind != null -> getPermission(requesterId, type, kind)
        type == SignerType.NIP44_V3_ENCRYPT.name || type == SignerType.NIP44_V3_DECRYPT.name -> getPermissionAllKinds(requesterId, type)
        else -> getPermission(requesterId, type)
    }
}

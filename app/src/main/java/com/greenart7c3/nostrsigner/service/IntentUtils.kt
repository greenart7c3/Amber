package com.greenart7c3.nostrsigner.service

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Browser
import android.widget.Toast
import androidx.collection.LruCache
import androidx.core.net.toUri
import com.greenart7c3.nostrsigner.AccountStorage
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.FailedMigrationException
import com.greenart7c3.nostrsigner.SavedAccounts
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.hasAmbiguousRelayTags
import com.greenart7c3.nostrsigner.common.sanitizeAppName
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.event_copied_to_the_clipboard
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.signature_copied_to_the_clipboard
import com.greenart7c3.nostrsigner.data.AccountRepository
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.models.PrivateZapEncryptedDataKind
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.models.decryptSignerTypes
import com.greenart7c3.nostrsigner.models.encryptDecryptSignerTypes
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.components.DecryptTypeScope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.utils.Hex
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object IntentUtils {
    private lateinit var accountRepository: AccountRepository
    private lateinit var appScope: CoroutineScope
    private lateinit var mainActivity: () -> Activity?

    /**
     * Installs the per-account databases, the background scope and the activity a result
     * is handed to; Amber's constructor calls it before an intent can arrive.
     */
    fun install(accounts: AccountRepository, scope: CoroutineScope, mainActivity: () -> Activity?) {
        accountRepository = accounts
        appScope = scope
        this.mainActivity = mainActivity
    }

    private val _intents = MutableStateFlow<ImmutableList<IntentData>>(persistentListOf())
    val intents = _intents.asStateFlow()

    private val capturedNativeAppMetadata = LruCache<String, Boolean>(256)

    internal fun clearNativeAppMetadataCache() {
        capturedNativeAppMetadata.evictAll()
    }

    /**
     * Captures a native (intent/ContentProvider) app's launcher icon and label
     * onto its saved [com.greenart7c3.nostrsigner.database.ApplicationEntity]
     * while the app is visible to us (i.e. it just sent a request). Other,
     * non-interactive screens (the applications list) can then show them without
     * the broad QUERY_ALL_PACKAGES permission — Android 11+ package visibility
     * otherwise hides installed apps that aren't currently interacting with us.
     *
     * Only edits an already-saved app and only fills blank name/icon, so it
     * effectively runs once per app and never overwrites existing values.
     */
    suspend fun persistNativeAppMetadata(
        context: Context,
        account: Account,
        packageName: String?,
    ) {
        if (packageName.isNullOrBlank()) return
        val cacheKey = "${account.npub}|$packageName"
        if (capturedNativeAppMetadata[cacheKey] == true) return
        val daos = accountRepository.daos(account.npub)
        val saved = daos.applicationDao.getByKey(packageName)?.application ?: return
        if (saved.name.isNotBlank() && saved.icon.isNotBlank()) {
            capturedNativeAppMetadata.put(cacheKey, true)
            return
        }

        val newIcon = saved.icon.ifBlank {
            AppIconStore.resolveIconPath(context, packageName) ?: ""
        }
        val newName = saved.name.ifBlank {
            runCatching {
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                sanitizeAppName(context.packageManager.getApplicationLabel(info).toString())
            }.getOrNull().orEmpty()
        }

        val finalName = newName.ifBlank { saved.name }
        if (finalName.isNotBlank() && newIcon.isNotBlank()) {
            capturedNativeAppMetadata.put(cacheKey, true)
        }
        if (newName == saved.name && newIcon == saved.icon) return
        daos.applicationWriteDao.updateNameAndIcon(packageName, finalName, newIcon)
    }

    data class InvalidIntentInfo(
        val id: String,
        val dataString: String?,
        val callingPackage: String?,
        val typeExtra: String?,
        val message: String,
        val stackTrace: String?,
        val timestampMs: Long = System.currentTimeMillis(),
    )

    private val _invalidIntents = MutableSharedFlow<InvalidIntentInfo>(
        replay = 1,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val invalidIntents = _invalidIntents.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun clearInvalidIntents() {
        _invalidIntents.resetReplayCache()
    }

    private fun emitInvalid(
        intent: Intent,
        packageName: String?,
        message: String,
        t: Throwable? = null,
    ) {
        _invalidIntents.tryEmit(
            InvalidIntentInfo(
                id = UUID.randomUUID().toString(),
                dataString = intent.dataString,
                callingPackage = packageName,
                typeExtra = intent.extras?.getString("type"),
                message = message,
                stackTrace = t?.stackTraceToString(),
            ),
        )
    }

    fun addAll(list: List<IntentData>) {
        _intents.update { current ->
            val existingKeys = current.mapTo(mutableSetOf()) { it.queueKey }
            val newList = list.filter { existingKeys.add(it.queueKey) }
            (current + newList).toPersistentList()
        }
    }

    fun removeAll(list: List<IntentData>) {
        _intents.update { current -> (current - list.toSet()).toPersistentList() }
    }

    fun clear() {
        _intents.value = persistentListOf()
    }

    private suspend fun getIntentDataWithoutExtras(
        data: String,
        intent: Intent,
        packageName: String?,
        route: String?,
        account: Account,
    ): IntentData? {
        val request = NostrSignerRequestParser.fromUri(data)
            ?: return getIntentDataFromIntent(intent, packageName, route, account)
        return buildIntentData(request, intent, packageName, route, account)
    }

    private suspend fun getIntentDataFromIntent(
        intent: Intent,
        packageName: String?,
        route: String?,
        account: Account,
    ): IntentData? {
        val request = NostrSignerRequestParser.fromExtras(intent.data?.toString() ?: "", packageName) { intent.extras?.getString(it) }
        return buildIntentData(request, intent, packageName, route, account)
    }

    private suspend fun buildIntentData(
        request: NostrSignerRequest,
        intent: Intent,
        packageName: String?,
        route: String?,
        account: Account,
    ): IntentData? {
        request.rejection()?.let {
            emitInvalid(intent, packageName, it)
            return null
        }
        val base = IntentData(
            data = request.data,
            name = displayName(request),
            type = request.type,
            pubKey = request.pubKey,
            id = intent.extras?.getString("id") ?: "",
            callBackUrl = request.callbackUrl,
            compression = request.compression,
            returnType = request.returnType,
            permissions = request.permissions,
            currentAccount = "",
            route = route,
            event = null,
            encryptedData = null,
        )
        return when (request.type) {
            SignerType.SIGN_EVENT -> signedIntentData(base, request, intent, packageName, account)
            in encryptDecryptSignerTypes -> encryptOrDecrypt(request, intent, packageName, account)?.let { result ->
                base.copy(
                    currentAccount = request.currentUser ?: account.npub,
                    encryptedData = getEncryptedDataKind(request.type, result, request.data, account),
                    nip44v3Kind = request.nip44v3Kind,
                    nip44v3Scope = request.nip44v3Scope,
                )
            }
            SignerType.GET_PUBLIC_KEY -> base.copy(
                currentAccount = if (request.fromUri) "" else request.currentUser ?: Hex.decode(request.pubKey).toNpub(),
            )
            SignerType.SIGN_PSBT -> base.copy(
                currentAccount = if (request.fromUri) "" else request.currentUser ?: account.npub,
            )
            else -> null
        }
    }

    private fun displayName(request: NostrSignerRequest): String {
        val callbackHost = request.callbackUrl?.takeIf { !request.fromUri && it.isNotBlank() }?.toUri()?.host
        return callbackHost?.ifBlank { null } ?: request.appName
    }

    @Suppress("NoComments")
    private fun signedIntentData(
        base: IntentData,
        request: NostrSignerRequest,
        intent: Intent,
        packageName: String?,
        account: Account,
    ): IntentData? {
        // The signing key follows the account that authorizes this request.
        // Never switch to the pubkey named inside the event: an app holding a
        // grant on one local account could otherwise silently obtain a
        // signature from another stored account. A current_user naming
        // another stored account stays a mismatch for the approval guard.
        val unsignedEvent = getUnsignedEvent(request.data, account)
        if (hasAmbiguousRelayTags(unsignedEvent.kind, unsignedEvent.tags)) {
            emitInvalid(intent, packageName, "Refusing a multi-relay auth event")
            return null
        }
        val signed = account.signSync<Event>(
            unsignedEvent.createdAt,
            unsignedEvent.kind,
            unsignedEvent.tags,
            unsignedEvent.content,
        )
        return base.copy(
            currentAccount = request.currentUser ?: Hex.decode(signed.pubKey).toNpub(),
            event = signed,
            unsignedEventKey = unsignedEvent.pubKey,
        )
    }

    @Suppress("NoComments")
    private suspend fun encryptOrDecrypt(
        request: NostrSignerRequest,
        intent: Intent,
        packageName: String?,
        account: Account,
    ): String? = try {
        AmberUtils.encryptOrDecryptData(
            request.data,
            request.type,
            account,
            request.pubKey,
            request.nip44v3Kind,
            request.nip44v3Scope,
        ) ?: "Could not decrypt the message"
    } catch (e: Exception) {
        if (e is FailedMigrationException) throw e
        // A NIP-44 v3 failure (missing/invalid kind, context mismatch, bad
        // MAC, corrupt payload) means the request can never succeed: reject
        // it without showing the user an approval screen.
        if (request.type == SignerType.NIP44_V3_ENCRYPT || request.type == SignerType.NIP44_V3_DECRYPT) {
            emitInvalid(intent, packageName, "Invalid NIP-44 v3 request: ${e.message}", e)
            null
        } else {
            appScope.launch {
                accountRepository.getLogDatabase(account.npub).dao().insertLogIfEnabled(
                    LogEntity(
                        0,
                        packageName ?: "",
                        request.type.toString(),
                        e.message ?: "Could not decrypt the message",
                        System.currentTimeMillis(),
                    ),
                )
            }
            "Could not decrypt the message"
        }
    }

    private suspend fun getEncryptedDataKind(
        type: SignerType,
        result: String,
        data: String,
        account: Account,
    ): EncryptedDataKind = when (type) {
        SignerType.DECRYPT_ZAP_EVENT -> {
            PrivateZapEncryptedDataKind(result)
        }

        SignerType.NIP44_V3_ENCRYPT -> ClearTextEncryptedDataKind(nip44v3WireToPlaintext(data), result)
        SignerType.NIP44_V3_DECRYPT -> ClearTextEncryptedDataKind(nip44v3WireToPlaintext(result), result)

        else -> {
            val plaintextSide = if (type.name.contains("ENCRYPT")) data else result
            previewOf(plaintextSide, data, result, account) { AmberLog.e("IntentUtils", it) }
        }
    }

    private fun nip44v3WireToPlaintext(base64Wire: String): String = try {
        Base64.decode(base64Wire).toString(Charsets.UTF_8)
    } catch (_: Exception) {
        base64Wire
    }

    suspend fun getIntentData(
        context: Context,
        intent: Intent,
        packageName: String?,
        route: String?,
        currentLoggedInAccount: Account,
        referrer: Uri? = null,
    ): IntentData? = try {
        if (isNotNewRequest(intent)) {
            null
        } else {
            val account = currentUserAccount(context, intent) ?: currentLoggedInAccount
            when {
                intent.dataString?.startsWith("nostrconnect:") == true -> {
                    NostrConnectUtils.getIntentFromNostrConnect(intent, account, accountRepository, appScope)
                    null
                }
                isWebRequest(
                    hasApplicationIdExtra = intent.extras?.getString(Browser.EXTRA_APPLICATION_ID) != null,
                    hasBrowsableCategory = intent.hasCategory(Intent.CATEGORY_BROWSABLE),
                    referrerIsBrowser = isBrowserReferrer(context, referrer),
                    typeExtra = intent.extras?.getString("type"),
                    dataString = intent.dataString,
                ) -> getIntentDataWithoutExtras(intent.data?.toString() ?: "", intent, packageName, route, account)
                else -> getIntentDataFromIntent(intent, packageName, route, account)
            }
        }
    } catch (e: Exception) {
        reportParseError(context, intent, packageName, e)
        null
    }

    private fun isNotNewRequest(intent: Intent): Boolean {
        if (intent.data == null) return true
        if (isWakeUpWhileBunkerRequestWaits(intent)) return true
        val id = requestIdAssigningOne(intent)
        return intents.value.any { it.id == id }
    }

    private fun isWakeUpWhileBunkerRequestWaits(intent: Intent): Boolean = intent.dataString == "nostrsigner:" && BunkerRequestUtils.getBunkerRequests().isNotEmpty()

    private fun requestIdAssigningOne(intent: Intent): String {
        if (intent.extras?.getString("id").isNullOrEmpty()) {
            intent.putExtra("id", UUID.randomUUID().toString())
        }
        return intent.extras?.getString("id") ?: ""
    }

    private suspend fun currentUserAccount(context: Context, intent: Intent): Account? {
        val currentUser = intent.getStringExtra("current_user") ?: return null
        return AccountStorage.load(context, parsePubKey(currentUser))
    }

    private fun reportParseError(context: Context, intent: Intent, packageName: String?, e: Exception) {
        AmberLog.e(Amber.TAG, "Error parsing intent: ${e.message}", e)
        emitInvalid(intent, packageName, "Error parsing intent: ${e.message}", e)
        logParseErrorInEveryAccount(context, e)
    }

    fun getUnsignedEvent(
        data: String,
        account: Account,
    ): Event {
        val event = AmberEvent.fromJson(data)
        if (event.pubKey.isEmpty()) {
            event.pubKey = account.hexKey
        }
        if (event.id.isEmpty()) {
            event.id =
                EventHasher.hashId(
                    event.pubKey,
                    event.createdAt,
                    event.kind,
                    event.tags,
                    event.content,
                )
        }

        return event.toEvent()
    }

    fun sendResult(
        context: Context,
        account: Account,
        reply: IntentReply,
        approval: IntentApproval,
        copyToClipboard: suspend (text: String, sensitive: Boolean) -> Unit,
        onLoading: (Boolean) -> Unit,
        onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
    ) {
        val (intentData, packageName, event, value) = reply
        val (rememberType, kind, signPolicy, permissions, appName, shouldCloseApplication, deleteAfter, relay, decryptTypeScope) = approval
        val key = "$packageName"
        onLoading(true)
        appScope.launch {
            try {
                val daos = accountRepository.daos(account.npub)
                val application = daos.applicationDao.getByKey(key) ?: IntentResponse.newApplication(
                    key = key,
                    name = appName ?: packageName?.let { installedAppName(context, it) } ?: "",
                    account = account,
                    closeApplication = shouldCloseApplication != false,
                    deleteAfter = deleteAfter,
                )
                application.application.isConnected = true

                if (signPolicy != null) {
                    AmberUtils.configureSignPolicy(application, signPolicy, key, permissions)
                }

                if (rememberType != RememberType.NEVER) {
                    appScope.launch {
                        AmberUtils.acceptPermission(
                            application = application,
                            key = key,
                            type = intentData.type,
                            kind = kind,
                            rememberType = rememberType,
                            relay = relay,
                            encryptedData = intentData.encryptedData,
                            decryptTypeScope = decryptTypeScope,
                        )
                    }
                }

                if (intentData.type == SignerType.GET_PUBLIC_KEY) {
                    rememberPublicKeyGrant(application, key, shouldCloseApplication)
                }

                if (packageName != null) {
                    daos.permissionWriteDao.insertApplicationWithPermissions(application)
                    persistNativeAppMetadata(context, account, packageName)
                    val content = IntentResponse.historyContent(intentData, accepted = true, event = event, value = value)
                    val entry = IntentResponse.historyEntry(intentData, key, kind, accepted = true, content = content)
                    appScope.launch {
                        accountRepository.getHistoryDatabase(account.npub).dao().addHistory(listOf(entry), account.npub)
                    }
                }
                deliverResult(context, packageName, copyToClipboard, intentData, event, value)
                onRemoveIntentData(listOf(intentData), IntentResultType.REMOVE)
                mainActivity()?.let {
                    it.intent = null
                    it.finishAndRemoveTask()
                }
            } finally {
                onLoading(false)
            }
        }
    }

    private fun installedAppName(context: Context, packageName: String): String? = try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        sanitizeAppName(context.packageManager.getApplicationLabel(info).toString())
    } catch (_: Exception) {
        null
    }

    private fun rememberPublicKeyGrant(application: ApplicationWithPermissions, key: String, shouldCloseApplication: Boolean?) {
        shouldCloseApplication?.let { application.application.closeApplication = it }
        if (application.permissions.none { it.type == SignerType.GET_PUBLIC_KEY.toString() }) {
            application.permissions.add(
                ApplicationPermissionsEntity(
                    null,
                    key,
                    SignerType.GET_PUBLIC_KEY.toString(),
                    null,
                    true,
                    RememberType.ALWAYS.screenCode,
                    Long.MAX_VALUE / 1000,
                    0,
                ),
            )
        }
    }

    private suspend fun deliverResult(
        context: Context,
        packageName: String?,
        copyToClipboard: suspend (text: String, sensitive: Boolean) -> Unit,
        intentData: IntentData,
        event: String,
        value: String,
    ) {
        if (packageName != null) {
            val intent = Intent()
            intent.putExtra("signature", value)
            intent.putExtra("result", value)
            intent.putExtra("id", intentData.id)
            intent.putExtra("event", event)
            if (intentData.type == SignerType.GET_PUBLIC_KEY) {
                intent.putExtra("package", BuildConfig.APPLICATION_ID)
            }
            mainActivity()?.setResult(RESULT_OK, intent)
        } else if (!intentData.callBackUrl.isNullOrBlank()) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = (intentData.callBackUrl + Uri.encode(IntentResponse.callbackPayload(intentData, event, value))).toUri()
            context.startActivity(intent)
        } else {
            val isSignature = intentData.returnType == ReturnType.SIGNATURE
            val result = if (isSignature) value else event
            val message = getStringBlocking(if (isSignature) Res.string.signature_copied_to_the_clipboard else Res.string.event_copied_to_the_clipboard)
            val resultIsPlaintext = intentData.type in decryptSignerTypes
            withContext(Dispatchers.Main) {
                copyToClipboard(result, resultIsPlaintext)
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Answers a rejected request. A web request (no calling package) shares the "null"
     * key with every website: as on approval, nothing is remembered or saved for it, but
     * it is still answered and its sheet closed.
     */
    fun sendRejection(
        key: String,
        account: Account,
        intentData: IntentData,
        appName: String,
        rememberType: RememberType,
        onLoading: (Boolean) -> Unit,
        onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
        kind: Int? = null,
        relay: String = "",
        decryptTypeScope: DecryptTypeScope = DecryptTypeScope.ALL,
    ) {
        appScope.launch(Dispatchers.IO) {
            val closeApplication = if (key == "null") {
                true
            } else {
                val application = accountRepository.getDatabase(account.npub).dao().getByKey(key)
                    ?: IntentResponse.newApplication(key, appName, account, closeApplication = true, deleteAfter = 0L)
                if (rememberType != RememberType.NEVER) {
                    AmberUtils.acceptOrRejectPermission(
                        application,
                        key,
                        intentData.type,
                        kind,
                        false,
                        rememberType,
                        relay,
                        intentData.encryptedData,
                        decryptTypeScope,
                        dao = accountRepository.daos(account.npub).permissionWriteDao,
                    )
                }

                accountRepository.daos(account.npub).permissionWriteDao.insertApplicationWithPermissions(application)
                val content = IntentResponse.historyContent(intentData, accepted = false, event = "", value = "")
                accountRepository.getHistoryDatabase(account.npub).dao().addHistory(
                    listOf(IntentResponse.historyEntry(intentData, key, kind, accepted = false, content = content)),
                    account.npub,
                )
                application.application.closeApplication
            }

            val activity = mainActivity()
            activity?.intent = null
            activity?.setResult(
                RESULT_OK,
                Intent()
                    .also { it.putExtra("rejected", true) }
                    .also { it.putExtra("id", intentData.id) },
            )
            if (closeApplication) {
                activity?.finishAndRemoveTask()
            }
            onRemoveIntentData(listOf(intentData), IntentResultType.REMOVE)
            onLoading(false)
        }
    }

    private fun logParseErrorInEveryAccount(context: Context, e: Exception) {
        appScope.launch {
            SavedAccounts.all(context).forEach {
                accountRepository.getLogDatabase(it.npub).dao().insertLogIfEnabled(
                    LogEntity(
                        id = 0,
                        url = "IntentUtils",
                        type = "Error",
                        message = e.stackTraceToString(),
                        time = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }
}

/** What [IntentUtils.sendResult] answers: the request, the app that sent it, and the result. */
data class IntentReply(
    val intentData: IntentData,
    val packageName: String?,
    /** The signed event (or result) for the history and the `event` extra. */
    val event: String,
    /** The `result` extra: a signature, a public key, a ciphertext or plaintext. */
    val value: String,
)

/**
 * What the user chose on the approval screen: how long to remember it, and for a
 * first request, the app's name, sign policy and permissions.
 */
data class IntentApproval(
    val rememberType: RememberType,
    /** The event kind a remembered sign grant covers. */
    val kind: Int? = null,
    val signPolicy: Int? = null,
    val permissions: List<Permission>? = null,
    val appName: String? = null,
    val shouldCloseApplication: Boolean? = null,
    val deleteAfter: Long = 0L,
    val relay: String = "",
    val decryptTypeScope: DecryptTypeScope = DecryptTypeScope.ALL,
)

/**
 * Safe read of the launch referrer. `Activity.getReferrer()` dereferences a null launch
 * intent on some OEM framework builds (e.g. Samsung, Android 12) and crashes when the
 * activity was delivered or left without an intent (see `sendRejection`, which nulls it).
 * Null is also the correct "unknown referrer" answer, which [isBrowserReferrer] already
 * treats as not-a-browser.
 */
fun Activity.safeReferrer(): Uri? = try {
    referrer
} catch (_: Exception) {
    null
}

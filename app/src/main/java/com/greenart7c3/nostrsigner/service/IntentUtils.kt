package com.greenart7c3.nostrsigner.service

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Browser
import android.widget.Toast
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.core.net.toUri
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.FailedMigrationException
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.hasAmbiguousRelayTags
import com.greenart7c3.nostrsigner.common.rememberedDecision
import com.greenart7c3.nostrsigner.common.sanitizeAppName
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.event_copied_to_the_clipboard
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.signature_copied_to_the_clipboard
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.LogEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.ClearTextEncryptedDataKind
import com.greenart7c3.nostrsigner.models.EncryptedDataKind
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.IntentResultType
import com.greenart7c3.nostrsigner.models.PrivateZapEncryptedDataKind
import com.greenart7c3.nostrsigner.models.ReturnType
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.models.decryptSignerTypes
import com.greenart7c3.nostrsigner.models.encryptDecryptSignerTypes
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.ui.RememberType
import com.greenart7c3.nostrsigner.ui.components.DecryptTypeScope
import com.greenart7c3.nostrsigner.ui.setSensitiveClip
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealEvent
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils
import java.net.URLDecoder
import java.util.UUID
import kotlin.io.encoding.Base64
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
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
    private val _intents = MutableStateFlow<ImmutableList<IntentData>>(persistentListOf())
    val intents = _intents.asStateFlow()

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
        val dao = Amber.instance.dao(account.npub)
        val saved = dao.getByKey(packageName)?.application ?: return
        if (saved.name.isNotBlank() && saved.icon.isNotBlank()) return

        val newIcon = saved.icon.ifBlank {
            AppIconStore.resolveIconPath(context, packageName) ?: ""
        }
        val newName = saved.name.ifBlank {
            runCatching {
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                sanitizeAppName(context.packageManager.getApplicationLabel(info).toString())
            }.getOrNull().orEmpty()
        }

        if (newName == saved.name && newIcon == saved.icon) return
        dao.updateNameAndIcon(packageName, newName.ifBlank { saved.name }, newIcon)
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

    // Compiled once at class load time; avoids recompiling the pattern on every isUrlEncoded() call.
    private val URL_ENCODED_REGEX = Regex("%[0-9a-fA-F]{2}")

    fun decodeData(data: String, replace: Boolean = true, decodeData: Boolean = true): String {
        val stripped = data.replace("nostrsigner:", "")
        // replace keeps a literal '+' instead of letting URLDecoder turn it into a space.
        val raw = if (replace) stripped.replace("+", "%2b") else stripped
        return if (decodeData) URLDecoder.decode(raw, "utf-8") else raw
    }

    fun isUrlEncoded(input: String): Boolean = URL_ENCODED_REGEX.containsMatchIn(input)

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

    /** Runs the key operation [request] asks for with [account] and wraps the result for the approval screen. */
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
                    // The peer (pubKey) is not a local account; the request runs with `account`.
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

    /** A native app is named after its callback host when it has one; a web app by its appName. */
    private fun displayName(request: NostrSignerRequest): String {
        val callbackHost = request.callbackUrl?.takeIf { !request.fromUri && it.isNotBlank() }?.toUri()?.host
        return callbackHost?.ifBlank { null } ?: request.appName
    }

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

    /** The encrypted or decrypted text, or null when the request was rejected as invalid. */
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
            Amber.instance.applicationIOScope.launch {
                Amber.instance.getLogDatabase(account.npub).dao().insertLogIfEnabled(
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

        // v3 wire values are Base64; decode once so display/history can read the
        // plaintext from `text`, while `result` keeps the wire value.
        SignerType.NIP44_V3_ENCRYPT -> {
            val plaintext = try {
                Base64.decode(data).toString(Charsets.UTF_8)
            } catch (_: Exception) {
                data
            }
            ClearTextEncryptedDataKind(plaintext, result)
        }
        SignerType.NIP44_V3_DECRYPT -> {
            val plaintext = try {
                Base64.decode(result).toString(Charsets.UTF_8)
            } catch (_: Exception) {
                result
            }
            ClearTextEncryptedDataKind(plaintext, result)
        }

        // The plaintext side is what the user reads: the data for an encrypt, the result for a decrypt.
        else -> previewOf(if (type.name.contains("ENCRYPT")) data else result, data, result, account)
    }

    /** Shows [text] as an event (opening a seal for the preview only), a tag array or plain text. */
    private suspend fun previewOf(
        text: String,
        data: String,
        result: String,
        account: Account,
    ): EncryptedDataKind = try {
        when {
            text.startsWith("{") -> {
                val event = AmberEvent.fromJson(text)
                if (event.kind == SealEvent.KIND) {
                    EventEncryptedDataKind(event, sealPreview(event, account), result)
                } else {
                    EventEncryptedDataKind(event, null, result)
                }
            }
            text.startsWith("[") -> TagArrayEncryptedDataKind(JacksonMapper.fromJsonToTagArray(text), result)
            else -> ClearTextEncryptedDataKind(data, result)
        }
    } catch (e: Exception) {
        AmberLog.e("IntentUtils", "Error parsing ${if (text.startsWith("{")) "JSON" else "TagArray"}: ${e.message}")
        ClearTextEncryptedDataKind(data, result)
    }

    /** The decrypted content of [seal]; the caller still gets the seal itself. */
    private suspend fun sealPreview(seal: AmberEvent, account: Account): EncryptedDataKind {
        val decrypted = try {
            account.decrypt(seal.content, account.hexKey)
        } catch (_: Exception) {
            seal.content
        }
        return try {
            when {
                decrypted.startsWith("{") -> EventEncryptedDataKind(AmberEvent.fromJson(decrypted), null, decrypted)
                decrypted.startsWith("[") -> TagArrayEncryptedDataKind(JacksonMapper.fromJsonToTagArray(decrypted), decrypted)
                else -> ClearTextEncryptedDataKind(seal.content, decrypted)
            }
        } catch (e: Exception) {
            AmberLog.e("IntentUtils", "Error parsing TagArray: ${e.message}")
            ClearTextEncryptedDataKind(seal.content, decrypted)
        }
    }

    fun parsePubKey(key: String): String? {
        if (key.startsWith("npub1")) {
            return key
        }
        return try {
            Hex.decode(key).toNpub()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Returns true when the launching app is a web browser. [referrer] is the value of
     * `Activity.getReferrer()`: an `http(s)` referrer is the web page itself, an
     * `android-app://<package>` referrer is the launching app.
     */
    fun isBrowserReferrer(context: Context, referrer: Uri?): Boolean = when (referrer?.scheme) {
        "http", "https" -> true
        "android-app" -> isBrowserPackage(context, referrer.host)
        else -> false
    }

    fun isBrowserPackage(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val browserIntent = Intent(Intent.ACTION_VIEW, "https://example.com".toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setPackage(packageName)
        return try {
            context.packageManager.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Decides whether a `nostrsigner:` request carries its parameters in the URI query
     * (web apps) instead of intent extras (native NIP-55 apps). Browsers don't agree on
     * how they mark external intents: Chrome adds [Browser.EXTRA_APPLICATION_ID], others
     * (e.g. DuckDuckGo) only add [Intent.CATEGORY_BROWSABLE] or nothing at all. A `type`
     * extra means the caller used the native extras API, so only then do we ignore the
     * weaker signals (BROWSABLE category, browser referrer, `type=` query parameter).
     */
    internal fun isWebRequest(
        hasApplicationIdExtra: Boolean,
        hasBrowsableCategory: Boolean,
        referrerIsBrowser: Boolean,
        typeExtra: String?,
        dataString: String?,
    ): Boolean {
        if (hasApplicationIdExtra) return true
        if (!typeExtra.isNullOrBlank()) return false
        return hasBrowsableCategory || referrerIsBrowser || hasTypeQueryParameter(dataString)
    }

    private fun hasTypeQueryParameter(dataString: String?): Boolean {
        if (dataString.isNullOrBlank()) return false
        val decoded = try {
            URLDecoder.decode(dataString.replace("+", "%2b"), "utf-8")
        } catch (_: Exception) {
            dataString
        }
        return listOf(dataString, decoded).any { data ->
            data.substringAfter('?', "")
                .split('?', '&')
                .any { it.startsWith("type=") }
        }
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
            val account = requestAccount(context, intent) ?: currentLoggedInAccount
            when {
                intent.dataString?.startsWith("nostrconnect:") == true -> {
                    NostrConnectUtils.getIntentFromNostrConnect(intent, account)
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
        reportParseError(intent, packageName, e)
        null
    }

    /**
     * True when [intent] carries nothing to queue: no data, the empty request a
     * client sends just to bring Amber up while a bunker request waits, or a
     * request already queued. Gives the request an id first if it has none.
     */
    private fun isNotNewRequest(intent: Intent): Boolean {
        if (intent.data == null) return true
        if (intent.dataString == "nostrsigner:" && BunkerRequestUtils.getBunkerRequests().isNotEmpty()) return true
        if (intent.extras?.getString("id").isNullOrEmpty()) {
            intent.putExtra("id", UUID.randomUUID().toString())
        }
        val id = intent.extras?.getString("id") ?: ""
        return intents.value.any { it.id == id }
    }

    /** The stored account the `current_user` extra names, if any. */
    private suspend fun requestAccount(context: Context, intent: Intent): Account? {
        val currentUser = intent.getStringExtra("current_user") ?: return null
        return LocalPreferences.loadAccount(context, parsePubKey(currentUser))
    }

    private fun reportParseError(intent: Intent, packageName: String?, e: Exception) {
        AmberLog.e(Amber.TAG, "Error parsing intent: ${e.message}", e)
        emitInvalid(intent, packageName, "Error parsing intent: ${e.message}", e)
        Amber.instance.applicationIOScope.launch {
            LocalPreferences.allSavedAccounts(Amber.instance).forEach {
                Amber.instance.getLogDatabase(it.npub).dao().insertLogIfEnabled(
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
        packageName: String?,
        account: Account,
        key: String,
        clipboardManager: Clipboard,
        event: String,
        value: String,
        intentData: IntentData,
        kind: Int?,
        onLoading: (Boolean) -> Unit,
        permissions: List<Permission>? = null,
        appName: String? = null,
        signPolicy: Int? = null,
        onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
        shouldCloseApplication: Boolean? = null,
        rememberType: RememberType,
        deleteAfter: Long = 0L,
        relay: String = "",
        decryptTypeScope: DecryptTypeScope = DecryptTypeScope.ALL,
    ) {
        onLoading(true)
        Amber.instance.applicationIOScope.launch {
            try {
                val dao = Amber.instance.dao(account.npub)
                val application = dao.getByKey(key) ?: IntentResponse.newApplication(
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
                    Amber.instance.applicationIOScope.launch {
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
                    dao.insertApplicationWithPermissions(application)
                    // The calling app is visible to us now (mid-request); capture its
                    // launcher icon + label so a first-ever connection populates them
                    // immediately, not only on a later request.
                    persistNativeAppMetadata(context, account, packageName)
                    val content = IntentResponse.historyContent(intentData, accepted = true, event = event, value = value)
                    val entry = IntentResponse.historyEntry(intentData, key, kind, accepted = true, content = content)
                    Amber.instance.applicationIOScope.launch {
                        Amber.instance.getHistoryDatabase(account.npub).dao().addHistory(listOf(entry), account.npub)
                    }
                }
                deliverResult(context, packageName, clipboardManager, intentData, event, value)
                onRemoveIntentData(listOf(intentData), IntentResultType.REMOVE)
                Amber.instance.getMainActivity()?.let {
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

    /** A connected app may always read the public key; answering get_public_key also sets whether Amber closes after its requests. */
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

    /** Hands the result back: as the activity result to a native app, to a web app's callback URL, or else to the clipboard. */
    private suspend fun deliverResult(
        context: Context,
        packageName: String?,
        clipboardManager: Clipboard,
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
            Amber.instance.getMainActivity()?.setResult(RESULT_OK, intent)
        } else if (!intentData.callBackUrl.isNullOrBlank()) {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = (intentData.callBackUrl + Uri.encode(IntentResponse.callbackPayload(intentData, event, value))).toUri()
            context.startActivity(intent)
        } else {
            val isSignature = intentData.returnType == ReturnType.SIGNATURE
            val result = if (isSignature) value else event
            val message = getStringBlocking(if (isSignature) Res.string.signature_copied_to_the_clipboard else Res.string.event_copied_to_the_clipboard)
            withContext(Dispatchers.Main) {
                if (intentData.type in decryptSignerTypes) {
                    // A decrypt result is the plaintext itself: hide it from
                    // clipboard previews and clear it, like the nsec.
                    clipboardManager.setSensitiveClip(context, result)
                } else {
                    clipboardManager.setClipEntry(ClipEntry(ClipData.newPlainText("", result)))
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun sendRejection(
        key: String,
        account: Account,
        intentData: IntentData,
        appName: String,
        rememberType: RememberType,
        kind: Int?,
        onLoading: (Boolean) -> Unit,
        onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
        relay: String = "",
        decryptTypeScope: DecryptTypeScope = DecryptTypeScope.ALL,
    ) {
        Amber.instance.applicationIOScope.launch(Dispatchers.IO) {
            if (key == "null") {
                onLoading(false)
                return@launch
            }

            val application = Amber.instance.getDatabase(account.npub).dao().getByKey(key)
                ?: IntentResponse.newApplication(key, appName, account, closeApplication = true, deleteAfter = 0L)
            if (rememberType != RememberType.NEVER) {
                AmberUtils.acceptOrRejectPermission(
                    application,
                    key,
                    intentData.type,
                    kind,
                    false,
                    rememberType,
                    account,
                    relay,
                    intentData.encryptedData,
                    decryptTypeScope,
                )
            }

            Amber.instance.dao(account.npub).insertApplicationWithPermissions(application)
            val content = IntentResponse.historyContent(intentData, accepted = false, event = "", value = "")
            Amber.instance.getHistoryDatabase(account.npub).dao().addHistory(
                listOf(IntentResponse.historyEntry(intentData, key, kind, accepted = false, content = content)),
                account.npub,
            )

            val activity = Amber.instance.getMainActivity()
            activity?.intent = null
            activity?.setResult(
                RESULT_OK,
                Intent()
                    .also { it.putExtra("rejected", true) }
                    .also { it.putExtra("id", intentData.id) },
            )
            if (application.application.closeApplication) {
                activity?.finishAndRemoveTask()
            }
            onRemoveIntentData(listOf(intentData), IntentResultType.REMOVE)
            onLoading(false)
        }
    }

    fun isRemembered(signPolicy: Int?, permission: ApplicationPermissionsEntity?): Boolean? = rememberedDecision(signPolicy, permission?.activeDecision(TimeUtils.now()))
}

/**
 * Safe read of the launch referrer. `Activity.getReferrer()` dereferences a null launch
 * intent on some OEM framework builds (e.g. Samsung, Android 12) and crashes when the
 * activity was delivered or left without an intent (see `sendRejection`, which nulls it).
 * Null is also the correct "unknown referrer" answer, which [IntentUtils.isBrowserReferrer] already
 * treats as not-a-browser.
 */
fun Activity.safeReferrer(): Uri? = try {
    referrer
} catch (_: Exception) {
    null
}

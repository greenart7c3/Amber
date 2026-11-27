package com.greenart7c3.nostrsigner.models

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.platform.Clipboard
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.DataStoreAccess
import com.greenart7c3.nostrsigner.KeystorePolicy
import com.greenart7c3.nostrsigner.common.psbt.PsbtSighashPolicy
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.secret_key_copied_to_clipboard
import com.greenart7c3.nostrsigner.ui.setSensitiveClip
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapRequestBuilder
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * A signed-in Nostr account and the operations its private key performs.
 *
 * @param scope watches name/picture to mark the account saveable, and runs the clipboard copy.
 * @param appContext the application context: key storage, the keyguard and toasts. Previews pass
 * none, so anything that needs it fails there instead of guessing.
 * @param keyAccessCheck reads the policy synchronously: the DataStore-backed settings may not be
 * loaded yet, and this gate must see the user's actual decision, not a default.
 */
@Stable
class Account(
    val signer: NostrSignerInternal,
    val name: MutableStateFlow<String>,
    val picture: MutableStateFlow<String>,
    signPolicy: Int,
    didBackup: Boolean,
    private val scope: CoroutineScope,
    appContext: Context? = null,
    private val keyAccessCheck: () -> Unit = {
        KeystorePolicy.requireAccess(checkNotNull(appContext) { "No context to check the device lock with" })
    },
) {
    /**
     * The account's public key, read from [signer]'s key pair: a copy passed in
     * separately could disagree with the key that actually signs.
     */
    val hexKey: HexKey = signer.keyPair.pubKey.toHexKey()

    /** [hexKey] as an npub. */
    val npub: String = signer.keyPair.pubKey.toNpub()

    /** Encryption and decryption with the account key. */
    val crypto: AccountCrypto = AccountCrypto(signer) { requirePrivateKeyAccess() }

    /** Exporting the account's secret key and recovery phrase. */
    val keyExport: AccountKeyExport = AccountKeyExport(this, scope, appContext)

    var signPolicy: Int = signPolicy
        set(value) {
            if (field != value) {
                field = value
                _saveable.value = AccountState(this)
            }
        }

    var didBackup: Boolean = didBackup
        set(value) {
            if (field != value) {
                field = value
                _saveable.value = AccountState(this)
            }
        }

    private val _saveable = MutableStateFlow(AccountState(this))
    val saveable = _saveable.asStateFlow()

    init {
        scope.launch {
            combine(name, picture) { _, _ -> }.drop(1).collect {
                _saveable.value = AccountState(this@Account)
            }
        }
    }

    internal fun requirePrivateKeyAccess() = keyAccessCheck()

    suspend fun <T : Event> sign(eventTemplate: EventTemplate<T>): T {
        requirePrivateKeyAccess()
        return signer.sign(eventTemplate)
    }

    fun <T : Event> signSync(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T {
        requirePrivateKeyAccess()
        return signer.signerSync.sign(createdAt, kind, tags, content)
    }

    suspend fun signPsbt(psbtHex: String): String {
        requirePrivateKeyAccess()
        PsbtSighashPolicy.requireCommitsToAllOutputs(psbtHex, signer.keyPair.pubKey)
        return signer.signPsbt(psbtHex)
    }

    fun decryptZapEvent(
        data: String,
    ): String? {
        requirePrivateKeyAccess()
        val event = Event.fromJson(data) as ZapRequestEvent
        return try {
            PrivateZapRequestBuilder().decryptZapEvent(
                event = event,
                signer = signer.signerSync,
            ).toJson()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun createMessageNIP17(template: EventTemplate<ChatMessageEvent>): NIP17Factory.Result {
        requirePrivateKeyAccess()
        return NIP17Factory().createMessageNIP17(template, signer)
    }
}

/** The account's secret key and recovery phrase leaving the app, each checking key access first. */
class AccountKeyExport internal constructor(
    private val account: Account,
    private val scope: CoroutineScope,
    private val appContext: Context?,
) {
    private fun requireContext(): Context = checkNotNull(appContext) { "Account has no application context" }

    fun nip49Encrypt(password: String): String {
        account.requirePrivateKeyAccess()
        return Nip49().encrypt(account.signer.keyPair.privKey!!.toHexKey(), password)
    }

    @Suppress("NoComments")
    suspend fun seedWords(): String {
        // Gate before reading: the repair below swallows decrypt failures and
        // would otherwise turn a lock refusal into an erased recovery phrase.
        account.requirePrivateKeyAccess()
        val stored = runCatching { DataStoreAccess.getEncryptedKey(requireContext(), account.npub, DataStoreAccess.SEED_WORDS) }.getOrNull() ?: ""

        // Lazy repair for accounts restored by pre-fix builds, which stored the
        // NIP-44 ciphertext in SEED_WORDS. If the stored value is not a valid
        // mnemonic but decrypts (with this account's key) to one, rewrite it;
        // otherwise overwrite it with an empty value so the invalid ciphertext
        // no longer masquerades as a recovery phrase. Best-effort: a repair
        // failure must not break the read.
        if (stored.isNotEmpty() && !Nip06().isValidMnemonic(stored)) {
            val repaired = runCatching { account.crypto.nip44Decrypt(stored, account.hexKey) }.getOrNull()
                ?.takeIf { Nip06().isValidMnemonic(it) }
                .orEmpty()
            runCatching {
                DataStoreAccess.saveEncryptedKey(requireContext(), account.npub, DataStoreAccess.SEED_WORDS, repaired)
            }
            return repaired
        }
        return stored
    }

    fun getNsec(): String {
        account.requirePrivateKeyAccess()
        return account.signer.keyPair.privKey!!.toNsec()
    }

    fun copyToClipboard(clipboardManager: Clipboard) {
        scope.launch {
            val nsec = try {
                getNsec()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AmberLog.e(Amber.TAG, "Failed to export secret key for clipboard", e)
                return@launch
            }
            account.didBackup = true
            scope.launch(Dispatchers.Main) {
                clipboardManager.setSensitiveClip(requireContext(), nsec)

                Toast.makeText(
                    requireContext(),
                    getStringBlocking(Res.string.secret_key_copied_to_clipboard),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}

@Immutable
class AccountState(val account: Account)

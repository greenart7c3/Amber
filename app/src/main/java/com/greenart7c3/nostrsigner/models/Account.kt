package com.greenart7c3.nostrsigner.models

import android.widget.Toast
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.platform.Clipboard
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.signer.RemoteSigner
import com.greenart7c3.nostrsigner.ui.setSensitiveClip
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Public, metadata-only account model used by the main/UI process. It deliberately
 * does NOT hold a [com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal] or
 * any private key — every cryptographic operation is forwarded by npub to the
 * isolated `:signer` process via [RemoteSigner]. The decrypted key never enters
 * this process.
 */
@Stable
class Account(
    val hexKey: HexKey,
    val npub: String,
    val name: MutableStateFlow<String>,
    val picture: MutableStateFlow<String>,
    signPolicy: Int,
    didBackup: Boolean,
) {
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
        Amber.instance.applicationIOScope.launch {
            combine(name, picture) { _, _ -> }.drop(1).collect {
                _saveable.value = AccountState(this@Account)
            }
        }
    }

    suspend fun <T : Event> sign(eventTemplate: EventTemplate<T>): T =
        @Suppress("UNCHECKED_CAST")
        (Event.fromJson(RemoteSigner.signEvent(npub, eventTemplate.createdAt, eventTemplate.kind, eventTemplate.tags, eventTemplate.content)) as T)

    fun <T : Event> signSync(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T =
        @Suppress("UNCHECKED_CAST")
        (Event.fromJson(RemoteSigner.signEvent(npub, createdAt, kind, tags, content)) as T)

    fun nip49Encrypt(password: String): String = RemoteSigner.nip49Encrypt(npub, password)

    suspend fun nip44Encrypt(plainText: String, toPublicKey: String): String = RemoteSigner.nip44Encrypt(npub, plainText, toPublicKey)

    suspend fun nip04Encrypt(plainText: String, toPublicKey: String): String = RemoteSigner.nip04Encrypt(npub, plainText, toPublicKey)

    suspend fun nip44Decrypt(cipherText: String, fromPublicKey: String): String = RemoteSigner.nip44Decrypt(npub, cipherText, fromPublicKey)

    suspend fun nip04Decrypt(cipherText: String, fromPublicKey: String): String = RemoteSigner.nip04Decrypt(npub, cipherText, fromPublicKey)

    fun nip44v3Encrypt(plainText: ByteArray, toPublicKey: String, kind: Int, scope: String): String = RemoteSigner.nip44v3Encrypt(npub, plainText, toPublicKey, kind, scope)

    fun nip44v3Decrypt(cipherText: String, fromPublicKey: String, kind: Int, scope: String): ByteArray = RemoteSigner.nip44v3Decrypt(npub, cipherText, fromPublicKey, kind, scope)

    suspend fun decrypt(encryptedContent: String, fromPublicKey: String): String = RemoteSigner.decrypt(npub, encryptedContent, fromPublicKey)

    suspend fun signPsbt(psbtHex: String): String = RemoteSigner.signPsbt(npub, psbtHex)

    suspend fun seedWords(): String = RemoteSigner.seedWords(npub)

    fun decryptZapEvent(data: String): String? = RemoteSigner.decryptZapEvent(npub, data)

    /** Returns the gift-wrapped events to publish. */
    suspend fun createMessageNIP17(template: EventTemplate<ChatMessageEvent>): List<Event> = RemoteSigner.createMessageNIP17(npub, template.createdAt, template.kind, template.tags, template.content)

    suspend fun getNsec(): String = RemoteSigner.getNsec(npub)

    fun copyToClipboard(clipboardManager: Clipboard) {
        Amber.instance.applicationIOScope.launch {
            didBackup = true
            val nsec = getNsec()
            Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                clipboardManager.setSensitiveClip("", nsec)

                Toast.makeText(
                    Amber.instance,
                    Amber.instance.getString(R.string.secret_key_copied_to_clipboard),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}

@Immutable
class AccountState(val account: Account)

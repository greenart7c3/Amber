package com.greenart7c3.nostrsigner.models

import android.app.KeyguardManager
import android.widget.Toast
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.platform.Clipboard
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.DataStoreAccess
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.service.DeviceUnlockPolicy
import com.greenart7c3.nostrsigner.service.nip44v3.Nip44v3
import com.greenart7c3.nostrsigner.ui.setSensitiveClip
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapRequestBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@Stable
class Account(
    val signer: NostrSignerInternal,
    val hexKey: HexKey,
    val npub: String,
    val name: MutableStateFlow<String>,
    val picture: MutableStateFlow<String>,
    signPolicy: Int,
    didBackup: Boolean,
    // Watches name/picture to mark the account saveable; injectable so Compose
    // previews can build an Account without the Amber Application singleton.
    scope: CoroutineScope = Amber.instance.applicationIOScope,
    private val keyAccessCheck: () -> Unit = {
        val required = Amber.instance.settings.requireUnlockedDevice
        if (required) {
            val keyguard = Amber.instance.getSystemService(KeyguardManager::class.java)
            DeviceUnlockPolicy.requireAccess(required, keyguard?.isDeviceLocked ?: true)
        }
    },
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

    fun nip49Encrypt(password: String): String {
        requirePrivateKeyAccess()
        return Nip49().encrypt(signer.keyPair.privKey!!.toHexKey(), password)
    }

    suspend fun nip44Encrypt(plainText: String, toPublicKey: String): String {
        requirePrivateKeyAccess()
        return signer.nip44Encrypt(plainText, toPublicKey)
    }

    suspend fun nip04Encrypt(plainText: String, toPublicKey: String): String {
        requirePrivateKeyAccess()
        return signer.nip04Encrypt(plainText, toPublicKey)
    }

    suspend fun nip44Decrypt(cipherText: String, fromPublicKey: String): String {
        requirePrivateKeyAccess()
        return signer.nip44Decrypt(cipherText, fromPublicKey)
    }

    suspend fun nip04Decrypt(cipherText: String, fromPublicKey: String): String {
        requirePrivateKeyAccess()
        return signer.nip04Decrypt(cipherText, fromPublicKey)
    }

    fun nip44v3Encrypt(plainText: ByteArray, toPublicKey: String, kind: Int, scope: String): String {
        requirePrivateKeyAccess()
        return Nip44v3.encrypt(plainText, signer.keyPair.privKey!!, toPublicKey.hexToByteArray(), kind, scope)
    }

    fun nip44v3Decrypt(cipherText: String, fromPublicKey: String, kind: Int, scope: String): ByteArray {
        requirePrivateKeyAccess()
        return Nip44v3.decrypt(cipherText, signer.keyPair.privKey!!, fromPublicKey.hexToByteArray(), kind, scope)
    }

    suspend fun decrypt(encryptedContent: String, fromPublicKey: String): String {
        requirePrivateKeyAccess()
        return signer.decrypt(encryptedContent, fromPublicKey)
    }

    suspend fun signPsbt(psbtHex: String): String {
        requirePrivateKeyAccess()
        return signer.signPsbt(psbtHex)
    }

    suspend fun seedWords() = runCatching { DataStoreAccess.getEncryptedKey(Amber.instance, npub, DataStoreAccess.SEED_WORDS) }.getOrNull() ?: ""

    fun decryptZapEvent(
        data: String,
    ): String? {
        requirePrivateKeyAccess()
        val event = Event.fromJson(data) as LnZapRequestEvent
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

    suspend fun getNsec(): String {
        requirePrivateKeyAccess()
        return signer.keyPair.privKey!!.toNsec()
    }

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

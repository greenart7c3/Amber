package com.greenart7c3.nostrsigner.models

import android.widget.Toast
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.platform.Clipboard
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.DataStoreAccess
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.service.RemoteBunkerClient
import com.greenart7c3.nostrsigner.service.nip44v3.Nip44v3
import com.greenart7c3.nostrsigner.ui.setSensitiveClip
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapRequestBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Stable
class Account(
    val signer: NostrSignerInternal,
    val hexKey: HexKey,
    val npub: String,
    val name: MutableStateFlow<String>,
    val picture: MutableStateFlow<String>,
    signPolicy: Int,
    didBackup: Boolean,
    val proxy: ProxyAccountMetadata? = null,
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

    val isProxy: Boolean get() = proxy != null

    private val _saveable = MutableStateFlow(AccountState(this))
    val saveable = _saveable.asStateFlow()

    init {
        Amber.instance.applicationIOScope.launch {
            combine(name, picture) { _, _ -> }.drop(1).collect {
                _saveable.value = AccountState(this@Account)
            }
        }
    }

    suspend fun <T : Event> sign(eventTemplate: EventTemplate<T>): T = if (isProxy) {
        @Suppress("UNCHECKED_CAST")
        RemoteBunkerClient.remoteSignEvent(this, eventTemplate) as T
    } else {
        signer.sign(eventTemplate)
    }

    fun <T : Event> signSync(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T = if (isProxy) {
        @Suppress("UNCHECKED_CAST")
        runBlocking {
            RemoteBunkerClient.remoteSignEventSync(this@Account, createdAt, kind, tags, content)
        } as T
    } else {
        signer.signerSync.sign(createdAt, kind, tags, content)
    }

    fun nip49Encrypt(password: String): String {
        check(!isProxy) { "Bunker proxy accounts have no local key to export" }
        return Nip49().encrypt(signer.keyPair.privKey!!.toHexKey(), password)
    }

    suspend fun nip44Encrypt(plainText: String, toPublicKey: String): String = if (isProxy) {
        RemoteBunkerClient.remoteEncrypt(this, plainText, toPublicKey, useNip44 = true)
    } else {
        signer.nip44Encrypt(plainText, toPublicKey)
    }

    suspend fun nip04Encrypt(plainText: String, toPublicKey: String): String = if (isProxy) {
        RemoteBunkerClient.remoteEncrypt(this, plainText, toPublicKey, useNip44 = false)
    } else {
        signer.nip04Encrypt(plainText, toPublicKey)
    }

    suspend fun nip44Decrypt(cipherText: String, fromPublicKey: String): String = if (isProxy) {
        RemoteBunkerClient.remoteDecrypt(this, cipherText, fromPublicKey, useNip44 = true)
    } else {
        signer.nip44Decrypt(cipherText, fromPublicKey)
    }

    suspend fun nip04Decrypt(cipherText: String, fromPublicKey: String): String = if (isProxy) {
        RemoteBunkerClient.remoteDecrypt(this, cipherText, fromPublicKey, useNip44 = false)
    } else {
        signer.nip04Decrypt(cipherText, fromPublicKey)
    }

    fun nip44v3Encrypt(plainText: ByteArray, toPublicKey: String, kind: Int, scope: String): String {
        check(!isProxy) { "Bunker proxy accounts cannot perform NIP-44v3 encryption locally" }
        return Nip44v3.encrypt(plainText, signer.keyPair.privKey!!, toPublicKey.hexToByteArray(), kind, scope)
    }

    fun nip44v3Decrypt(cipherText: String, fromPublicKey: String, kind: Int, scope: String): ByteArray {
        check(!isProxy) { "Bunker proxy accounts cannot perform NIP-44v3 decryption locally" }
        return Nip44v3.decrypt(cipherText, signer.keyPair.privKey!!, fromPublicKey.hexToByteArray(), kind, scope)
    }

    suspend fun decrypt(encryptedContent: String, fromPublicKey: String): String = if (isProxy) {
        RemoteBunkerClient.remoteDecryptAuto(this, encryptedContent, fromPublicKey)
    } else {
        signer.decrypt(encryptedContent, fromPublicKey)
    }

    suspend fun signPsbt(psbtHex: String): String = if (isProxy) {
        RemoteBunkerClient.remoteSignPsbt(this, psbtHex)
    } else {
        signer.signPsbt(psbtHex)
    }

    suspend fun seedWords(): String {
        if (isProxy) return ""
        return runCatching { DataStoreAccess.getEncryptedKey(Amber.instance, npub, DataStoreAccess.SEED_WORDS) }.getOrNull() ?: ""
    }

    fun decryptZapEvent(
        data: String,
    ): String? {
        if (isProxy) {
            return runCatching {
                runBlocking { RemoteBunkerClient.remoteDecryptZapEvent(this@Account, data) }
            }.getOrNull()
        }
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
        check(!isProxy) { "Bunker proxy accounts cannot build NIP-17 messages locally" }
        return NIP17Factory().createMessageNIP17(template, signer)
    }

    suspend fun getNsec(): String {
        check(!isProxy) { "Bunker proxy accounts have no local nsec" }
        return signer.keyPair.privKey!!.toNsec()
    }

    fun copyToClipboard(clipboardManager: Clipboard) {
        if (isProxy) {
            Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                Toast.makeText(
                    Amber.instance,
                    Amber.instance.getString(R.string.app_name),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            return
        }
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
data class ProxyAccountMetadata(
    val remotePubkey: String,
    val relays: List<NormalizedRelayUrl>,
    val bunkerName: String,
    val nostrConnectSecret: String,
)

@Immutable
class AccountState(val account: Account)

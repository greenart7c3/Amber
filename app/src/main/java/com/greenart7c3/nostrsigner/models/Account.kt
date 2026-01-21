package com.greenart7c3.nostrsigner.models

import android.content.ClipData
import android.widget.Toast
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.lifecycle.LiveData
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.DataStoreAccess
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import com.vitorpamplona.quartz.nip55AndroidSigner.signString
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapRequestBuilder
import java.lang.ref.WeakReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Stable
class Account(
    val hexKey: HexKey,
    val npub: String,
    val name: MutableStateFlow<String>,
    val picture: MutableStateFlow<String>,
    var signPolicy: Int,
    var didBackup: Boolean,
) {
    val saveable: AccountLiveData = AccountLiveData(this)

    @Volatile
    private var signerCache: WeakReference<NostrSignerInternal>? = null

    private val signerMutex = Mutex()

    private suspend fun getSigner(): NostrSignerInternal {
        signerCache?.get()?.let { return it }

        return signerMutex.withLock {
            signerCache?.get()?.let { return it }

            val privKey = DataStoreAccess.getEncryptedKey(
                Amber.instance,
                npub,
                DataStoreAccess.NOSTR_PRIVKEY,
            )

            val signer = NostrSignerInternal(
                KeyPair(privKey.hexToByteArray()),
            )

            signerCache = WeakReference(signer)
            signer
        }
    }

    suspend fun <T : Event> sign(eventTemplate: EventTemplate<T>): T {
        val signer = getSigner()
        return signer.sign(eventTemplate)
    }

    fun <T : Event> signSync(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T {
        val signer = runBlocking { getSigner() }
        return signer.signerSync.sign(createdAt, kind, tags, content)
    }

    suspend fun signString(message: String): String {
        val signer = getSigner()
        return signString(message, signer.keyPair.privKey!!).toHexKey()
    }

    suspend fun nip49Encrypt(password: String): String {
        val signer = getSigner()
        return Nip49().encrypt(signer.keyPair.privKey!!.toHexKey(), password)
    }

    suspend fun nip44Encrypt(plainText: String, toPublicKey: String): String {
        val signer = getSigner()
        return signer.signerSync.nip44Encrypt(plainText, toPublicKey)
    }

    suspend fun nip04Encrypt(plainText: String, toPublicKey: String): String {
        val signer = getSigner()
        return signer.signerSync.nip04Encrypt(plainText, toPublicKey)
    }

    suspend fun nip44Decrypt(cipherText: String, fromPublicKey: String): String {
        val signer = getSigner()
        return signer.signerSync.nip44Decrypt(cipherText, fromPublicKey)
    }

    suspend fun nip04Decrypt(cipherText: String, fromPublicKey: String): String {
        val signer = getSigner()
        return signer.signerSync.nip04Decrypt(cipherText, fromPublicKey)
    }

    suspend fun decrypt(encryptedContent: String, fromPublicKey: String): String {
        val signer = getSigner()
        return signer.signerSync.decrypt(encryptedContent, fromPublicKey)
    }

    suspend fun seedWords() = DataStoreAccess.getEncryptedKey(Amber.instance, npub, DataStoreAccess.SEED_WORDS)

    suspend fun decryptZapEvent(
        data: String,
    ): String? {
        val event = Event.fromJson(data) as LnZapRequestEvent
        val signerSync = getSigner().signerSync
        return try {
            PrivateZapRequestBuilder().decryptZapEvent(
                event = event,
                signer = signerSync,
            ).toJson()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun createMessageNIP17(template: EventTemplate<ChatMessageEvent>): NIP17Factory.Result = NIP17Factory().createMessageNIP17(template, getSigner())

    suspend fun getNsec(): String = getSigner().keyPair.privKey!!.toNsec()

    fun copyToClipboard(clipboardManager: Clipboard) {
        Amber.instance.applicationIOScope.launch {
            didBackup = true
            LocalPreferences.saveToEncryptedStorage(Amber.instance, this@Account, null, null, null)
            val nsec = getNsec()
            Amber.instance.applicationIOScope.launch(Dispatchers.Main) {
                clipboardManager.setClipEntry(
                    ClipEntry(
                        ClipData.newPlainText("", nsec),
                    ),
                )

                Toast.makeText(
                    Amber.instance,
                    Amber.instance.getString(R.string.secret_key_copied_to_clipboard),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun clearSignerCache() {
        signerCache?.clear()
        signerCache = null
    }
}

class AccountLiveData(account: Account) : LiveData<AccountState>(AccountState(account))

@Immutable
class AccountState(val account: Account)

package com.greenart7c3.nostrsigner.signer

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.greenart7c3.nostrsigner.DataStoreAccess
import com.greenart7c3.nostrsigner.FailedMigrationException
import com.greenart7c3.nostrsigner.service.nip44v3.Nip44v3
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip17Dm.NIP17Factory
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapRequestBuilder
import java.security.InvalidKeyException
import java.security.KeyStoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

/**
 * Hosts all private-key cryptographic operations in the isolated `:signer`
 * process (declared with android:process=":signer", android:exported=false).
 *
 * The decrypted key is loaded here via [SignerKeyCache] from the
 * process-independent encrypted storage and never leaves this process — except
 * the deliberate getNsec/seedWords secret return that the UI requires, and the
 * encrypt/decrypt results that are the operation's whole point.
 *
 * Binder calls arrive on background pool threads, so blocking (runBlocking) is
 * safe and is exactly what the synchronous ContentProvider path needs.
 */
class SignerService : Service() {
    private val binder = object : ISignerService.Stub() {
        override fun signEvent(npub: String, createdAt: Long, kind: Int, tagsJson: String, content: CryptoPayload): CryptoPayload = guarded {
            val signer = SignerKeyCache.signerFor(applicationContext, npub)
            CryptoPayload.of(signEvent(signer, createdAt, kind, parseTags(tagsJson), content.readString()))
        }

        override fun nip04Encrypt(npub: String, plainText: CryptoPayload, toPublicKey: String): CryptoPayload = guarded {
            val signer = SignerKeyCache.signerFor(applicationContext, npub)
            CryptoPayload.of(runBlocking { signer.nip04Encrypt(plainText.readString(), toPublicKey) })
        }

        override fun nip04Decrypt(npub: String, cipherText: CryptoPayload, fromPublicKey: String): CryptoPayload = guarded {
            val signer = SignerKeyCache.signerFor(applicationContext, npub)
            CryptoPayload.of(runBlocking { signer.nip04Decrypt(cipherText.readString(), fromPublicKey) })
        }

        override fun nip44Encrypt(npub: String, plainText: CryptoPayload, toPublicKey: String): CryptoPayload = guarded {
            val signer = SignerKeyCache.signerFor(applicationContext, npub)
            CryptoPayload.of(runBlocking { signer.nip44Encrypt(plainText.readString(), toPublicKey) })
        }

        override fun nip44Decrypt(npub: String, cipherText: CryptoPayload, fromPublicKey: String): CryptoPayload = guarded {
            val signer = SignerKeyCache.signerFor(applicationContext, npub)
            CryptoPayload.of(runBlocking { signer.nip44Decrypt(cipherText.readString(), fromPublicKey) })
        }

        override fun decrypt(npub: String, cipherText: CryptoPayload, fromPublicKey: String): CryptoPayload = guarded {
            val signer = SignerKeyCache.signerFor(applicationContext, npub)
            CryptoPayload.of(runBlocking { signer.decrypt(cipherText.readString(), fromPublicKey) })
        }

        override fun nip44v3Encrypt(npub: String, plainText: CryptoPayload, toPublicKey: String, kind: Int, scope: String): CryptoPayload = guarded {
            val signer = SignerKeyCache.signerFor(applicationContext, npub)
            CryptoPayload.of(Nip44v3.encrypt(plainText.readBytes(), signer.keyPair.privKey!!, toPublicKey.hexToByteArray(), kind, scope))
        }

        override fun nip44v3Decrypt(npub: String, cipherText: CryptoPayload, fromPublicKey: String, kind: Int, scope: String): CryptoPayload = guarded {
            val signer = SignerKeyCache.signerFor(applicationContext, npub)
            CryptoPayload.of(Nip44v3.decrypt(cipherText.readString(), signer.keyPair.privKey!!, fromPublicKey.hexToByteArray(), kind, scope))
        }

        override fun signPsbt(npub: String, psbtHex: CryptoPayload): CryptoPayload = guarded {
            val signer = SignerKeyCache.signerFor(applicationContext, npub)
            CryptoPayload.of(runBlocking { signer.signPsbt(psbtHex.readString()) })
        }

        override fun decryptZapEvent(npub: String, eventJson: CryptoPayload): CryptoPayload = guarded {
            val signer = SignerKeyCache.signerFor(applicationContext, npub)
            val result = decryptZap(signer, eventJson.readString())
                ?: throw IllegalStateException(SignerErrorCodes.encode(SignerErrorCodes.NULL_RESULT, "null"))
            CryptoPayload.of(result)
        }

        override fun nip49Encrypt(npub: String, password: String): String = guarded {
            val signer = SignerKeyCache.signerFor(applicationContext, npub)
            Nip49().encrypt(signer.keyPair.privKey!!.toHexKey(), password)
        }

        override fun createMessageNIP17(npub: String, createdAt: Long, kind: Int, tagsJson: String, content: CryptoPayload): CryptoPayload = guarded {
            val signer = SignerKeyCache.signerFor(applicationContext, npub)
            val template = EventTemplate<ChatMessageEvent>(createdAt, kind, parseTags(tagsJson), content.readString())
            val result = runBlocking { NIP17Factory().createMessageNIP17(template, signer) }
            // Newline-delimited event JSONs; compact event JSON never contains a raw newline.
            CryptoPayload.of(result.wraps.joinToString("\n") { it.toJson() })
        }

        override fun getNsec(npub: String): String = guarded {
            val signer = SignerKeyCache.signerFor(applicationContext, npub)
            signer.keyPair.privKey!!.toNsec()
        }

        override fun seedWords(npub: String): String = guarded {
            runCatching {
                runBlocking { DataStoreAccess.getEncryptedKey(applicationContext, npub, DataStoreAccess.SEED_WORDS) }
            }.getOrNull() ?: ""
        }

        override fun connSignEvent(connPrivKeyHex: String, createdAt: Long, kind: Int, tagsJson: String, content: CryptoPayload): CryptoPayload = guarded {
            val signer = SignerKeyCache.connectionSigner(connPrivKeyHex)
            CryptoPayload.of(signEvent(signer, createdAt, kind, parseTags(tagsJson), content.readString()))
        }

        override fun connNip04Encrypt(connPrivKeyHex: String, plainText: CryptoPayload, toPublicKey: String): CryptoPayload = guarded {
            val signer = SignerKeyCache.connectionSigner(connPrivKeyHex)
            CryptoPayload.of(runBlocking { signer.nip04Encrypt(plainText.readString(), toPublicKey) })
        }

        override fun connNip44Encrypt(connPrivKeyHex: String, plainText: CryptoPayload, toPublicKey: String): CryptoPayload = guarded {
            val signer = SignerKeyCache.connectionSigner(connPrivKeyHex)
            CryptoPayload.of(runBlocking { signer.nip44Encrypt(plainText.readString(), toPublicKey) })
        }

        override fun connDecrypt(connPrivKeyHex: String, cipherText: CryptoPayload, fromPublicKey: String): CryptoPayload = guarded {
            val signer = SignerKeyCache.connectionSigner(connPrivKeyHex)
            CryptoPayload.of(runBlocking { signer.decrypt(cipherText.readString(), fromPublicKey) })
        }

        override fun evict(npub: String) {
            SignerKeyCache.evict(npub)
        }

        override fun evictAll() {
            SignerKeyCache.evictAll()
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun parseTags(tagsJson: String): Array<Array<String>> = if (tagsJson.isBlank()) emptyArray() else JacksonMapper.fromJsonToTagArray(tagsJson)

    private fun signEvent(signer: NostrSignerInternal, createdAt: Long, kind: Int, tags: Array<Array<String>>, content: String): String = signer.signerSync.sign<Event>(createdAt, kind, tags, content).toJson()

    private fun decryptZap(signer: NostrSignerInternal, data: String): String? {
        val event = Event.fromJson(data) as LnZapRequestEvent
        return try {
            PrivateZapRequestBuilder().decryptZapEvent(event = event, signer = signer.signerSync).toJson()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    /**
     * Runs a crypto op and re-maps failures to an [IllegalStateException] whose
     * message carries a [SignerErrorCodes] value, so the main-process facade can
     * reconstruct the right domain semantics. Key material never appears in the
     * message — only the exception class name.
     */
    private inline fun <T> guarded(block: () -> T): T {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            // Already an encoded signer error (or an unencoded ISE we pass through).
            throw e
        } catch (e: InvalidKeyException) {
            throw IllegalStateException(SignerErrorCodes.encode(SignerErrorCodes.KEYSTORE_FAILED, e.javaClass.simpleName))
        } catch (e: KeyStoreException) {
            throw IllegalStateException(SignerErrorCodes.encode(SignerErrorCodes.KEYSTORE_FAILED, e.javaClass.simpleName))
        } catch (e: FailedMigrationException) {
            throw IllegalStateException(SignerErrorCodes.encode(SignerErrorCodes.NO_KEY, "no key"))
        } catch (e: Exception) {
            throw IllegalStateException(SignerErrorCodes.encode(SignerErrorCodes.GENERIC, e.javaClass.simpleName))
        }
    }
}

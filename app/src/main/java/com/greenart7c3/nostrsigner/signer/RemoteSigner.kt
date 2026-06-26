package com.greenart7c3.nostrsigner.signer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A binder/crypto failure that is not an expected domain outcome. */
class SignerException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Signals that the operation legitimately produced null (e.g. decryptZapEvent). */
class SignerNullResultException : RuntimeException()

/**
 * Main-process facade over the isolated [SignerService]. Every private-key
 * operation in the app funnels through here; the decrypted key only ever lives
 * in the `:signer` process. Method shapes mirror the old in-process Account
 * crypto methods so call sites change minimally.
 *
 * Binding is eager (from Amber.onCreate in the main process) but callers may
 * arrive on a binder thread before it completes (the ContentProvider path), so
 * [svc] blocks on a latch — safe because those callers are never on the UI
 * thread.
 */
object RemoteSigner {
    private const val BIND_TIMEOUT_SECONDS = 8L

    @Volatile private var service: ISignerService? = null

    @Volatile private var latch = CountDownLatch(1)

    @Volatile private var appContext: Context? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = ISignerService.Stub.asInterface(binder)
            latch.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            latch = CountDownLatch(1)
        }

        override fun onBindingDied(name: ComponentName?) {
            AmberLog.w(Amber.TAG, "Signer service binding died; rebinding")
            service = null
            latch = CountDownLatch(1)
            synchronized(this@RemoteSigner) { bound = false }
            appContext?.let { bind(it) }
        }
    }

    /** Idempotently binds to the signer service. Safe to call from any main-process entry point. */
    @Synchronized
    fun bind(context: Context) {
        appContext = context.applicationContext
        if (bound) return
        val intent = Intent(appContext, SignerService::class.java)
        bound = appContext!!.bindService(intent, connection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
        if (!bound) AmberLog.e(Amber.TAG, "Failed to bind signer service")
    }

    private fun svc(): ISignerService {
        service?.let { return it }
        appContext?.let { bind(it) }
        if (!latch.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw SignerException("Signer service not available (bind timed out)")
        }
        return service ?: throw SignerException("Signer service not available")
    }

    private inline fun <T> remote(npub: String?, block: (ISignerService) -> T): T {
        val s = svc()
        try {
            return block(s)
        } catch (e: RemoteException) {
            service = null
            latch = CountDownLatch(1)
            throw SignerException("Signer process died during request", e)
        } catch (e: IllegalStateException) {
            throw decode(npub, e)
        }
    }

    // --- account-key operations -------------------------------------------------

    fun signEvent(npub: String, createdAt: Long, kind: Int, tags: Array<Array<String>>, content: String): String = remote(npub) { it.signEvent(npub, createdAt, kind, JacksonMapper.toJson(tags), CryptoPayload.of(content)).readString() }

    fun nip04Encrypt(npub: String, plainText: String, toPublicKey: String): String = remote(npub) { it.nip04Encrypt(npub, CryptoPayload.of(plainText), toPublicKey).readString() }

    fun nip04Decrypt(npub: String, cipherText: String, fromPublicKey: String): String = remote(npub) { it.nip04Decrypt(npub, CryptoPayload.of(cipherText), fromPublicKey).readString() }

    fun nip44Encrypt(npub: String, plainText: String, toPublicKey: String): String = remote(npub) { it.nip44Encrypt(npub, CryptoPayload.of(plainText), toPublicKey).readString() }

    fun nip44Decrypt(npub: String, cipherText: String, fromPublicKey: String): String = remote(npub) { it.nip44Decrypt(npub, CryptoPayload.of(cipherText), fromPublicKey).readString() }

    fun decrypt(npub: String, cipherText: String, fromPublicKey: String): String = remote(npub) { it.decrypt(npub, CryptoPayload.of(cipherText), fromPublicKey).readString() }

    fun nip44v3Encrypt(npub: String, plainText: ByteArray, toPublicKey: String, kind: Int, scope: String): String = remote(npub) { it.nip44v3Encrypt(npub, CryptoPayload.of(plainText), toPublicKey, kind, scope).readString() }

    fun nip44v3Decrypt(npub: String, cipherText: String, fromPublicKey: String, kind: Int, scope: String): ByteArray = remote(npub) { it.nip44v3Decrypt(npub, CryptoPayload.of(cipherText), fromPublicKey, kind, scope).readBytes() }

    fun signPsbt(npub: String, psbtHex: String): String = remote(npub) { it.signPsbt(npub, CryptoPayload.of(psbtHex)).readString() }

    /** Returns null when the operation legitimately yields null (matches Account.decryptZapEvent). */
    fun decryptZapEvent(npub: String, eventJson: String): String? = try {
        remote(npub) { it.decryptZapEvent(npub, CryptoPayload.of(eventJson)).readString() }
    } catch (e: SignerNullResultException) {
        null
    }

    fun nip49Encrypt(npub: String, password: String): String = remote(npub) { it.nip49Encrypt(npub, password) }

    fun getNsec(npub: String): String = remote(npub) { it.getNsec(npub) }

    fun seedWords(npub: String): String = remote(npub) { it.seedWords(npub) }

    /** Returns the gift-wrapped events to publish (the only thing callers use). */
    fun createMessageNIP17(npub: String, createdAt: Long, kind: Int, tags: Array<Array<String>>, content: String): List<Event> {
        val jsonl = remote(npub) { it.createMessageNIP17(npub, createdAt, kind, JacksonMapper.toJson(tags), CryptoPayload.of(content)).readString() }
        return jsonl.split("\n").filter { line -> line.isNotBlank() }.map { line -> Event.fromJson(line) }
    }

    // --- connection-scoped (NIP-46 localKey) operations -------------------------

    fun connSignEvent(connPrivKeyHex: String, createdAt: Long, kind: Int, tags: Array<Array<String>>, content: String): String = remote(null) { it.connSignEvent(connPrivKeyHex, createdAt, kind, JacksonMapper.toJson(tags), CryptoPayload.of(content)).readString() }

    fun connNip04Encrypt(connPrivKeyHex: String, plainText: String, toPublicKey: String): String = remote(null) { it.connNip04Encrypt(connPrivKeyHex, CryptoPayload.of(plainText), toPublicKey).readString() }

    fun connNip44Encrypt(connPrivKeyHex: String, plainText: String, toPublicKey: String): String = remote(null) { it.connNip44Encrypt(connPrivKeyHex, CryptoPayload.of(plainText), toPublicKey).readString() }

    fun connDecrypt(connPrivKeyHex: String, cipherText: String, fromPublicKey: String): String = remote(null) { it.connDecrypt(connPrivKeyHex, CryptoPayload.of(cipherText), fromPublicKey).readString() }

    // --- lifecycle (best-effort) ------------------------------------------------

    fun evict(npub: String) {
        runCatching { service?.evict(npub) }
    }

    fun evictAll() {
        runCatching { service?.evictAll() }
    }

    /** Re-maps an encoded remote [IllegalStateException] into a domain exception, recording keystore failures. */
    private fun decode(npub: String?, e: IllegalStateException): RuntimeException = when (SignerErrorCodes.codeOf(e.message)) {
        SignerErrorCodes.NULL_RESULT -> SignerNullResultException()
        SignerErrorCodes.KEYSTORE_FAILED -> {
            if (npub != null) {
                Amber.instance.keystoreFailedAccounts.value = (Amber.instance.keystoreFailedAccounts.value + npub).distinct()
            }
            SignerException("AndroidKeyStore key for $npub is broken", e)
        }
        SignerErrorCodes.NO_KEY -> SignerException("No private key stored for $npub", e)
        else -> SignerException(e.message ?: "Signer crypto error", e)
    }
}

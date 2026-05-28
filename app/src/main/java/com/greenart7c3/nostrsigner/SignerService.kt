package com.greenart7c3.nostrsigner

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import com.greenart7c3.nostrsigner.models.SignerType

/**
 * Bound service exposing [ISignerService] — a synchronous alternative to the
 * [SignerProvider] ContentProvider.
 *
 * Both transports share their business logic in [SignerCore]; this class only
 * resolves the calling package from the binder UID and marshals
 * [SignerCore.SignResult] into the [Bundle] contract documented in the AIDL.
 */
class SignerService : Service() {
    private val binder = object : ISignerService.Stub() {
        override fun signMessage(message: String?, npub: String?): Bundle? = run("SIGN_MESSAGE") { pkg ->
            SignerCore.signMessage(applicationContext, pkg, message ?: return@run null, npub ?: return@run null)
        }

        override fun signEvent(eventJson: String?, npub: String?): Bundle? = run("SIGN_EVENT") { pkg ->
            SignerCore.signEvent(applicationContext, pkg, eventJson ?: return@run null, npub ?: return@run null)
        }

        override fun nip04Encrypt(plaintext: String?, pubKey: String?, npub: String?): Bundle? = encryptOrDecrypt(SignerType.NIP04_ENCRYPT, plaintext, pubKey, npub)

        override fun nip04Decrypt(ciphertext: String?, pubKey: String?, npub: String?): Bundle? = encryptOrDecrypt(SignerType.NIP04_DECRYPT, ciphertext, pubKey, npub)

        override fun nip44Encrypt(plaintext: String?, pubKey: String?, npub: String?): Bundle? = encryptOrDecrypt(SignerType.NIP44_ENCRYPT, plaintext, pubKey, npub)

        override fun nip44Decrypt(ciphertext: String?, pubKey: String?, npub: String?): Bundle? = encryptOrDecrypt(SignerType.NIP44_DECRYPT, ciphertext, pubKey, npub)

        override fun decryptZapEvent(eventJson: String?, pubKey: String?, npub: String?): Bundle? = encryptOrDecrypt(SignerType.DECRYPT_ZAP_EVENT, eventJson, pubKey, npub)

        override fun signPsbt(psbtHex: String?, npub: String?): Bundle? = run("SIGN_PSBT") { pkg ->
            SignerCore.signPsbt(applicationContext, pkg, psbtHex ?: return@run null, npub ?: return@run null)
        }

        override fun ping(npub: String?): Bundle? = run("PING") { pkg ->
            SignerCore.ping(applicationContext, pkg, npub)
        }

        private fun encryptOrDecrypt(
            type: SignerType,
            content: String?,
            pubKey: String?,
            npub: String?,
        ): Bundle? = run(type.toString()) { pkg ->
            SignerCore.encryptOrDecrypt(
                applicationContext,
                pkg,
                type,
                content ?: return@run null,
                pubKey ?: return@run null,
                npub ?: return@run null,
            )
        }
    }

    /**
     * Resolves the caller's package, runs [block], and converts the result into
     * the [Bundle] contract. Returns `null` on any error or missing caller,
     * matching the ContentProvider's `null` cursor behaviour.
     */
    private inline fun run(
        type: String,
        block: (packageName: String) -> SignerCore.SignResult?,
    ): Bundle? {
        val packageName = callingPackageName() ?: run {
            Log.d(Amber.TAG, "No package name")
            return null
        }
        return try {
            block(packageName)?.toBundle()
        } catch (e: Exception) {
            SignerCore.logError(applicationContext, packageName, type, e.message ?: "Error from $packageName")
            null
        }
    }

    private fun callingPackageName(): String? {
        val uid = Binder.getCallingUid()
        return packageManager.getPackagesForUid(uid)?.firstOrNull()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    companion object {
        const val KEY_REJECTED = "rejected"
        const val KEY_SIGNATURE = "signature"
        const val KEY_EVENT = "event"
        const val KEY_RESULT = "result"

        fun SignerCore.SignResult.toBundle(): Bundle = when (this) {
            is SignerCore.SignResult.Rejected -> Bundle().apply { putBoolean(KEY_REJECTED, true) }
            is SignerCore.SignResult.Reply -> Bundle().apply {
                putString(KEY_SIGNATURE, signature)
                putString(KEY_EVENT, event)
                putString(KEY_RESULT, result)
            }
        }
    }
}

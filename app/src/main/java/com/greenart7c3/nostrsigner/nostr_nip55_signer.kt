package com.greenart7c3.nostrsigner

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

@Suppress("ClassName")
class nostr_nip55_signer : Service() {
    override fun onCreate() {
        Log.d("NostrSignerService", "Service class = ${this::class.qualifiedName}")
        super.onCreate()
    }

    private val binder = object : INostrSigner.Stub() {
        override fun getPublicKey(): String? {
            Log.d(Amber.TAG, "getPublicKey")
            val callingPackage = applicationContext.packageManager.getPackagesForUid(getCallingUid())?.firstOrNull()

            Log.d(Amber.TAG, callingPackage ?: "")

            return "123"
        }

        override fun signEvent(unsigned_event: String?): String? {
            Log.d(Amber.TAG, "signEvent")
            return null
        }

        override fun nip44Encrypt(current_user_public_key: String?, public_key: String?, plaintext: String?): String? {
            Log.d(Amber.TAG, "nip44Encrypt")
            return null
        }

        override fun nip44Decrypt(current_user_public_key: String?, public_key: String?, ciphertext: String?): String? {
            Log.d(Amber.TAG, "nip44Decrypt")
            return null
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }
}

package com.greenart7c3.nostrsigner.signer

import android.content.Context
import com.greenart7c3.nostrsigner.DataStoreAccess
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking

/**
 * The ONLY place in the app where decrypted account private keys are
 * materialized into memory — and it lives exclusively in the isolated
 * `:signer` process. The main/UI process never instantiates a
 * NostrSignerInternal from a real account key.
 *
 * Keys are loaded on demand from the process-independent encrypted DataStore
 * (Android Keystore-backed) and cached per npub until evicted on logout.
 */
object SignerKeyCache {
    private val accountSigners = ConcurrentHashMap<String, NostrSignerInternal>()

    fun signerFor(context: Context, npub: String): NostrSignerInternal = accountSigners.getOrPut(npub) {
        val privHex = runBlocking {
            DataStoreAccess.getEncryptedKey(context, npub, DataStoreAccess.NOSTR_PRIVKEY)
        }
        NostrSignerInternal(KeyPair(privKey = privHex.hexToByteArray()))
    }

    /**
     * Connection-scoped (NIP-46 localKey) signer. These keys are short-lived and
     * per-request, so they are built each time rather than cached — but still
     * only ever inside this process.
     */
    fun connectionSigner(connPrivKeyHex: String): NostrSignerInternal = NostrSignerInternal(KeyPair(privKey = connPrivKeyHex.hexToByteArray()))

    fun evict(npub: String) {
        accountSigners.remove(npub)
    }

    fun evictAll() {
        accountSigners.clear()
    }
}

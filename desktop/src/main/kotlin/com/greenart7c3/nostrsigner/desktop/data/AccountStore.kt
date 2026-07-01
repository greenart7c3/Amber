package com.greenart7c3.nostrsigner.desktop.data

import com.greenart7c3.nostrsigner.shared.SecureCryptoHelper
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads/persists the single desktop bunker account's private key, encrypted at rest via [SecureCryptoHelper]. */
object AccountStore {
    private val keyFile get() = AppDataDir.file("account.key")

    suspend fun hasAccount(): Boolean = withContext(Dispatchers.IO) { keyFile.exists() }

    /** Loads the persisted account, or null if none has been set up yet. */
    suspend fun load(): KeyPair? {
        if (!hasAccount()) return null
        val encrypted = withContext(Dispatchers.IO) { keyFile.readText() }
        val privKeyHex = SecureCryptoHelper.decrypt(encrypted)
        return KeyPair(privKey = privKeyHex.hexToByteArray())
    }

    /** Generates a brand-new key and persists it. */
    suspend fun generate(): KeyPair = save(KeyPair())

    /** Imports an existing hex or nsec-decoded private key and persists it. */
    suspend fun import(privKeyHex: String): KeyPair = save(KeyPair(privKey = privKeyHex.hexToByteArray()))

    private suspend fun save(keyPair: KeyPair): KeyPair {
        val privKeyHex = requireNotNull(keyPair.privKey) { "Generated key pair is missing a private key" }.toHexKey()
        val encrypted = SecureCryptoHelper.encrypt(privKeyHex)
        withContext(Dispatchers.IO) { keyFile.writeText(encrypted) }
        return keyPair
    }
}

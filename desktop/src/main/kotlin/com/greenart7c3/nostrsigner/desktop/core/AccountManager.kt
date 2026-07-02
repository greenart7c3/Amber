package com.greenart7c3.nostrsigner.desktop.core

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip06KeyDerivation.Bip39Mnemonics
import com.vitorpamplona.quartz.nip06KeyDerivation.Nip06
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip49PrivKeyEnc.Nip49
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.RandomInstance
import kotlinx.coroutines.flow.MutableStateFlow

/** Desktop counterpart of the Android `Account`. */
class DesktopAccount(
    val signer: NostrSignerInternal,
    val hexKey: String,
    val npub: String,
    val name: MutableStateFlow<String>,
    var signPolicy: Int,
    var didBackup: Boolean,
) {
    fun <T : Event> signSync(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T = signer.signerSync.sign(createdAt, kind, tags, content)

    suspend fun nip44Encrypt(plainText: String, toPublicKey: String): String = signer.nip44Encrypt(plainText, toPublicKey)

    suspend fun nip04Encrypt(plainText: String, toPublicKey: String): String = signer.nip04Encrypt(plainText, toPublicKey)

    suspend fun nip44Decrypt(cipherText: String, fromPublicKey: String): String = signer.nip44Decrypt(cipherText, fromPublicKey)

    suspend fun nip04Decrypt(cipherText: String, fromPublicKey: String): String = signer.nip04Decrypt(cipherText, fromPublicKey)

    suspend fun decrypt(encryptedContent: String, fromPublicKey: String): String = signer.decrypt(encryptedContent, fromPublicKey)

    fun getNsec(): String = signer.keyPair.privKey!!.toNsec()

    fun nip49Encrypt(password: String): String = Nip49().encrypt(signer.keyPair.privKey!!.toHexKey(), password)
}

object AccountManager {
    /**
     * Parses a user-supplied key: nsec, ncryptsec (with [password]),
     * BIP-39 mnemonic, or raw hex. Mirrors `AccountStateViewModel.isValidKey`.
     */
    fun parseKey(key: String, password: String = ""): Result<KeyPair> = runCatching {
        val trimmed = key.trim()
        if (trimmed.startsWith("ncryptsec")) {
            val newKey = Nip49().decrypt(trimmed, password)
            KeyPair(Hex.decode(newKey))
        } else if (trimmed.startsWith("nsec")) {
            KeyPair(privKey = trimmed.bechToBytes())
        } else if (trimmed.contains(" ") && Nip06().isValidMnemonic(trimmed)) {
            KeyPair(privKey = Nip06().privateKeyFromMnemonic(trimmed))
        } else {
            KeyPair(Hex.decode(trimmed))
        }
    }

    fun generateSeedWords(): List<String> {
        while (true) {
            val entropy = RandomInstance.bytes(16)
            val words = Bip39Mnemonics.toMnemonics(entropy)
            if (words.toSet().size == 12) return words
        }
    }

    suspend fun addAccount(
        keyPair: KeyPair,
        name: String = "",
        seedWords: String = "",
        signPolicy: Int = 1,
        didBackup: Boolean = true,
    ): DesktopAccount {
        val npub = keyPair.pubKey.toNpub()
        AccountsStore.upsert(
            AccountRecord(
                npub = npub,
                name = name,
                encryptedPrivKey = DesktopKeyStore.encrypt(keyPair.privKey!!.toHexKey()),
                encryptedSeedWords = if (seedWords.isBlank()) "" else DesktopKeyStore.encrypt(seedWords),
                signPolicy = signPolicy,
                didBackup = didBackup,
            ),
        )
        SettingsStore.update { it.copy(currentAccount = npub) }
        return loadAccount(npub)!!
    }

    suspend fun loadAccount(npub: String): DesktopAccount? {
        val record = AccountsStore.get(npub) ?: return null
        val privKeyHex = try {
            DesktopKeyStore.decrypt(record.encryptedPrivKey)
        } catch (e: Exception) {
            AmberLogger.e("AccountManager", "Failed to decrypt key for $npub", e)
            return null
        }
        val keyPair = KeyPair(privKey = privKeyHex.hexToByteArray())
        return DesktopAccount(
            signer = NostrSignerInternal(keyPair),
            hexKey = keyPair.pubKey.toHexKey(),
            npub = npub,
            name = MutableStateFlow(record.name),
            signPolicy = record.signPolicy,
            didBackup = record.didBackup,
        )
    }

    suspend fun seedWords(npub: String): String {
        val record = AccountsStore.get(npub) ?: return ""
        if (record.encryptedSeedWords.isBlank()) return ""
        return runCatching { DesktopKeyStore.decrypt(record.encryptedSeedWords) }.getOrDefault("")
    }

    fun saveAccountMeta(account: DesktopAccount) {
        AccountsStore.get(account.npub)?.let {
            AccountsStore.upsert(
                it.copy(
                    name = account.name.value,
                    signPolicy = account.signPolicy,
                    didBackup = account.didBackup,
                ),
            )
        }
    }
}

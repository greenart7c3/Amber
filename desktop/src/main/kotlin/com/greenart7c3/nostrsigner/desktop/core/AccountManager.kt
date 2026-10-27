package com.greenart7c3.nostrsigner.desktop.core

import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.Event
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

    /** Zeroes the private key bytes; the account cannot sign afterward. */
    internal fun wipe() {
        signer.keyPair.privKey?.fill(0)
    }
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

    /**
     * Stores [keyPair]'s key and returns the cached account for it. Consumes
     * the key pair: its private key bytes are wiped once they are encrypted,
     * so the only live copy is the cached account's (wiped on lock).
     */
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
                encryptedPrivKey = encryptPrivKey(keyPair.privKey!!, npub),
                encryptedSeedWords = if (seedWords.isBlank()) "" else DesktopKeyStore.encrypt(seedWords, seedContext(npub)),
                signPolicy = signPolicy,
                didBackup = didBackup,
            ),
        )
        keyPair.privKey?.fill(0)
        SettingsStore.update { it.copy(currentAccount = npub) }
        return AmberDesktop.account(npub)!!
    }

    suspend fun loadAccount(npub: String): DesktopAccount? {
        val record = AccountsStore.get(npub) ?: return null
        val privKey = try {
            decryptPrivKey(record.encryptedPrivKey, npub)
        } catch (e: Exception) {
            AmberLogger.e("AccountManager", "Failed to decrypt key for $npub", e)
            return null
        }
        val keyPair = KeyPair(privKey = privKey)
        // Never sign as one identity under another account's name and permissions.
        if (keyPair.pubKey.toNpub() != npub) {
            privKey.fill(0)
            AmberLogger.e("AccountManager", "The stored key for $npub belongs to a different account; refusing to load it")
            return null
        }
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
        return runCatching { DesktopKeyStore.decrypt(record.encryptedSeedWords, seedContext(npub)) }.getOrDefault("")
    }

    // The private key is stored as hex text (the original format). These
    // helpers convert between raw bytes and hex bytes without an intermediate
    // String, so every copy of the key can be wiped.

    private suspend fun encryptPrivKey(raw: ByteArray, npub: String): String {
        val hex = bytesToHex(raw)
        return try {
            DesktopKeyStore.encrypt(hex, privKeyContext(npub))
        } finally {
            hex.fill(0)
        }
    }

    private suspend fun decryptPrivKey(encrypted: String, npub: String): ByteArray = hexToBytes(DesktopKeyStore.decryptToBytes(encrypted, privKeyContext(npub)))

    private const val HEX_DIGITS = "0123456789abcdef"

    private fun bytesToHex(raw: ByteArray): ByteArray {
        val out = ByteArray(raw.size * 2)
        raw.forEachIndexed { i, b ->
            out[i * 2] = HEX_DIGITS[(b.toInt() shr 4) and 0xF].code.toByte()
            out[i * 2 + 1] = HEX_DIGITS[b.toInt() and 0xF].code.toByte()
        }
        return out
    }

    /** Decodes ASCII hex and wipes [hex]. */
    private fun hexToBytes(hex: ByteArray): ByteArray {
        try {
            require(hex.size % 2 == 0) { "Odd-length hex key" }
            val out = ByteArray(hex.size / 2)
            for (i in out.indices) {
                val hi = Character.digit(hex[i * 2].toInt(), 16)
                val lo = Character.digit(hex[i * 2 + 1].toInt(), 16)
                if (hi < 0 || lo < 0) {
                    out.fill(0)
                    throw IllegalArgumentException("Invalid hex key")
                }
                out[i] = ((hi shl 4) or lo).toByte()
            }
            return out
        } finally {
            hex.fill(0)
        }
    }

    private fun privKeyContext(npub: String) = "$npub/privkey"

    private fun seedContext(npub: String) = "$npub/seed"

    fun saveAccountMeta(account: DesktopAccount) {
        AccountsStore.update(account.npub) {
            it.copy(
                name = account.name.value,
                signPolicy = account.signPolicy,
                didBackup = account.didBackup,
            )
        }
    }
}

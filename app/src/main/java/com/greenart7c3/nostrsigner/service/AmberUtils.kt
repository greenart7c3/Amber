package com.greenart7c3.nostrsigner.service

import android.util.Log
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.Nip44Version
import com.vitorpamplona.quartz.crypto.decodeNIP44
import com.vitorpamplona.quartz.crypto.encodeNIP44
import com.vitorpamplona.quartz.encoders.Bech32
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.LnZapPrivateEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import fr.acinq.secp256k1.Hex
import java.nio.charset.Charset
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AmberUtils {
    fun getSignedEvent(unsignedEvent: AmberEvent, privateKey: ByteArray): AmberEvent {
        val id = unsignedEvent.id.hexToByteArray()
        val sig = CryptoUtils.sign(id, privateKey).toHexKey()
        return AmberEvent(
            unsignedEvent.id,
            unsignedEvent.pubKey,
            unsignedEvent.createdAt,
            unsignedEvent.kind,
            unsignedEvent.tags,
            unsignedEvent.content,
            sig
        )
    }
    fun getZapRequestEvent(localEvent: LnZapRequestEvent, privateKey: ByteArray): LnZapRequestEvent {
        val originalNoteId = localEvent.zappedPost().firstOrNull()
        val pubkey = localEvent.zappedAuthor()[0]
        var privkey = privateKey
        val encryptionPrivateKey = LnZapRequestEvent.createEncryptionPrivateKey(
            privkey.toHexKey(),
            originalNoteId ?: pubkey,
            localEvent.createdAt
        )
        val noteJson = (LnZapPrivateEvent.create(privkey, listOf(localEvent.tags[0], localEvent.tags[1]), localEvent.content)).toJson()
        val encryptedContent = encryptPrivateZapMessage(
            noteJson,
            encryptionPrivateKey,
            pubkey.hexToByteArray()
        )
        var tags = localEvent.tags.filter { !it.contains("anon") }
        tags = tags + listOf(listOf("anon", encryptedContent))
        privkey = encryptionPrivateKey // sign event with generated privkey
        val pubKey = CryptoUtils.pubkeyCreate(encryptionPrivateKey).toHexKey() // updated event with according pubkey

        val id = AmberEvent.generateId(
            pubKey,
            localEvent.createdAt,
            LnZapRequestEvent.kind,
            tags,
            ""
        )
        val sig = CryptoUtils.sign(id, privkey)
        return LnZapRequestEvent(id.toHexKey(), pubKey, localEvent.createdAt, tags, "", sig.toHexKey())
    }
    fun encryptOrDecryptData(data: String, type: SignerType, account: Account, pubKey: HexKey): String? {
        return when (type) {
            SignerType.DECRYPT_ZAP_EVENT -> {
                decryptZapEvent(data, account)
            }
            SignerType.NIP04_DECRYPT -> {
                CryptoUtils.decryptNIP04(
                    data,
                    account.keyPair.privKey,
                    Hex.decode(pubKey)
                )
            }
            SignerType.NIP04_ENCRYPT -> {
                CryptoUtils.encryptNIP04(
                    data,
                    account.keyPair.privKey,
                    Hex.decode(pubKey)
                )
            }
            SignerType.NIP44_ENCRYPT -> {
                val sharedSecret = CryptoUtils.getSharedSecretNIP44(
                    account.keyPair.privKey,
                    pubKey.hexToByteArray()
                )

                encodeNIP44(
                    CryptoUtils.encryptNIP44(
                        data,
                        sharedSecret
                    )
                )
            }
            else -> {
                val toDecrypt = decodeNIP44(data) ?: return null
                when (toDecrypt.v) {
                    Nip44Version.NIP04.versionCode -> CryptoUtils.decryptNIP04(
                        toDecrypt,
                        account.keyPair.privKey,
                        pubKey.hexToByteArray()
                    )
                    Nip44Version.NIP44.versionCode -> CryptoUtils.decryptNIP44(
                        toDecrypt,
                        account.keyPair.privKey,
                        pubKey.hexToByteArray()
                    )
                    else -> null
                }
            }
        }
    }
    private fun decryptZapEvent(data: String, account: Account): String? {
        val event = Event.fromJson(data) as LnZapRequestEvent

        val loggedInPrivateKey = account.keyPair.privKey

        return if (event.isPrivateZap()) {
            val recipientPK = event.zappedAuthor().firstOrNull()
            val recipientPost = event.zappedPost().firstOrNull()
            if (recipientPK == account.keyPair.pubKey.toHexKey()) {
                // if the receiver is logged in, these are the params.
                val pubkeyToUse = event.pubKey

                event.getPrivateZapEvent(loggedInPrivateKey, pubkeyToUse)?.toJson() ?: ""
            } else {
                // if the sender is logged in, these are the params
                val altPrivateKeyToUse = if (recipientPost != null) {
                    LnZapRequestEvent.createEncryptionPrivateKey(
                        loggedInPrivateKey.toHexKey(),
                        recipientPost,
                        event.createdAt
                    )
                } else if (recipientPK != null) {
                    LnZapRequestEvent.createEncryptionPrivateKey(
                        loggedInPrivateKey.toHexKey(),
                        recipientPK,
                        event.createdAt
                    )
                } else {
                    null
                }

                try {
                    if (altPrivateKeyToUse != null && recipientPK != null) {
                        val altPubKeyFromPrivate = CryptoUtils.pubkeyCreate(altPrivateKeyToUse).toHexKey()

                        if (altPubKeyFromPrivate == event.pubKey) {
                            val result = event.getPrivateZapEvent(altPrivateKeyToUse, recipientPK)

                            result?.toJson() ?: ""
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e("Account", "Failed to create pubkey for ZapRequest ${event.id}", e)
                    null
                }
            }
        } else {
            null
        }
    }

    private fun encryptPrivateZapMessage(msg: String, privkey: ByteArray, pubkey: ByteArray): String {
        val sharedSecret = CryptoUtils.getSharedSecretNIP04(privkey, pubkey)
        val iv = ByteArray(16)
        SecureRandom().nextBytes(iv)

        val keySpec = SecretKeySpec(sharedSecret, "AES")
        val ivSpec = IvParameterSpec(iv)

        val utf8message = msg.toByteArray(Charset.forName("utf-8"))
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        val encryptedMsg = cipher.doFinal(utf8message)

        val encryptedMsgBech32 = Bech32.encode("pzap", Bech32.eight2five(encryptedMsg), Bech32.Encoding.Bech32)
        val ivBech32 = Bech32.encode("iv", Bech32.eight2five(iv), Bech32.Encoding.Bech32)

        return encryptedMsgBech32 + "_" + ivBech32
    }
}

fun String.toShortenHex(): String {
    if (length <= 16) return this
    return replaceRange(8, length - 8, ":")
}

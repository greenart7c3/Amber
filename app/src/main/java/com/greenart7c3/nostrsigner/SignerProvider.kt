package com.greenart7c3.nostrsigner

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.greenart7c3.nostrsigner.service.toNpub
import com.greenart7c3.nostrsigner.ui.encryptPrivateZapMessage
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.crypto.CryptoUtils.pubkeyCreate
import com.vitorpamplona.quartz.crypto.Nip44Version
import com.vitorpamplona.quartz.crypto.decodeNIP44
import com.vitorpamplona.quartz.crypto.encodeNIP44
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.LnZapPrivateEvent
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import fr.acinq.secp256k1.Hex

class SignerProvider : ContentProvider() {
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        return 0
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun onCreate(): Boolean {
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        return when (uri.toString()) {
            "content://com.greenart7c3.nostrsigner.SIGN_EVENT" -> {
                val packageName = callingPackage ?: return null
                val json = projection?.first() ?: return null
                val account = LocalPreferences.loadFromEncryptedStorage() ?: return null
                val event = Event.fromJson(json)
                val key = "$packageName-SIGN_EVENT-${event.kind}"
                val isRemembered = account.savedApps[key] ?: false
                if (!isRemembered) return null

                if (event is LnZapRequestEvent) {
                    val isPrivateZap = event.tags.any { tag -> tag.any { t -> t == "anon" } }
                    val originalNoteId = event.zappedPost()[0]
                    val pubkey = event.zappedAuthor()[0]
                    var privkey = account.keyPair.privKey
                    if (isPrivateZap) {
                        val encryptionPrivateKey = LnZapRequestEvent.createEncryptionPrivateKey(
                            privkey.toHexKey(),
                            originalNoteId,
                            event.createdAt
                        )
                        val noteJson = (LnZapPrivateEvent.create(privkey, listOf(event.tags[0], event.tags[1]), event.content)).toJson()
                        val encryptedContent = encryptPrivateZapMessage(
                            noteJson,
                            encryptionPrivateKey,
                            pubkey.hexToByteArray()
                        )
                        var tags = event.tags.filter { !it.contains("anon") }
                        tags = tags + listOf(listOf("anon", encryptedContent))
                        privkey = encryptionPrivateKey // sign event with generated privkey
                        val pubKey = pubkeyCreate(
                            encryptionPrivateKey
                        ).toHexKey() // updated event with according pubkey

                        val id = Event.generateId(
                            pubKey,
                            event.createdAt,
                            LnZapRequestEvent.kind,
                            tags,
                            ""
                        )
                        val sig = com.vitorpamplona.quartz.crypto.CryptoUtils.sign(id, privkey)
                        val signedEvent = LnZapRequestEvent(
                            id.toHexKey(),
                            pubKey,
                            event.createdAt,
                            tags,
                            "",
                            sig.toHexKey()
                        )
                        val cursor = MatrixCursor(arrayOf("signature", "event"))
                        cursor.addRow(arrayOf(sig, signedEvent.toJson()))
                        return cursor
                    }
                }

                val id = event.id.hexToByteArray()
                val sig = CryptoUtils.sign(id, account.keyPair.privKey).toHexKey()
                val signedEvent = Event(
                    event.id,
                    event.pubKey,
                    event.createdAt,
                    event.kind,
                    event.tags,
                    event.content,
                    sig
                )
                val cursor = MatrixCursor(arrayOf("signature", "event"))
                cursor.addRow(arrayOf(sig, signedEvent.toJson()))
                return cursor
            }
            "content://com.greenart7c3.nostrsigner.NIP04_DECRYPT" -> {
                val packageName = callingPackage ?: return null
                val encryptedContent = projection?.first() ?: return null
                val key = "$packageName-NIP04_DECRYPT"
                val pubkey = projection[1]
                val account = LocalPreferences.loadFromEncryptedStorage() ?: return null
                val isRemembered = account.savedApps[key] ?: false
                if (!isRemembered) return null

                val decrypted = try {
                    CryptoUtils.decryptNIP04(
                        encryptedContent,
                        account.keyPair.privKey,
                        Hex.decode(pubkey)
                    )
                } catch (e: Exception) {
                    "Could not decrypt the message"
                }
                val cursor = MatrixCursor(arrayOf("signature"))
                cursor.addRow(arrayOf<Any>(decrypted))
                return cursor
            }
            "content://com.greenart7c3.nostrsigner.NIP44_DECRYPT" -> {
                val packageName = callingPackage ?: return null
                val encryptedContent = projection?.first() ?: return null
                val key = "$packageName-NIP44_DECRYPT"
                val pubkey = projection[1]
                val account = LocalPreferences.loadFromEncryptedStorage() ?: return null
                val isRemembered = account.savedApps[key] ?: false
                if (!isRemembered) return null

                val decrypted = try {
                    val toDecrypt = decodeNIP44(encryptedContent) ?: return null
                    when (toDecrypt.v) {
                        Nip44Version.NIP04.versionCode -> com.vitorpamplona.quartz.crypto.CryptoUtils.decryptNIP04(
                            toDecrypt,
                            account.keyPair.privKey,
                            pubkey.hexToByteArray()
                        )
                        Nip44Version.NIP44.versionCode -> com.vitorpamplona.quartz.crypto.CryptoUtils.decryptNIP44(
                            toDecrypt,
                            account.keyPair.privKey,
                            pubkey.hexToByteArray()
                        )
                        else -> null
                    } ?: "Could not decrypt the message"
                } catch (e: Exception) {
                    "Could not decrypt the message"
                }
                val cursor = MatrixCursor(arrayOf("signature"))
                cursor.addRow(arrayOf<Any>(decrypted))
                return cursor
            }
            "content://com.greenart7c3.nostrsigner.NIP04_ENCRYPT" -> {
                val packageName = callingPackage ?: return null
                val decryptedContent = projection?.first() ?: return null
                val key = "$packageName-NIP04_ENCRYPT"
                val pubkey = projection[1]
                val account = LocalPreferences.loadFromEncryptedStorage() ?: return null
                val isRemembered = account.savedApps[key] ?: false
                if (!isRemembered) return null

                val encrypted = CryptoUtils.encryptNIP04(
                    decryptedContent,
                    account.keyPair.privKey,
                    Hex.decode(pubkey)
                )

                val cursor = MatrixCursor(arrayOf("signature"))
                cursor.addRow(arrayOf<Any>(encrypted))
                return cursor
            }
            "content://com.greenart7c3.nostrsigner.GET_PUBLIC_KEY" -> {
                val packageName = callingPackage ?: return null
                val key = "$packageName-GET_PUBLIC_KEY"
                val account = LocalPreferences.loadFromEncryptedStorage() ?: return null
                val isRemembered = account.savedApps[key] ?: false
                if (!isRemembered) return null

                val cursor = MatrixCursor(arrayOf("signature"))
                cursor.addRow(arrayOf<Any>(account.keyPair.pubKey.toNpub()))
                return cursor
            }
            "content://com.greenart7c3.nostrsigner.NIP44_ENCRYPT" -> {
                val packageName = callingPackage ?: return null
                val decryptedContent = projection?.first() ?: return null
                val key = "$packageName-NIP44_ENCRYPT"
                val pubkey = projection[1]
                val account = LocalPreferences.loadFromEncryptedStorage() ?: return null
                val isRemembered = account.savedApps[key] ?: false
                if (!isRemembered) return null

                val sharedSecret = CryptoUtils.getSharedSecretNIP44(
                    account.keyPair.privKey,
                    pubkey.hexToByteArray()
                )

                val encrypted = encodeNIP44(
                    CryptoUtils.encryptNIP44(
                        decryptedContent,
                        sharedSecret
                    )
                )

                val cursor = MatrixCursor(arrayOf("signature"))
                cursor.addRow(arrayOf<Any>(encrypted))
                return cursor
            }
            "content://com.greenart7c3.nostrsigner.DECRYPT_ZAP_EVENT" -> {
                val packageName = callingPackage ?: return null
                val encryptedContent = projection?.first() ?: return null
                val key = "$packageName-DECRYPT_ZAP_EVENT"
                val account = LocalPreferences.loadFromEncryptedStorage() ?: return null
                val isRemembered = account.savedApps[key] ?: false
                if (!isRemembered) return null

                val event = Event.fromJson(encryptedContent) as LnZapRequestEvent
                val loggedInPrivateKey = account.keyPair.privKey

                val decrypted = try {
                    if (event.isPrivateZap()) {
                        val recipientPK = event.zappedAuthor().firstOrNull()
                        val recipientPost = event.zappedPost().firstOrNull()
                        if (recipientPK == account.keyPair.pubKey.toHexKey()) {
                            // if the receiver is logged in, these are the params.
                            val privateKeyToUse = loggedInPrivateKey
                            val pubkeyToUse = event.pubKey

                            event.getPrivateZapEvent(privateKeyToUse, pubkeyToUse)?.toJson() ?: ""
                        } else {
                            // if the sender is logged in, these are the params
                            val altPubkeyToUse = recipientPK
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
                                if (altPrivateKeyToUse != null && altPubkeyToUse != null) {
                                    val altPubKeyFromPrivate = pubkeyCreate(altPrivateKeyToUse).toHexKey()

                                    if (altPubKeyFromPrivate == event.pubKey) {
                                        val result = event.getPrivateZapEvent(altPrivateKeyToUse, altPubkeyToUse)

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
                } catch (e: Exception) {
                    "Could not decrypt the message"
                } ?: "Could not decrypt the message"
                val cursor = MatrixCursor(arrayOf("signature"))
                cursor.addRow(arrayOf<Any>(decrypted))
                return cursor
            }
            else -> null
        }
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        return 0
    }
}

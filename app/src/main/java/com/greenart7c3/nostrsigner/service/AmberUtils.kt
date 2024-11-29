package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.util.Log
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.BunkerRequest
import com.greenart7c3.nostrsigner.models.BunkerResponse
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.kindToNip
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import fr.acinq.secp256k1.Hex

object AmberUtils {
    fun encryptOrDecryptData(
        data: String,
        type: SignerType,
        account: Account,
        pubKey: HexKey,
    ): String? {
        return when (type) {
            SignerType.DECRYPT_ZAP_EVENT -> {
                decryptZapEvent(data, account)
            }
            SignerType.NIP04_DECRYPT -> {
                CryptoUtils.decryptNIP04(
                    data,
                    account.signer.keyPair.privKey!!,
                    Hex.decode(pubKey),
                )
            }
            SignerType.NIP04_ENCRYPT -> {
                CryptoUtils.encryptNIP04(
                    data,
                    account.signer.keyPair.privKey!!,
                    Hex.decode(pubKey),
                )
            }
            SignerType.NIP44_ENCRYPT -> {
                CryptoUtils.encryptNIP44(
                    data,
                    account.signer.keyPair.privKey!!,
                    pubKey.hexToByteArray(),
                ).encodePayload()
            }
            else -> {
                CryptoUtils.decryptNIP44(
                    data,
                    account.signer.keyPair.privKey!!,
                    pubKey.hexToByteArray(),
                )
            }
        }
    }

    private fun decryptZapEvent(
        data: String,
        account: Account,
    ): String? {
        val event = Event.fromJson(data) as LnZapRequestEvent

        val loggedInPrivateKey = account.signer.keyPair.privKey

        return if (event.isPrivateZap()) {
            val recipientPK = event.zappedAuthor().firstOrNull()
            val recipientPost = event.zappedPost().firstOrNull()
            if (recipientPK == account.signer.keyPair.pubKey.toHexKey()) {
                // if the receiver is logged in, these are the params.
                val pubkeyToUse = event.pubKey

                event.getPrivateZapEvent(loggedInPrivateKey!!, pubkeyToUse)?.toJson() ?: ""
            } else {
                // if the sender is logged in, these are the params
                val altPrivateKeyToUse =
                    if (recipientPost != null) {
                        LnZapRequestEvent.createEncryptionPrivateKey(
                            loggedInPrivateKey!!.toHexKey(),
                            recipientPost,
                            event.createdAt,
                        )
                    } else if (recipientPK != null) {
                        LnZapRequestEvent.createEncryptionPrivateKey(
                            loggedInPrivateKey!!.toHexKey(),
                            recipientPK,
                            event.createdAt,
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

    fun sendBunkerError(
        intentData: IntentData,
        account: Account,
        bunkerRequest: BunkerRequest,
        relays: List<RelaySetupInfo>,
        context: Context,
        closeApplication: Boolean,
        onRemoveIntentData: (IntentData) -> Unit,
        onLoading: (Boolean) -> Unit,
    ) {
        IntentUtils.sendBunkerResponse(
            context,
            account,
            bunkerRequest,
            BunkerResponse(bunkerRequest.id, "", "user rejected"),
            relays,
            onLoading = onLoading,
            onDone = {
                onRemoveIntentData(intentData)
                context.getAppCompatActivity()?.intent = null
                if (closeApplication) {
                    context.getAppCompatActivity()?.finish()
                }
            },
        )
    }

    fun acceptOrRejectPermission(
        application: ApplicationWithPermissions,
        key: String,
        intentData: IntentData,
        kind: Int?,
        value: Boolean,
        database: AppDatabase,
    ) {
        val noPermission = application.permissions.none {
            val nip = it.kind?.kindToNip()?.toIntOrNull()
            (it.type == intentData.type.toString() && it.kind == kind) || (nip != null && it.type == "NIP" && it.kind == nip)
        }
        if (noPermission) {
            application.permissions.add(
                ApplicationPermissionsEntity(
                    null,
                    key,
                    intentData.type.toString(),
                    kind,
                    value,
                ),
            )

            database
                .applicationDao()
                .insertApplicationWithPermissions(application)
        }
    }
}

fun String.toShortenHex(): String {
    if (length <= 16) return this
    return replaceRange(8, length - 8, ":")
}

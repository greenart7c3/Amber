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
import com.greenart7c3.nostrsigner.ui.IntentResultType
import com.vitorpamplona.ammolite.relays.RelaySetupInfo
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01
import com.vitorpamplona.quartz.nip04Dm.crypto.Nip04
import com.vitorpamplona.quartz.nip44Encryption.Nip44
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEncryption
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
                Nip04.decrypt(
                    data,
                    account.signer.keyPair.privKey!!,
                    Hex.decode(pubKey),
                )
            }
            SignerType.NIP04_ENCRYPT -> {
                Nip04.encrypt(
                    data,
                    account.signer.keyPair.privKey!!,
                    Hex.decode(pubKey),
                )
            }
            SignerType.NIP44_ENCRYPT -> {
                Nip44.encrypt(
                    data,
                    account.signer.keyPair.privKey!!,
                    pubKey.hexToByteArray(),
                ).encodePayload()
            }
            else -> {
                Nip44.decrypt(
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
            if (recipientPK == account.hexKey) {
                // if the receiver is logged in, these are the params.
                val pubkeyToUse = event.pubKey

                event.getPrivateZapEvent(loggedInPrivateKey!!, pubkeyToUse)?.toJson() ?: ""
            } else {
                // if the sender is logged in, these are the params
                val altPrivateKeyToUse =
                    if (recipientPost != null) {
                        PrivateZapEncryption.createEncryptionPrivateKey(
                            loggedInPrivateKey!!.toHexKey(),
                            recipientPost,
                            event.createdAt,
                        )
                    } else if (recipientPK != null) {
                        PrivateZapEncryption.createEncryptionPrivateKey(
                            loggedInPrivateKey!!.toHexKey(),
                            recipientPK,
                            event.createdAt,
                        )
                    } else {
                        null
                    }

                try {
                    if (altPrivateKeyToUse != null && recipientPK != null) {
                        val altPubKeyFromPrivate = Nip01.pubKeyCreate(altPrivateKeyToUse).toHexKey()

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
        onRemoveIntentData: (List<IntentData>, IntentResultType) -> Unit,
        onLoading: (Boolean) -> Unit,
    ) {
        BunkerRequestUtils.sendBunkerResponse(
            context,
            account,
            bunkerRequest,
            BunkerResponse(bunkerRequest.id, "", "user rejected"),
            relays,
            onLoading = onLoading,
            onDone = { result ->
                if (!result) {
                    onLoading(false)
                } else {
                    onRemoveIntentData(listOf(intentData), IntentResultType.REMOVE)
                    context.getAppCompatActivity()?.intent = null
                    if (closeApplication) {
                        context.getAppCompatActivity()?.finish()
                    }
                }
            },
        )
    }

    suspend fun acceptOrRejectPermission(
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

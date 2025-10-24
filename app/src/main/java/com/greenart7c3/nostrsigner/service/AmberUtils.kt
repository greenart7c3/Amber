package com.greenart7c3.nostrsigner.service

import android.content.Context
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.basicPermissions
import com.greenart7c3.nostrsigner.ui.RememberType
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip04Dm.crypto.Nip04
import com.vitorpamplona.quartz.nip44Encryption.Nip44
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapRequestBuilder
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException

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
        return try {
            PrivateZapRequestBuilder().decryptZapEvent(
                event = event,
                signer = account.signer.signerSync,
            ).toJson()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun sendBunkerError(
        account: Account,
        bunkerRequest: AmberBunkerRequest,
        relays: List<NormalizedRelayUrl>,
        context: Context,
        closeApplication: Boolean,
        onLoading: (Boolean) -> Unit,
    ) {
        BunkerRequestUtils.sendBunkerResponse(
            context,
            account,
            bunkerRequest,
            BunkerResponse(bunkerRequest.request.id, "", "user rejected"),
            relays,
            onLoading = onLoading,
            onDone = { result ->
                if (!result) {
                    onLoading(false)
                } else {
                    BunkerRequestUtils.clearRequests()
                    EventNotificationConsumer(context).notificationManager().cancelAll()
                    val activity = Amber.instance.getMainActivity()
                    activity?.intent = null
                    if (closeApplication) {
                        activity?.finish()
                    }
                }
            },
        )
    }

    suspend fun acceptOrRejectPermission(
        application: ApplicationWithPermissions,
        key: String,
        signerType: SignerType,
        kind: Int?,
        value: Boolean,
        rememberType: RememberType,
        account: Account,
    ) {
        val until = when (rememberType) {
            RememberType.ALWAYS -> Long.MAX_VALUE / 1000
            RememberType.ONE_MINUTE -> TimeUtils.oneMinuteFromNow()
            RememberType.FIVE_MINUTES -> TimeUtils.now() + TimeUtils.FIVE_MINUTES
            RememberType.TEN_MINUTES -> TimeUtils.now() + TimeUtils.FIFTEEN_MINUTES
            RememberType.NEVER -> 0L
        }

        if (kind != null) {
            application.permissions.removeIf { it.kind == kind && it.type == signerType.toString() }
        } else {
            application.permissions.removeIf { it.type == signerType.toString() && it.type != "SIGN_EVENT" }
        }

        application.permissions.add(
            ApplicationPermissionsEntity(
                null,
                key,
                signerType.toString(),
                kind,
                value,
                rememberType.screenCode,
                if (value) until else 0L,
                if (!value) until else 0L,
            ),
        )

        Amber.instance.getDatabase(account.npub)
            .dao()
            .insertApplicationWithPermissions(application)
    }

    fun configureSignPolicy(
        application: ApplicationWithPermissions,
        signPolicy: Int,
        key: String,
        permissions: List<Permission>?,
    ) {
        when (signPolicy) {
            0 -> {
                application.application.signPolicy = 0
                basicPermissions.forEach {
                    if (application.permissions.any { permission -> permission.type == it.type.toUpperCase(Locale.current) && permission.kind == it.kind }) {
                        return@forEach
                    }
                    application.permissions.add(
                        ApplicationPermissionsEntity(
                            null,
                            key,
                            it.type.toUpperCase(Locale.current),
                            it.kind,
                            true,
                            RememberType.ALWAYS.screenCode,
                            Long.MAX_VALUE / 1000,
                            0,
                        ),
                    )
                }
            }
            1 -> {
                application.application.signPolicy = 1
                permissions?.filter { it.checked }?.forEach {
                    if (application.permissions.any { permission -> permission.type == it.type.toUpperCase(Locale.current) && permission.kind == it.kind }) {
                        return@forEach
                    }
                    application.permissions.add(
                        ApplicationPermissionsEntity(
                            null,
                            key,
                            it.type.toUpperCase(Locale.current),
                            it.kind,
                            true,
                            RememberType.ALWAYS.screenCode,
                            Long.MAX_VALUE / 1000,
                            0,
                        ),
                    )
                }
            }
            2 -> {
                application.application.signPolicy = 2
            }
        }
    }

    fun acceptPermission(
        application: ApplicationWithPermissions,
        key: String,
        type: SignerType,
        kind: Int?,
        rememberType: RememberType,
    ) {
        val until = when (rememberType) {
            RememberType.ALWAYS -> Long.MAX_VALUE / 1000
            RememberType.ONE_MINUTE -> TimeUtils.oneMinuteFromNow()
            RememberType.FIVE_MINUTES -> TimeUtils.now() + TimeUtils.FIVE_MINUTES
            RememberType.TEN_MINUTES -> TimeUtils.now() + TimeUtils.FIFTEEN_MINUTES
            else -> 0L
        }

        if (kind != null) {
            application.permissions.removeIf { it.kind == kind && it.type == type.toString() }
        } else {
            application.permissions.removeIf { it.type == type.toString() && it.type != "SIGN_EVENT" }
        }

        application.permissions.add(
            ApplicationPermissionsEntity(
                null,
                key,
                type.toString(),
                kind,
                true,
                rememberType.screenCode,
                until,
                0,
            ),
        )
    }
}

fun String.toShortenHex(): String {
    if (length <= 16) return this
    return replaceRange(8, length - 8, ":")
}

fun isPrivateEvent(
    kind: Int,
    tags: Array<Array<String>>,
): Boolean {
    return kind == LnZapRequestEvent.KIND && tags.any { t -> t.size > 1 && t[0] == "anon" }
}

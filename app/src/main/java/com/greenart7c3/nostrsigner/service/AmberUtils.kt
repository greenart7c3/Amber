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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent
import com.vitorpamplona.quartz.utils.TimeUtils

object AmberUtils {
    suspend fun encryptOrDecryptData(
        data: String,
        type: SignerType,
        account: Account,
        pubKey: HexKey,
    ): String? = when (type) {
        SignerType.DECRYPT_ZAP_EVENT -> {
            account.decryptZapEvent(data)
        }
        SignerType.NIP04_DECRYPT -> {
            account.nip04Decrypt(data, pubKey)
        }
        SignerType.NIP04_ENCRYPT -> {
            account.nip04Encrypt(data, pubKey)
        }
        SignerType.NIP44_ENCRYPT -> {
            account.nip44Encrypt(data, pubKey)
        }
        else -> {
            account.nip44Decrypt(data, pubKey)
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
): Boolean = kind == LnZapRequestEvent.KIND && tags.any { t -> t.size > 1 && t[0] == "anon" }

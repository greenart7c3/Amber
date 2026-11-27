package com.greenart7c3.nostrsigner.service

import android.app.NotificationManager
import android.content.Context
import androidx.core.net.toUri
import com.greenart7c3.nostrsigner.BuildConfig
import com.greenart7c3.nostrsigner.SignerProviderQuery
import com.greenart7c3.nostrsigner.common.NIP44V3_DECRYPT_FAILED
import com.greenart7c3.nostrsigner.common.NIP44V3_KIND_REQUIRED
import com.greenart7c3.nostrsigner.common.Nip46Step
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.nip44v3Kind
import com.greenart7c3.nostrsigner.common.nip44v3Scope
import com.greenart7c3.nostrsigner.common.sanitizeAppName
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.localizedBlocking
import com.greenart7c3.nostrsigner.commonsui.requests
import com.greenart7c3.nostrsigner.commonsui.wants_to_encrypt_a_list_of_tags_with
import com.greenart7c3.nostrsigner.commonsui.wants_to_encrypt_a_text_with
import com.greenart7c3.nostrsigner.commonsui.wants_to_encrypt_event_kind
import com.greenart7c3.nostrsigner.commonsui.wants_to_read_a_list_of_tags_from_encrypted_content
import com.greenart7c3.nostrsigner.commonsui.wants_to_read_a_text_from_encrypted_content
import com.greenart7c3.nostrsigner.commonsui.wants_to_read_from_encrypted_content
import com.greenart7c3.nostrsigner.commonsui.wants_you_to_sign_a
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.models.EventEncryptedDataKind
import com.greenart7c3.nostrsigner.models.TagArrayEncryptedDataKind
import com.greenart7c3.nostrsigner.service.NotificationUtils.sendNotification
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal class Nip46AdmittedRequests(
    private val applicationContext: Context,
    private val services: Nip46RequestServices,
    private val notificationManager: () -> NotificationManager,
    private val onConnectionsChanged: suspend () -> Unit,
) {
    suspend fun answer(a: Admitted, pubKey: String, relayUrl: String) {
        when (val step = a.firstStep) {
            is Nip46Step.SwitchRelays -> switchRelays(a, step.result)
            Nip46Step.Logout -> logout(a)
            else -> if (a.type == SignerType.GET_PUBLIC_KEY) getPublicKey(a) else answerFromGrants(a, pubKey, relayUrl)
        }
    }

    private fun askUser(a: Admitted) {
        val message = generateMessage(a.type, a.amberEvent, a.request, a.event, a.permission, a.applicationWithSecret)
        notificationManager().sendNotification(message, "Bunker", "BunkerID", applicationContext, a.request)
    }

    private suspend fun switchRelays(a: Admitted, result: String?) {
        val permission = a.permission ?: return
        val defaultRelays = services.settingsStore.settings.defaultRelays
        services.reply(a, result) { published ->
            if (published) {
                services.scope.launch {
                    val daos = services.accountRepository.daos(a.acc.npub)
                    daos.applicationWriteDao.delete(permission.application.key)
                    daos.permissionWriteDao.insertApplicationWithPermissions(permission.copy(application = permission.application.copy(relays = defaultRelays)))
                    onConnectionsChanged()
                }
            }
        }
    }

    private suspend fun logout(a: Admitted) {
        val permission = a.permission ?: return
        services.reply(a, Nip46Step.Logout.RESULT) { published ->
            if (published) {
                services.scope.launch {
                    services.accountRepository.daos(a.acc.npub).applicationWriteDao.delete(permission.application.key)
                    services.accountRepository.getHistoryDatabase(a.acc.npub).dao().deleteHistory(permission.application.key)
                    BunkerRequestUtils.removeAllForClient(applicationContext, permission.application.key)
                    onConnectionsChanged()
                }
            }
        }
    }

    private suspend fun getPublicKey(a: Admitted) {
        val dao = services.accountRepository.daos(a.acc.npub).permissionLookupDao
        val accepted = isRemembered(dao.getSignPolicy(a.event.pubKey), dao.getPermission(a.event.pubKey, "GET_PUBLIC_KEY"))
        if (accepted == null) {
            askUser(a)
            return
        }
        services.scope.launch {
            services.accountRepository.getHistoryDatabase(a.acc.npub).dao().addHistory(
                listOf(HistoryEntity(0, a.event.pubKey, "GET_PUBLIC_KEY", null, TimeUtils.now(), accepted)),
                a.acc.npub,
            )
        }
        if (accepted) services.reply(a, a.acc.hexKey) else services.reply(a, "", "user rejected")
    }

    private suspend fun answerFromGrants(a: Admitted, pubKey: String, relayUrl: String) {
        val bunkerRequest = a.request.request
        val data = BunkerRequestUtils.getDataFromBunker(bunkerRequest)
        val projection = when (a.type) {
            SignerType.PING -> arrayOf(a.acc.npub)
            SignerType.NIP44_V3_ENCRYPT, SignerType.NIP44_V3_DECRYPT -> {
                val kind = bunkerRequest.nip44v3Kind()
                val scope = bunkerRequest.nip44v3Scope()
                validateNip44v3Request(a.type, kind, scope, data, pubKey, a.acc)?.let {
                    services.log("Rejecting invalid NIP-44 v3 request: $it", relayUrl, a.acc.npub)
                    services.reply(a, "", it)
                    return
                }
                arrayOf(data, pubKey, a.acc.npub, kind.toString(), scope)
            }
            else -> arrayOf(data, pubKey, a.acc.npub)
        }
        val cursor = SignerProviderQuery(services.settingsStore, services.accountRepository, services.scope).query(
            context = applicationContext,
            requesterId = a.event.pubKey,
            callerPackageName = null,
            operationUri = "content://${BuildConfig.APPLICATION_ID}.${a.type}".toUri(),
            arguments = projection,
        )
        cursor.use { localCursor ->
            when {
                localCursor == null || !localCursor.moveToFirst() -> askUser(a)
                localCursor.getColumnIndex("rejected") > -1 -> {
                    services.log("User rejected request ${a.id}", relayUrl, a.acc.npub)
                    services.reply(a, "", "user rejected")
                }
                else -> {
                    val index = localCursor.getColumnIndex("event").takeIf { it != -1 } ?: localCursor.getColumnIndex("result")
                    services.reply(a, localCursor.getString(index))
                }
            }
        }
    }
}

private fun generateMessage(
    type: SignerType,
    amberEvent: AmberEvent?,
    request: AmberBunkerRequest,
    event: Event,
    permission: ApplicationWithPermissions?,
    applicationWithSecret: ApplicationWithPermissions?,
): String {
    val name = sequenceOf(permission, applicationWithSecret)
        .map { sanitizeAppName(it?.application?.name ?: "") }
        .firstOrNull { it.isNotBlank() } ?: event.pubKey.toShortenHex()

    val bunkerPermission = Permission(type.toString().lowercase(), amberEvent?.kind)
    var message = "$name  ${getStringBlocking(Res.string.requests)} ${bunkerPermission.localizedBlocking()}"
    if (type == SignerType.SIGN_EVENT) {
        message = "$name ${getStringBlocking(Res.string.wants_you_to_sign_a, bunkerPermission.localizedBlocking())}"
    }
    if (type == SignerType.CONNECT) {
        message = "$name ${bunkerPermission.localizedBlocking()}"
    }

    if (type.name.contains("ENCRYPT")) {
        when (request.encryptedData) {
            is EventEncryptedDataKind -> {
                val permission = Permission("sign_event", request.encryptedData.event.kind)
                message = "$name ${getStringBlocking(Res.string.wants_to_encrypt_event_kind, permission.localizedBlocking(), type.name.split("_").first())}"
            }

            is TagArrayEncryptedDataKind -> {
                message = "$name ${getStringBlocking(Res.string.wants_to_encrypt_a_list_of_tags_with, type.name.split("_").first())}"
            }

            else -> message = "$name ${getStringBlocking(Res.string.wants_to_encrypt_a_text_with, type.name.split("_").first())}"
        }
    } else if (type.name.contains("DECRYPT")) {
        when (request.encryptedData) {
            is EventEncryptedDataKind -> {
                val permission = Permission("sign_event", request.encryptedData.event.kind)
                message = "$name${getStringBlocking(Res.string.wants_to_read_from_encrypted_content, permission.localizedBlocking(), type.name.split("_").first())}"
            }

            is TagArrayEncryptedDataKind -> {
                message = "$name ${getStringBlocking(Res.string.wants_to_read_a_list_of_tags_from_encrypted_content, type.name.split("_").first())}"
            }

            else -> message = "$name ${getStringBlocking(Res.string.wants_to_read_a_text_from_encrypted_content, type.name.split("_").first())}"
        }
    }
    return message
}

private fun validateNip44v3Request(
    type: SignerType,
    kind: Int?,
    scope: String,
    data: String,
    pubKey: String,
    acc: Account,
): String? {
    if (kind == null) return NIP44V3_KIND_REQUIRED
    if (type != SignerType.NIP44_V3_DECRYPT) return null
    return try {
        acc.crypto.nip44v3Decrypt(data, pubKey, kind, scope)
        null
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        val isAccessRefusalNotBadCiphertext = e is SecurityException || e is IllegalStateException
        if (isAccessRefusalNotBadCiphertext) throw e
        NIP44V3_DECRYPT_FAILED
    }
}

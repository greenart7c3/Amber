package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.common.BUNKER_REPLY_ATTEMPTS
import com.greenart7c3.nostrsigner.common.NIP46_INVALID_PARAMS
import com.greenart7c3.nostrsigner.common.Nip46Admission
import com.greenart7c3.nostrsigner.common.Nip46AdmissionReply
import com.greenart7c3.nostrsigner.common.Nip46Connection
import com.greenart7c3.nostrsigner.common.Nip46Step
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.admitNip46Request
import com.greenart7c3.nostrsigner.common.eventParam
import com.greenart7c3.nostrsigner.common.hasRequiredParams
import com.greenart7c3.nostrsigner.common.nip46Step
import com.greenart7c3.nostrsigner.common.peerPubKeyParam
import com.greenart7c3.nostrsigner.common.reply
import com.greenart7c3.nostrsigner.common.zapRequestParam
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.AmberBunkerRequest
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.utils.TimeUtils
import java.util.UUID
import kotlinx.coroutines.launch

internal class RequestParams(val pubKey: String, val amberEvent: AmberEvent?)

@Suppress("LongParameterList")
internal class Admitted(
    val acc: Account,
    val request: AmberBunkerRequest,
    val relays: List<NormalizedRelayUrl>,
    val event: Event,
    val type: SignerType,
    val amberEvent: AmberEvent?,
    val permission: ApplicationWithPermissions?,
    val applicationWithSecret: ApplicationWithPermissions?,
    val firstStep: Nip46Step,
) {
    val id: String get() = request.request.id
}

internal class Nip46RequestAdmission(private val services: Nip46RequestServices) {
    suspend fun readParams(acc: Account, request: AmberBunkerRequest, type: SignerType, relay: NormalizedRelayUrl): RequestParams? {
        val bunkerRequest = request.request
        if (type == SignerType.INVALID) {
            services.log("Invalid request method ${bunkerRequest.method}", relay.url, acc.npub)
            services.refuse(acc, request, "Unrecognized method: ${bunkerRequest.method}")
            return null
        }
        val params = requestParams(bunkerRequest)?.takeIf { bunkerRequest.hasRequiredParams() }
        if (params == null) {
            services.log("Invalid params for request ${bunkerRequest.id} method ${bunkerRequest.method}", relay.url, acc.npub)
            services.refuse(acc, request, NIP46_INVALID_PARAMS)
        }
        return params
    }

    suspend fun admit(
        event: Event,
        acc: Account,
        request: AmberBunkerRequest,
        type: SignerType,
        params: RequestParams,
        relay: NormalizedRelayUrl,
    ): Admitted? {
        val dao = services.accountRepository.daos(acc.npub).applicationDao
        val bunkerRequest = request.request
        val stored = dao.getByKey(event.pubKey)
        if (stored != null && stored.application.isExpired()) {
            services.log("Connection expired for ${event.pubKey}", relay.url, acc.npub)
        }
        val connectSecret = (bunkerRequest as? BunkerRequestConnect)?.let { it.secret ?: UUID.randomUUID().toString() }
        val withSecret = connectSecret?.let { dao.getBySecret(it) }
        val admission = admitNip46Request(type, stored?.toNip46Connection(), request.signerPrivKey, connectSecret, withSecret?.toNip46Connection())
        if (admission !is Nip46Admission.Admit) {
            admission.reply(event.pubKey, bunkerRequest.id, stored?.application?.relays, withSecret?.application?.relays, request.relays)
                ?.let { sendAdmissionReply(it, acc, request, type, params.amberEvent, relay.url) }
            return null
        }
        val permission = stored.takeIf { admission.stored != null }
        val applicationWithSecret = withSecret.takeIf { admission.bySecret != null }
        val relays = permission?.application?.relays ?: applicationWithSecret?.application?.relays ?: request.relays
        val step = nip46Step(bunkerRequest, type, services.settingsStore.settings.authWhitelist, services.settingsStore.settings.defaultRelays.map { it.url })
        if (step is Nip46Step.Refuse) {
            refuseUpFront(acc, request.copy(relays = relays), step, type, params.amberEvent, relay.url)
            return null
        }
        val authorized = request.copy(
            relays = relays,
            signedEvent = (bunkerRequest as? BunkerRequestSign)?.let { acc.sign(it.event) },
            encryptedData = bunkerEncryptedDataKind(bunkerRequest, acc) { services.log(it, relay.url, acc.npub) },
        )
        return Admitted(acc, authorized, relays, event, type, params.amberEvent, permission, applicationWithSecret, step)
    }

    private suspend fun refuseUpFront(
        acc: Account,
        request: AmberBunkerRequest,
        step: Nip46Step.Refuse,
        type: SignerType,
        amberEvent: AmberEvent?,
        relayUrl: String,
    ) {
        services.log(step.log, relayUrl, acc.npub)
        if (step.recordRejected) {
            services.scope.launch {
                services.accountRepository.getHistoryDatabase(acc.npub).dao().addHistory(
                    listOf(HistoryEntity(0, request.localKey, type.toString(), amberEvent?.kind, TimeUtils.now(), false, content = amberEvent?.toEvent()?.toJson() ?: "")),
                    acc.npub,
                )
            }
        }
        services.refuse(acc, request, step.error, attempts = BUNKER_REPLY_ATTEMPTS)
    }

    private suspend fun sendAdmissionReply(
        reply: Nip46AdmissionReply,
        acc: Account,
        request: AmberBunkerRequest,
        type: SignerType,
        amberEvent: AmberEvent?,
        relayUrl: String,
    ) {
        val response = when (reply) {
            is Nip46AdmissionReply.Ack -> {
                services.scope.launch {
                    services.accountRepository.getHistoryDatabase(acc.npub).dao().addHistory(
                        listOf(HistoryEntity(0, reply.connection.key, type.toString().lowercase(), amberEvent?.kind, TimeUtils.now(), true, content = amberEvent?.content ?: "")),
                        acc.npub,
                    )
                }
                Triple(BunkerResponse(request.request.id, reply.result, null), reply.relays, reply.attempts)
            }
            is Nip46AdmissionReply.Refuse -> {
                services.log(reply.log, relayUrl, acc.npub)
                Triple(BunkerResponse(request.request.id, "", reply.error), reply.relays, reply.attempts)
            }
            is Nip46AdmissionReply.Drop -> {
                services.log(reply.log, relayUrl, acc.npub)
                return
            }
        }
        val (bunkerResponse, relays, attempts) = response
        services.bunkerResponder.sendBunkerResponse(acc, request, bunkerResponse, relays, onLoading = {}, onDone = {}, attempts = attempts)
    }
}

private fun requestParams(bunkerRequest: BunkerRequest): RequestParams? {
    val method = bunkerRequest.method
    val amberEvent = if (method == "sign_event") bunkerRequest.eventParam()?.let { runCatching { AmberEvent.fromJson(it) }.getOrNull() } else null
    val pubKey = when {
        method.endsWith("encrypt") || method.endsWith("decrypt") -> bunkerRequest.peerPubKeyParam()
        method == "decrypt_zap_event" -> bunkerRequest.zapRequestParam()
        method == "sign_event" -> amberEvent?.pubKey
        else -> ""
    }
    return pubKey?.let { RequestParams(it, amberEvent) }
}

private fun ApplicationWithPermissions.toNip46Connection() = Nip46Connection(
    key = application.key,
    localKey = application.localKey,
    secret = application.secret,
    useSecret = application.useSecret,
    isConnected = application.isConnected,
    isExpired = application.isExpired(),
)

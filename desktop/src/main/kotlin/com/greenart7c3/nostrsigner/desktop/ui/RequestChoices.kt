package com.greenart7c3.nostrsigner.desktop.ui

import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.EncryptionScope
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.approve
import com.greenart7c3.nostrsigner.desktop.core.reject
import kotlinx.coroutines.flow.MutableStateFlow

/** The choices made on each request card ("Remember", scope, connect options), and answering a request with them. */
object RequestChoices {
    /** Per-request "Remember" choice, shared by the dropdown and the shortcuts. */
    val rememberChoices = MutableStateFlow<Map<String, RememberType>>(emptyMap())

    /**
     * Per-request encrypt/decrypt "Encryption scope". Android defaults NIP-04/44
     * to all methods and NIP-44 v3 to this kind only.
     */
    val scopeChoices = MutableStateFlow<Map<String, EncryptionScope>>(emptyMap())

    fun scopeChoiceFor(request: PendingBunkerRequest): EncryptionScope = scopeChoices.value[request.queueKey] ?: request.defaultScope

    fun setScopeChoice(requestId: String, scope: EncryptionScope) {
        scopeChoices.value += (requestId to scope)
    }

    /**
     * Per-connect-request choices (sign policy, granted permissions, delete
     * after), shared by the request card and the approve shortcut.
     */
    val connectChoices = MutableStateFlow<Map<String, ConnectChoice>>(emptyMap())

    fun connectChoiceFor(request: PendingBunkerRequest): ConnectChoice = connectChoices.value[request.queueKey] ?: ConnectChoice(
        signPolicy = request.account.signPolicy,
        granted = request.requestedPermissions.map { it.copy() },
        accountNpub = request.account.npub,
    )

    fun setConnectChoice(requestId: String, choice: ConnectChoice) {
        connectChoices.value += (requestId to choice)
    }

    /** Approves [request] with the choices made on its card. */
    fun approve(request: PendingBunkerRequest) {
        if (request.type == SignerType.CONNECT) {
            val choice = connectChoiceFor(request)
            AmberDesktop.engine.approve(
                request,
                RememberType.ALWAYS,
                choice.granted,
                signPolicy = choice.signPolicy,
                deleteAfter = choice.deleteAfter.deleteAt(),
                accountNpub = choice.accountNpub,
            )
        } else {
            AmberDesktop.engine.approve(
                request,
                rememberChoiceFor(request.queueKey),
                encryptionScope = scopeChoiceFor(request),
            )
        }
    }

    /** Rejects [request] with the "Remember" and scope choices made on its card. */
    fun reject(request: PendingBunkerRequest) {
        AmberDesktop.engine.reject(request, rememberChoiceFor(request.queueKey), scopeChoiceFor(request))
    }

    fun setGroupRemember(requests: List<PendingBunkerRequest>, type: RememberType) {
        rememberChoices.value += requests.associate { it.queueKey to type }
    }

    fun setGroupScope(requests: List<PendingBunkerRequest>, scope: EncryptionScope) {
        scopeChoices.value += requests.associate { it.queueKey to scope }
    }

    fun rememberChoiceFor(requestId: String): RememberType = rememberChoices.value[requestId] ?: RememberType.NEVER

    fun setRememberChoice(requestId: String, type: RememberType) {
        rememberChoices.value += (requestId to type)
    }
}

/** The batch screen's Approve/Deny decision per request, and confirming the batch with them. */
object BatchDecisions {
    /**
     * Approve (true) / Deny (false) per request on the batch screen. Only keys
     * the screen has shown are present, so Confirm never answers a request
     * that arrived after the user last looked.
     */
    val batchApproved = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /** Marks newly shown batch requests as approved, like Android's default. */
    fun seedBatch(requests: List<PendingBunkerRequest>) {
        val missing = requests.filter { it.queueKey !in batchApproved.value }
        if (missing.isNotEmpty()) batchApproved.value += missing.associate { it.queueKey to true }
    }

    fun setBatchApproved(requests: List<PendingBunkerRequest>, approved: Boolean) {
        batchApproved.value += requests.associate { it.queueKey to approved }
    }

    /** Confirm on the batch screen: answers every shown request by its checkbox, with its group's choices. */
    fun confirmBatch(): Boolean {
        val pending = AmberDesktop.engine.pending.value
        if (singleRequest(pending) != null) return false
        val decisions = batchApproved.value
        batchApps(pending).flatMap { it.requests }.forEach { request ->
            when (decisions[request.queueKey]) {
                true -> RequestChoices.approve(request)
                false -> RequestChoices.reject(request)
                null -> Unit
            }
        }
        return true
    }
}

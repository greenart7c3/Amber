package com.greenart7c3.nostrsigner.desktop.ui

import com.greenart7c3.nostrsigner.shared.BunkerApprovalDecision
import com.greenart7c3.nostrsigner.shared.BunkerApprovalPort
import com.greenart7c3.nostrsigner.shared.BunkerApprovalRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PendingApproval(val request: BunkerApprovalRequest, val result: CompletableDeferred<BunkerApprovalDecision>)

/** Bridges [BunkerSigningEngine] approval prompts to the Compose UI: queues requests and suspends until the user answers. */
class DesktopApprovalPort : BunkerApprovalPort {
    private val _pending = MutableStateFlow<List<PendingApproval>>(emptyList())
    val pending = _pending.asStateFlow()

    override suspend fun requestApproval(request: BunkerApprovalRequest): BunkerApprovalDecision {
        val deferred = CompletableDeferred<BunkerApprovalDecision>()
        val entry = PendingApproval(request, deferred)
        _pending.update { it + entry }
        return try {
            deferred.await()
        } finally {
            _pending.update { list -> list.filterNot { it === entry } }
        }
    }

    fun answer(entry: PendingApproval, approved: Boolean, remember: Boolean) {
        entry.result.complete(BunkerApprovalDecision(approved = approved, remember = remember))
    }
}

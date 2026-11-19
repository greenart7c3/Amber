package com.greenart7c3.nostrsigner.desktop.core

import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// The approval queue: what is waiting for the user, and taking requests off it.

// Atomic updates: socket clients queue from several threads at once, and a
// plain read-then-write of `pending.value` loses one of two racing requests.
internal fun BunkerEngine.addPending(request: PendingBunkerRequest) {
    pending.update { list -> if (list.any { it.isSameEntry(request) }) list else list + request }
}

/** Removes the entry queued under [req]'s (account, connection, id) key. */
fun BunkerEngine.removePending(req: PendingBunkerRequest) {
    pending.update { list -> list.filterNot { it.isSameEntry(req) } }
}

/** Drops requests whose [PendingBunkerRequest.expiresAt] has passed; the UI calls this periodically. */
fun BunkerEngine.pruneExpired(now: Long = TimeUtils.now()) {
    dropPending("request expired") { it.expiresAt != null && it.expiresAt <= now }
}

/**
 * Removes the matching requests from the queue. Relay clients simply time
 * out, but a local socket client is still waiting on its connection, so it
 * gets [error] as the answer.
 */
fun BunkerEngine.dropPending(error: String, predicate: (PendingBunkerRequest) -> Boolean) {
    var dropped = emptyList<PendingBunkerRequest>()
    pending.update { list ->
        dropped = list.filter(predicate)
        list.filterNot(predicate)
    }
    dropped.forEach { req ->
        req.responder?.let { respond -> scope.launch { respond(BunkerResponse(req.request.id, "", error)) } }
    }
}

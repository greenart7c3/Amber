package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.commonsui.Permission
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.d_request_approved
import com.greenart7c3.nostrsigner.commonsui.d_request_rejected
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.desktop.Session
import com.greenart7c3.nostrsigner.desktop.core.AccountsStore
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.DeleteAfterType
import com.greenart7c3.nostrsigner.desktop.core.EncryptedContent
import com.greenart7c3.nostrsigner.desktop.core.EncryptionScope
import com.greenart7c3.nostrsigner.desktop.core.PassphraseLock
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.rememberTypeDisplayOrder
import kotlinx.coroutines.flow.MutableStateFlow

/** Options picked on a connect request card (mirrors Android's BunkerConnectRequestScreen). */
data class ConnectChoice(
    val signPolicy: Int,
    val granted: List<Permission>,
    val accountNpub: String,
    val deleteAfter: DeleteAfterType = DeleteAfterType.NEVER,
)

/** One app's requests of one kind in the batch screen (mirrors Android's RequestGroupKey). */
data class RequestGroup(
    val type: SignerType,
    val kind: Int?,
    val content: EncryptedContent?,
    val requests: List<PendingBunkerRequest>,
) {
    val id: String get() = requests.first().let { "${it.account.npub}:${it.localKey}:$type:$kind:${content?.type}:${content?.eventKind}" }
}

/** The requests one connection (client key, per account) has in the batch screen. */
data class RequestApp(val groups: List<RequestGroup>) {
    val first: PendingBunkerRequest get() = groups.first().requests.first()
    val requests: List<PendingBunkerRequest> get() = groups.flatMap { it.requests }
}

/**
 * The request shown on its own, like Android: a pending connect (it needs the
 * account and replace choices only the single card has), else the only request.
 * Null means several requests: the batch screen.
 */
fun singleRequest(pending: List<PendingBunkerRequest>): PendingBunkerRequest? = pending.firstOrNull { it.type == SignerType.CONNECT } ?: pending.singleOrNull()

/** Batch screen layout: per app in arrival order, then per request type, sorted like Android's groupRequests. */
fun batchApps(pending: List<PendingBunkerRequest>): List<RequestApp> = pending
    .filter { it.type != SignerType.CONNECT }
    .groupBy { it.account.npub to it.localKey }
    .values
    .map { requests ->
        RequestApp(
            requests.groupBy { Triple(it.type, it.kind, it.encryptedContent) }
                .map { (key, items) -> RequestGroup(key.first, key.second, key.third, items) }
                .sortedWith(compareBy({ it.type.ordinal }, { it.content?.type?.ordinal ?: -1 }, { it.kind ?: -1 })),
        )
    }

/** The requests on screen, in display order: what ↑/↓ and the shortcuts act on. */
fun displayedRequests(pending: List<PendingBunkerRequest>): List<PendingBunkerRequest> = singleRequest(pending)?.let(::listOf) ?: batchApps(pending).flatMap { it.requests }

/**
 * Navigation state, hoisted out of the composition so the window-level
 * keyboard handler (and the tray) can drive it.
 */
object UiState {
    val currentRoute = MutableStateFlow<Route>(Route.Applications)

    /** null = list; non-null = the application detail screen for that key. */
    val selectedApplication = MutableStateFlow<String?>(null)

    /** Request id the keyboard is acting on in the incoming-requests list. */
    val selectedRequestId = MutableStateFlow<String?>(null)

    /** Request whose "See details" dialog is open; shortcuts pause meanwhile. */
    val detailsRequestId = MutableStateFlow<String?>(null)

    fun navigate(route: Route) {
        selectedApplication.value = null
        currentRoute.value = route
    }

    /** The selected request on screen, falling back to the first one shown. */
    fun selectedRequest(): PendingBunkerRequest? {
        val shown = displayedRequests(AmberDesktop.engine.pending.value)
        return shown.firstOrNull { it.queueKey == selectedRequestId.value } ?: shown.firstOrNull()
    }

    fun moveRequestSelection(delta: Int) {
        val pending = displayedRequests(AmberDesktop.engine.pending.value)
        if (pending.isEmpty()) return
        val current = pending.indexOfFirst { it.queueKey == selectedRequestId.value }.coerceAtLeast(0)
        selectedRequestId.value = pending[(current + delta).coerceIn(0, pending.size - 1)].queueKey
    }

    /** Drops selection/remember state for requests that no longer exist. */
    fun pruneRequestState(pending: List<PendingBunkerRequest>) {
        val ids = pending.map { it.queueKey }.toSet()
        if (selectedRequestId.value !in ids) {
            selectedRequestId.value = displayedRequests(pending).firstOrNull()?.queueKey
        }
        if (RequestChoices.rememberChoices.value.keys.any { it !in ids }) {
            RequestChoices.rememberChoices.value = RequestChoices.rememberChoices.value.filterKeys { it in ids }
        }
        if (RequestChoices.connectChoices.value.keys.any { it !in ids }) {
            RequestChoices.connectChoices.value = RequestChoices.connectChoices.value.filterKeys { it in ids }
        }
        if (RequestChoices.scopeChoices.value.keys.any { it !in ids }) {
            RequestChoices.scopeChoices.value = RequestChoices.scopeChoices.value.filterKeys { it in ids }
        }
        if (BatchDecisions.batchApproved.value.keys.any { it !in ids }) {
            BatchDecisions.batchApproved.value = BatchDecisions.batchApproved.value.filterKeys { it in ids }
        }
        if (detailsRequestId.value != null && detailsRequestId.value !in ids) {
            detailsRequestId.value = null
        }
    }
}

/** The keys that edit the selected request on the incoming-requests screen, applied to its batch group where one exists. */
object RequestKeyboard {
    /**
     * Space on the batch screen: flips the selected request between Approve and
     * Deny. With [wholeGroup] (Shift+Space) the selected request's section, like
     * its checkbox: all approved -> deny all, otherwise approve all.
     */
    fun toggleSelectedBatchApproval(wholeGroup: Boolean = false): Boolean {
        if (singleRequest(AmberDesktop.engine.pending.value) != null) return false
        val request = UiState.selectedRequest()?.takeIf { it.queueKey in BatchDecisions.batchApproved.value } ?: return false
        val targets = if (wholeGroup) choiceTargets(request) else listOf(request)
        BatchDecisions.setBatchApproved(targets, !targets.all { BatchDecisions.batchApproved.value[it.queueKey] != false })
        return true
    }

    private fun choiceTargets(request: PendingBunkerRequest): List<PendingBunkerRequest> {
        val pending = AmberDesktop.engine.pending.value
        if (singleRequest(pending) != null) return listOf(request)
        return batchApps(pending).flatMap { it.groups }.firstOrNull { request in it.requests }?.requests ?: listOf(request)
    }

    /**
     * ←/→ on the selected request: cycles "Delete after" on a connect request
     * (connect approvals are always remembered), else the "Remember" duration.
     */
    fun cycleRememberChoice(delta: Int) {
        val request = UiState.selectedRequest() ?: return
        if (request.type == SignerType.CONNECT) {
            val choice = RequestChoices.connectChoiceFor(request)
            val order = DeleteAfterType.entries
            RequestChoices.setConnectChoice(request.queueKey, choice.copy(deleteAfter = order[(order.indexOf(choice.deleteAfter) + delta).mod(order.size)]))
            return
        }
        val order = rememberTypeDisplayOrder
        val current = order.indexOf(RequestChoices.rememberChoiceFor(request.queueKey)).coerceAtLeast(0)
        val next = (current + delta).mod(order.size)
        RequestChoices.setGroupRemember(choiceTargets(request), order[next])
    }

    /** 1–3 on a selected connect request: picks the sign policy (0 basic, 1 manual, 2 full trust). */
    fun setSelectedSignPolicy(policy: Int): Boolean {
        val request = UiState.selectedRequest()?.takeIf { it.type == SignerType.CONNECT } ?: return false
        RequestChoices.setConnectChoice(request.queueKey, RequestChoices.connectChoiceFor(request).copy(signPolicy = policy))
        return true
    }

    /** D on a selected request: opens its details, when it has any. */
    fun showSelectedDetails(): Boolean {
        val request = UiState.selectedRequest()?.takeIf { RequestDetails.of(it) != null } ?: return false
        UiState.detailsRequestId.value = request.queueKey
        return true
    }

    /**
     * S on a selected encrypt/decrypt request: toggles its "Encryption scope"
     * (this method / kind only <-> all methods / kinds).
     */
    fun toggleSelectedScope(): Boolean {
        val request = UiState.selectedRequest()?.takeIf { it.hasScopeChoice } ?: return false
        val next = if (RequestChoices.scopeChoiceFor(request) == EncryptionScope.ALL) EncryptionScope.SPECIFIC else EncryptionScope.ALL
        RequestChoices.setGroupScope(choiceTargets(request), next)
        return true
    }

    /** The A key on a selected connect request: cycles the account it will be saved under. */
    fun cycleSelectedAccount(): Boolean {
        val request = UiState.selectedRequest()?.takeIf { it.canSwitchAccount } ?: return false
        val npubs = AccountsStore.accounts.value.map { it.npub }
        if (npubs.size < 2) return false
        val choice = RequestChoices.connectChoiceFor(request)
        val next = npubs[(npubs.indexOf(choice.accountNpub) + 1).mod(npubs.size)]
        RequestChoices.setConnectChoice(request.queueKey, choice.copy(accountNpub = next))
        return true
    }
}

/** True on macOS, where shortcuts use ⌘ instead of Ctrl. */
val isMacOs: Boolean = System.getProperty("os.name").lowercase().contains("mac")

/** Human-readable shortcut label, e.g. "⌘1" on macOS or "Ctrl+1" elsewhere. */
fun shortcutLabel(key: String, shift: Boolean = false): String = buildString {
    append(if (isMacOs) "⌘" else "Ctrl+")
    if (shift) append(if (isMacOs) "⇧" else "Shift+")
    append(key)
}

/**
 * Window-level keyboard shortcuts:
 * - Ctrl/⌘ 1–4: switch between the sidebar sections
 * - ↑/↓ (incoming requests): select a pending request
 * - ←/→ (incoming requests): cycle the selected request's "Remember" choice
 *   (on a connect request: its "Delete after" choice)
 * - 1/2/3 (connect request): basic / manual / full-trust sign policy
 * - A (connect request): cycle the account the connection is saved under
 * - Space (several requests): toggle the selected request between Approve and Deny
 * - Shift Space (several requests): approve or deny the selected request's whole section
 * - S (encrypt/decrypt request): toggle the encryption scope (this method or
 *   kind only / all methods or kinds)
 * - Ctrl/⌘ Enter: approve the selected request with the chosen duration
 *   (several requests: Confirm the batch; ←/→ and S then set the whole group)
 * - Ctrl/⌘ Shift Enter: reject the selected request
 * - Ctrl/⌘ L: lock (when a passphrase is set)
 * - Ctrl/⌘ M or W: hide the window (to the tray when enabled)
 * - Ctrl/⌘ Q: quit
 * - Escape: leave the application detail screen
 *
 * Returns true when the event was consumed.
 */
fun handleShortcut(
    event: KeyEvent,
    hideWindow: () -> Unit,
    quit: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    val loggedIn = Session.account.value != null && !PassphraseLock.isLocked()

    val readOnlyDetailsDialogOpen = UiState.detailsRequestId.value != null

    val ctrlOrCmdHeld = if (isMacOs) event.isMetaPressed else event.isCtrlPressed
    return if (ctrlOrCmdHeld) {
        ctrlOrCmdShortcut(event, loggedIn, readOnlyDetailsDialogOpen, hideWindow, quit)
    } else {
        plainShortcut(event, loggedIn, readOnlyDetailsDialogOpen)
    }
}

private inline fun consumed(action: () -> Unit): Boolean {
    action()
    return true
}

private fun plainShortcut(
    event: KeyEvent,
    loggedIn: Boolean,
    detailsOpen: Boolean,
): Boolean {
    if (event.key == Key.Escape && UiState.selectedApplication.value != null) {
        UiState.selectedApplication.value = null
        return true
    }
    val incomingRequestsListShown = loggedIn &&
        UiState.currentRoute.value == Route.IncomingRequest &&
        UiState.selectedApplication.value == null &&
        !detailsOpen &&
        AmberDesktop.engine.pending.value.isNotEmpty()
    return incomingRequestsListShown && incomingRequestKey(event)
}

private fun incomingRequestKey(event: KeyEvent): Boolean = when (event.key) {
    Key.DirectionUp -> consumed { UiState.moveRequestSelection(-1) }
    Key.DirectionDown -> consumed { UiState.moveRequestSelection(1) }
    Key.DirectionLeft -> consumed { RequestKeyboard.cycleRememberChoice(-1) }
    Key.DirectionRight -> consumed { RequestKeyboard.cycleRememberChoice(1) }
    Key.One -> RequestKeyboard.setSelectedSignPolicy(0)
    Key.Two -> RequestKeyboard.setSelectedSignPolicy(1)
    Key.Three -> RequestKeyboard.setSelectedSignPolicy(2)
    Key.A -> RequestKeyboard.cycleSelectedAccount()
    Key.S -> RequestKeyboard.toggleSelectedScope()
    Key.D -> RequestKeyboard.showSelectedDetails()
    Key.Spacebar -> RequestKeyboard.toggleSelectedBatchApproval(wholeGroup = event.isShiftPressed)
    else -> false
}

private fun ctrlOrCmdShortcut(
    event: KeyEvent,
    loggedIn: Boolean,
    detailsOpen: Boolean,
    hideWindow: () -> Unit,
    quit: () -> Unit,
): Boolean {
    sidebarOrderNavigationKeys[event.key]?.let { route -> return loggedIn && consumed { UiState.navigate(route) } }
    return otherCtrlOrCmdShortcut(event, loggedIn, detailsOpen, hideWindow, quit)
}

private val sidebarOrderNavigationKeys = mapOf(
    Key.One to Route.IncomingRequest,
    Key.Two to Route.Applications,
    Key.Three to Route.Relays,
    Key.Four to Route.Settings,
)

private fun otherCtrlOrCmdShortcut(
    event: KeyEvent,
    loggedIn: Boolean,
    detailsOpen: Boolean,
    hideWindow: () -> Unit,
    quit: () -> Unit,
): Boolean = when (event.key) {
    Key.Enter -> loggedIn && !detailsOpen && answerSelectedRequest(reject = event.isShiftPressed)
    Key.L -> PassphraseLock.isEnabled() && !PassphraseLock.isLocked() && consumed { PassphraseLock.lock() }
    Key.M, Key.W -> consumed(hideWindow)
    Key.Q -> consumed(quit)
    else -> false
}

private fun answerSelectedRequest(reject: Boolean): Boolean {
    val batchShown = singleRequest(AmberDesktop.engine.pending.value) == null
    if (batchShown) return !reject && BatchDecisions.confirmBatch()
    val request = UiState.selectedRequest() ?: return false
    if (reject) {
        RequestChoices.reject(request)
        Toaster.toast(getStringBlocking(Res.string.d_request_rejected))
    } else {
        RequestChoices.approve(request)
        Toaster.toast(getStringBlocking(Res.string.d_request_approved))
    }
    return true
}

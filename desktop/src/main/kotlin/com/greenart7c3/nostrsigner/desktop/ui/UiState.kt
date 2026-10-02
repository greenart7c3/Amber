package com.greenart7c3.nostrsigner.desktop.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.greenart7c3.nostrsigner.desktop.Session
import com.greenart7c3.nostrsigner.desktop.core.AccountsStore
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.DeleteAfterType
import com.greenart7c3.nostrsigner.desktop.core.EncryptionScope
import com.greenart7c3.nostrsigner.desktop.core.PassphraseLock
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.RequestedPermission
import com.greenart7c3.nostrsigner.desktop.core.SignerType
import com.greenart7c3.nostrsigner.desktop.core.Strings
import com.greenart7c3.nostrsigner.desktop.core.contentScopedSignerTypes
import com.greenart7c3.nostrsigner.desktop.core.nip44v3SignerTypes
import com.greenart7c3.nostrsigner.desktop.core.rememberTypeDisplayOrder
import kotlinx.coroutines.flow.MutableStateFlow

/** Options picked on a connect request card (mirrors Android's BunkerConnectRequestScreen). */
data class ConnectChoice(
    val signPolicy: Int,
    val granted: List<RequestedPermission>,
    val accountNpub: String,
    val deleteAfter: DeleteAfterType = DeleteAfterType.NEVER,
)

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

    /** Per-request "Remember" choice, shared by the dropdown and the shortcuts. */
    val rememberChoices = MutableStateFlow<Map<String, RememberType>>(emptyMap())

    /**
     * Per-request encrypt/decrypt "Encryption scope". Android defaults NIP-04/44
     * to all methods and NIP-44 v3 to this kind only.
     */
    val scopeChoices = MutableStateFlow<Map<String, EncryptionScope>>(emptyMap())

    fun scopeChoiceFor(request: PendingBunkerRequest): EncryptionScope = scopeChoices.value[request.request.id] ?: defaultScope(request)

    fun defaultScope(request: PendingBunkerRequest): EncryptionScope = if (request.type in nip44v3SignerTypes) EncryptionScope.SPECIFIC else EncryptionScope.ALL

    fun setScopeChoice(requestId: String, scope: EncryptionScope) {
        scopeChoices.value = scopeChoices.value + (requestId to scope)
    }

    /**
     * Per-connect-request choices (sign policy, granted permissions, delete
     * after), shared by the request card and the approve shortcut.
     */
    val connectChoices = MutableStateFlow<Map<String, ConnectChoice>>(emptyMap())

    fun connectChoiceFor(request: PendingBunkerRequest): ConnectChoice = connectChoices.value[request.request.id] ?: ConnectChoice(
        signPolicy = request.account.signPolicy,
        granted = request.requestedPermissions.map { it.copy() },
        accountNpub = request.account.npub,
    )

    fun setConnectChoice(requestId: String, choice: ConnectChoice) {
        connectChoices.value = connectChoices.value + (requestId to choice)
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
                rememberChoiceFor(request.request.id),
                encryptionScope = scopeChoiceFor(request),
            )
        }
    }

    /** Rejects [request] with the "Remember" and scope choices made on its card. */
    fun reject(request: PendingBunkerRequest) {
        AmberDesktop.engine.reject(request, rememberChoiceFor(request.request.id), scopeChoiceFor(request))
    }

    fun navigate(route: Route) {
        selectedApplication.value = null
        currentRoute.value = route
    }

    /** The selected pending request, falling back to the first one. */
    fun selectedRequest(): PendingBunkerRequest? {
        val pending = AmberDesktop.engine.pending.value
        return pending.firstOrNull { it.request.id == selectedRequestId.value } ?: pending.firstOrNull()
    }

    fun moveRequestSelection(delta: Int) {
        val pending = AmberDesktop.engine.pending.value
        if (pending.isEmpty()) return
        val current = pending.indexOfFirst { it.request.id == selectedRequestId.value }.coerceAtLeast(0)
        selectedRequestId.value = pending[(current + delta).coerceIn(0, pending.size - 1)].request.id
    }

    fun rememberChoiceFor(requestId: String): RememberType = rememberChoices.value[requestId] ?: RememberType.NEVER

    fun setRememberChoice(requestId: String, type: RememberType) {
        rememberChoices.value = rememberChoices.value + (requestId to type)
    }

    /**
     * ←/→ on the selected request: cycles "Delete after" on a connect request
     * (connect approvals are always remembered), else the "Remember" duration.
     */
    fun cycleRememberChoice(delta: Int) {
        val request = selectedRequest() ?: return
        if (request.type == SignerType.CONNECT) {
            val choice = connectChoiceFor(request)
            val order = DeleteAfterType.entries
            setConnectChoice(request.request.id, choice.copy(deleteAfter = order[(order.indexOf(choice.deleteAfter) + delta).mod(order.size)]))
            return
        }
        val order = rememberTypeDisplayOrder
        val current = order.indexOf(rememberChoiceFor(request.request.id)).coerceAtLeast(0)
        val next = (current + delta).mod(order.size)
        setRememberChoice(request.request.id, order[next])
    }

    /** 1–3 on a selected connect request: picks the sign policy (0 basic, 1 manual, 2 full trust). */
    fun setSelectedSignPolicy(policy: Int): Boolean {
        val request = selectedRequest()?.takeIf { it.type == SignerType.CONNECT } ?: return false
        setConnectChoice(request.request.id, connectChoiceFor(request).copy(signPolicy = policy))
        return true
    }

    /**
     * S on a selected encrypt/decrypt request: toggles its "Encryption scope"
     * (this method / kind only <-> all methods / kinds).
     */
    fun toggleSelectedScope(): Boolean {
        val request = selectedRequest()?.takeIf { it.type in contentScopedSignerTypes || it.type in nip44v3SignerTypes } ?: return false
        val next = if (scopeChoiceFor(request) == EncryptionScope.ALL) EncryptionScope.SPECIFIC else EncryptionScope.ALL
        setScopeChoice(request.request.id, next)
        return true
    }

    /** A on a selected connect request: cycles the account it will be saved under. */
    fun cycleSelectedAccount(): Boolean {
        val request = selectedRequest()?.takeIf { it.canSwitchAccount } ?: return false
        val npubs = AccountsStore.accounts.value.map { it.npub }
        if (npubs.size < 2) return false
        val choice = connectChoiceFor(request)
        val next = npubs[(npubs.indexOf(choice.accountNpub) + 1).mod(npubs.size)]
        setConnectChoice(request.request.id, choice.copy(accountNpub = next))
        return true
    }

    /** Drops selection/remember state for requests that no longer exist. */
    fun pruneRequestState(pending: List<PendingBunkerRequest>) {
        val ids = pending.map { it.request.id }.toSet()
        if (selectedRequestId.value !in ids) {
            selectedRequestId.value = pending.firstOrNull()?.request?.id
        }
        if (rememberChoices.value.keys.any { it !in ids }) {
            rememberChoices.value = rememberChoices.value.filterKeys { it in ids }
        }
        if (connectChoices.value.keys.any { it !in ids }) {
            connectChoices.value = connectChoices.value.filterKeys { it in ids }
        }
        if (scopeChoices.value.keys.any { it !in ids }) {
            scopeChoices.value = scopeChoices.value.filterKeys { it in ids }
        }
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
 * - S (encrypt/decrypt request): toggle the encryption scope (this method or
 *   kind only / all methods or kinds)
 * - Ctrl/⌘ Enter: approve the selected request with the chosen duration
 * - Ctrl/⌘ Shift Enter: reject the selected request
 * - Ctrl/⌘ L: lock (when a passphrase is set)
 * - Ctrl/⌘ W: hide the window (to the tray when enabled)
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

    val modifier = if (isMacOs) event.isMetaPressed else event.isCtrlPressed
    if (!modifier) {
        if (event.key == Key.Escape && UiState.selectedApplication.value != null) {
            UiState.selectedApplication.value = null
            return true
        }
        // Plain arrows drive the incoming-requests list: ↑/↓ selects a card,
        // ←/→ cycles its "Remember" duration. Only consumed on that screen so
        // arrow keys elsewhere (text fields, scrolling) keep working.
        val onIncoming = loggedIn &&
            UiState.currentRoute.value == Route.IncomingRequest &&
            UiState.selectedApplication.value == null &&
            AmberDesktop.engine.pending.value.isNotEmpty()
        if (onIncoming) {
            when (event.key) {
                Key.DirectionUp -> {
                    UiState.moveRequestSelection(-1)
                    return true
                }

                Key.DirectionDown -> {
                    UiState.moveRequestSelection(1)
                    return true
                }

                Key.DirectionLeft -> {
                    UiState.cycleRememberChoice(-1)
                    return true
                }

                Key.DirectionRight -> {
                    UiState.cycleRememberChoice(1)
                    return true
                }

                Key.One -> return UiState.setSelectedSignPolicy(0)

                Key.Two -> return UiState.setSelectedSignPolicy(1)

                Key.Three -> return UiState.setSelectedSignPolicy(2)

                Key.A -> return UiState.cycleSelectedAccount()

                Key.S -> return UiState.toggleSelectedScope()
            }
        }
        return false
    }

    when (event.key) {
        Key.One -> if (loggedIn) UiState.navigate(Route.IncomingRequest) else return false
        Key.Two -> if (loggedIn) UiState.navigate(Route.Applications) else return false
        Key.Three -> if (loggedIn) UiState.navigate(Route.Relays) else return false
        Key.Four -> if (loggedIn) UiState.navigate(Route.Settings) else return false

        Key.Enter -> {
            if (!loggedIn) return false
            val request = UiState.selectedRequest() ?: return false
            if (event.isShiftPressed) {
                UiState.reject(request)
                Toaster.toast(Strings.get("d_request_rejected"))
            } else {
                UiState.approve(request)
                Toaster.toast(Strings.get("d_request_approved"))
            }
        }

        Key.L -> {
            if (PassphraseLock.isEnabled() && !PassphraseLock.isLocked()) {
                PassphraseLock.lock()
            } else {
                return false
            }
        }

        // Ctrl/⌘ + M — minimize to tray (keep running in the background).
        // Ctrl/⌘ + W stays as an alias.
        Key.M -> hideWindow()
        Key.W -> hideWindow()
        Key.Q -> quit()
        else -> return false
    }
    return true
}

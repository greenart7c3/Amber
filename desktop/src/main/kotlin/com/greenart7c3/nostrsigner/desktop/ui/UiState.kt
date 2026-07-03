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
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.PassphraseLock
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.SignerType
import com.greenart7c3.nostrsigner.desktop.core.Strings
import com.greenart7c3.nostrsigner.desktop.core.rememberTypeDisplayOrder
import kotlinx.coroutines.flow.MutableStateFlow

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

    /** Cycles the selected request's "Remember" duration with ←/→. */
    fun cycleRememberChoice(delta: Int) {
        val request = selectedRequest() ?: return
        if (request.type == SignerType.CONNECT) return // connect approvals are always remembered
        val order = rememberTypeDisplayOrder
        val current = order.indexOf(rememberChoiceFor(request.request.id)).coerceAtLeast(0)
        val next = (current + delta).mod(order.size)
        setRememberChoice(request.request.id, order[next])
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
            val rememberType = UiState.rememberChoiceFor(request.request.id)
            if (event.isShiftPressed) {
                AmberDesktop.engine.reject(request, rememberType)
                Toaster.toast(Strings.get("d_request_rejected"))
            } else {
                AmberDesktop.engine.approve(
                    request,
                    if (request.type == SignerType.CONNECT) RememberType.ALWAYS else rememberType,
                    request.requestedPermissions,
                )
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

        Key.W -> hideWindow()
        Key.Q -> quit()
        else -> return false
    }
    return true
}

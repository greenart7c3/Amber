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
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.SignerType
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Navigation state, hoisted out of the composition so the window-level
 * keyboard handler (and the tray) can drive it.
 */
object UiState {
    val currentRoute = MutableStateFlow<Route>(Route.Applications)

    /** null = list; non-null = the application detail screen for that key. */
    val selectedApplication = MutableStateFlow<String?>(null)

    fun navigate(route: Route) {
        selectedApplication.value = null
        currentRoute.value = route
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
 * - Ctrl/⌘ Enter: approve the first pending request (just once)
 * - Ctrl/⌘ Shift Enter: reject the first pending request
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
        return false
    }

    when (event.key) {
        Key.One -> if (loggedIn) UiState.navigate(Route.IncomingRequest) else return false
        Key.Two -> if (loggedIn) UiState.navigate(Route.Applications) else return false
        Key.Three -> if (loggedIn) UiState.navigate(Route.Relays) else return false
        Key.Four -> if (loggedIn) UiState.navigate(Route.Settings) else return false

        Key.Enter -> {
            if (!loggedIn) return false
            val request = AmberDesktop.engine.pending.value.firstOrNull() ?: return false
            if (event.isShiftPressed) {
                AmberDesktop.engine.reject(request, RememberType.NEVER)
                Toaster.toast("Request rejected")
            } else {
                AmberDesktop.engine.approve(
                    request,
                    if (request.type == SignerType.CONNECT) RememberType.ALWAYS else RememberType.NEVER,
                    request.requestedPermissions,
                )
                Toaster.toast("Request approved")
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

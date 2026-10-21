package com.greenart7c3.nostrsigner.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AccountsStore
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AmberLogger
import com.greenart7c3.nostrsigner.desktop.core.AppImageIntegration
import com.greenart7c3.nostrsigner.desktop.core.AutoStart
import com.greenart7c3.nostrsigner.desktop.core.BuildVariant
import com.greenart7c3.nostrsigner.desktop.core.CrashReporter
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.LocalSigner
import com.greenart7c3.nostrsigner.desktop.core.NetworkConnectivity
import com.greenart7c3.nostrsigner.desktop.core.Notifier
import com.greenart7c3.nostrsigner.desktop.core.PassphraseLock
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import com.greenart7c3.nostrsigner.desktop.core.SignerType
import com.greenart7c3.nostrsigner.desktop.core.Strings
import com.greenart7c3.nostrsigner.desktop.core.UriLaunch
import com.greenart7c3.nostrsigner.desktop.core.describe
import com.greenart7c3.nostrsigner.desktop.ui.App
import com.greenart7c3.nostrsigner.desktop.ui.MIN_WINDOW_SIZE
import com.greenart7c3.nostrsigner.desktop.ui.NostrSignerTheme
import com.greenart7c3.nostrsigner.desktop.ui.RequestNotificationPopup
import com.greenart7c3.nostrsigner.desktop.ui.UiState
import com.greenart7c3.nostrsigner.desktop.ui.handleShortcut
import com.greenart7c3.nostrsigner.desktop.ui.initialWindowSize
import java.awt.Dimension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Desktop counterpart of `AccountStateViewModel`: which account is active. */
object Session {
    val account = MutableStateFlow<DesktopAccount?>(null)
    val loading = MutableStateFlow(true)

    /**
     * True once the account for the current unlock has been resolved. The UI
     * sees the lock open before [boot] has loaded the account, so it waits on
     * this instead of showing the login screen for an account-less moment.
     */
    val ready = MutableStateFlow(false)
    val addingAccount = MutableStateFlow(false)

    fun boot() {
        NetworkConnectivity.start(AmberDesktop.applicationIOScope)
        AmberDesktop.startTor()
        AmberDesktop.applicationIOScope.launch {
            var engineStarted = false
            PassphraseLock.state.collect { status ->
                when (status) {
                    PassphraseLock.Status.LOCKED, PassphraseLock.Status.DISABLED -> {
                        // Key material is evicted (or not set up yet); drop the
                        // account reference so nothing in the UI can reach a
                        // decrypted signer, and keep the engine disconnected
                        // until the passphrase has been set up and unlocked.
                        ready.value = false
                        account.value = null
                        loading.value = false
                    }

                    PassphraseLock.Status.UNLOCKED -> {
                        val saved = AmberDesktop.settings.currentAccount
                        val npub = saved.ifBlank { AccountsStore.accounts.value.firstOrNull()?.npub ?: "" }
                        if (npub.isNotBlank()) {
                            account.value = AmberDesktop.account(npub)
                        }
                        ready.value = true
                        loading.value = false
                        if (engineStarted) {
                            AmberDesktop.engine.checkForNewRelaysAndUpdateAllFilters()
                        } else {
                            AmberDesktop.engine.start()
                            engineStarted = true
                        }
                    }
                }
            }
        }
    }

    suspend fun switchTo(npub: String) {
        SettingsStore.update { it.copy(currentAccount = npub) }
        account.value = AmberDesktop.account(npub)
        addingAccount.value = false
    }

    fun onAccountAdded(newAccount: DesktopAccount) {
        account.value = newAccount
        addingAccount.value = false
        AmberDesktop.applicationIOScope.launch {
            AmberDesktop.engine.checkForNewRelaysAndUpdateAllFilters()
        }
    }

    suspend fun logout(npub: String) {
        AmberDesktop.engine.dropPending("account removed") { it.account.npub == npub }
        AmberDesktop.store(npub).deleteAllFiles()
        AccountsStore.delete(npub)
        AmberDesktop.evictAccount(npub)
        val next = AccountsStore.accounts.value.firstOrNull()?.npub ?: ""
        SettingsStore.update { it.copy(currentAccount = next) }
        account.value = if (next.isBlank()) null else AmberDesktop.account(next)
        AmberDesktop.engine.checkForNewRelaysAndUpdateAllFilters()
    }

    fun saveMeta(acc: DesktopAccount) {
        AccountManager.saveAccountMeta(acc)
    }
}

/**
 * Holds the native (dorkbox) tray and the window-visibility/quit signals it
 * drives. The tray is built in [main] before Compose starts, so this bridges
 * its off-thread menu callbacks to Compose via flows.
 */
private object DesktopTray {
    val isLinux = System.getProperty("os.name").lowercase().let { it.contains("linux") || it.contains("nix") || it.contains("nux") }
    val windowVisible = MutableStateFlow(true)
    val quitRequested = MutableStateFlow(false)

    @Volatile
    var instance: NativeTray? = null
}

/**
 * AWT derives the X11 WM_CLASS from whichever thread first loaded the toolkit
 * (e.g. `java-lang-Thread`), so docks (GNOME, KDE) can't match the window to
 * its desktop entry and show a generic icon instead of Amber's. Must run
 * before the first window is created; needs
 * `--add-opens java.desktop/sun.awt.X11=ALL-UNNAMED`.
 */
private fun setX11WindowClass(name: String) {
    runCatching {
        val toolkit = java.awt.Toolkit.getDefaultToolkit()
        toolkit.javaClass.getDeclaredField("awtAppClassName").apply {
            isAccessible = true
            set(toolkit, name)
        }
    }.onFailure { AmberLogger.e("Main", "could not set WM_CLASS", it) }
}

fun main(args: Array<String>) {
    // First, so a crash anywhere below is saved for the report dialog.
    CrashReporter.install()
    // Packaged handler: the URI arrives as an argument. Dev handler
    // (gradle): it arrives via the drop file (gradle cannot take it as an
    // argument).
    val launchUri = UriLaunch.extract(args) ?: UriLaunch.readForwardedUri()
    // Single instance: a second launch hands its nostrconnect:// URI to the
    // running app and exits. The OS releases the lock if the first instance
    // dies, so a failed acquire always means a live instance exists.
    val primary = UriLaunch.tryAcquireSingleInstance()
    if (!primary) {
        if (launchUri != null && UriLaunch.forwardToRunningInstance(launchUri)) return
        if (launchUri == null) return
        // Could not forward (no listener): fall through and start anyway.
    } else {
        // macOS delivers nostrconnect:// links as an Apple event, not as an
        // argument (also to an already-running Amber). Install the handler
        // before Compose starts so the event that launched the app is not
        // missed — but only in the primary instance: it initializes AWT, and
        // once AWT is up returning from main no longer ends the process, so
        // a second instance would linger invisibly instead of exiting.
        UriLaunch.installMacOpenUriHandler()
        UriLaunch.registerSchemeHandler()
        AppImageIntegration.sync()
        UriLaunch.startIpcServer()
        // NIP-5F: CLI tools sign over a local socket (always on).
        LocalSigner.start()
    }
    if (launchUri != null) UriLaunch.pending.value = launchUri

    // Keep the autostart entry fresh (binary path can change between builds/installs).
    if (SettingsStore.settings.value.startOnBoot) {
        AutoStart.setEnabled(true)
    }

    // The dorkbox tray prefers to be created before Compose/AWT initializes
    // GTK (dorkbox has to own GTK loading, otherwise the AppIndicator backend
    // fails to start and SystemTray.get() returns null even when
    // libayatana-appindicator is installed). So start it here, first thing —
    // but never let it block the window: createBounded probes for an SNI host
    // and abandons init that takes too long.
    if (DesktopTray.isLinux && System.getenv("AMBER_DISABLE_TRAY") == null) {
        DesktopTray.instance = NativeTray.createBounded(
            iconStream = { NativeTray::class.java.getResourceAsStream("/icon.png") },
            tooltip = Strings.get("d_tray_tooltip"),
            openLabel = Strings.get("d_tray_open"),
            lockLabel = Strings.get("d_lock_now"),
            quitLabel = Strings.get("d_tray_quit"),
            onToggle = { DesktopTray.windowVisible.value = !DesktopTray.windowVisible.value },
            onLock = { PassphraseLock.lock() },
            onQuit = { DesktopTray.quitRequested.value = true },
        )
    }

    if (DesktopTray.isLinux) setX11WindowClass(AppImageIntegration.WINDOW_CLASS)

    Session.boot()

    application {
        val windowState = remember {
            val saved = SettingsStore.settings.value
            WindowState(
                placement = if (saved.windowMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
                position = WindowPosition(Alignment.Center),
                size = initialWindowSize(saved.windowWidth, saved.windowHeight),
            )
        }
        val pending by AmberDesktop.engine.pending.collectAsState()
        val settings by SettingsStore.settings.collectAsState()
        val language by Strings.currentLanguage.collectAsState()
        val lockStatus by PassphraseLock.state.collectAsState()
        val windowVisible by DesktopTray.windowVisible.collectAsState()
        val quitRequested by DesktopTray.quitRequested.collectAsState()
        val trayState = rememberTrayState()
        val isLinux = DesktopTray.isLinux
        val nativeTray = DesktopTray.instance

        // AWT's tray only works with an XEmbed host, which excludes Wayland; on
        // Linux we use the dorkbox tray (created above) instead, and keep AWT
        // for Windows/macOS where it works well.
        val awtTrayUsable = remember {
            !isLinux && isTraySupported && runCatching { java.awt.SystemTray.getSystemTray() }.isSuccess
        }

        // Remember maximized state and the floating size across launches. The
        // size is only saved while floating, so un-maximizing restores it.
        LaunchedEffect(windowState) {
            snapshotFlow { windowState.placement to windowState.size }
                .drop(1)
                .collectLatest { (placement, size) ->
                    delay(500)
                    SettingsStore.update {
                        if (placement == WindowPlacement.Floating) {
                            it.copy(windowMaximized = false, windowWidth = size.width.value.toInt(), windowHeight = size.height.value.toInt())
                        } else {
                            it.copy(windowMaximized = placement == WindowPlacement.Maximized)
                        }
                    }
                }
        }
        // Drop requests whose client has most likely given up (see PendingBunkerRequest.expiresAt),
        // and connections whose "delete after" time has passed.
        LaunchedEffect(Unit) {
            while (true) {
                delay(15_000)
                AmberDesktop.engine.pruneExpired()
                AmberDesktop.engine.pruneExpiredApplications()
            }
        }
        // The tray's Quit routes here so we can exit the Compose app cleanly.
        LaunchedEffect(quitRequested) { if (quitRequested) exitApplication() }
        // A nostrconnect:// link explicitly targets Amber: raise the window,
        // even when it was minimized to the tray.
        LaunchedEffect(Unit) {
            UriLaunch.pending.collect { uri ->
                if (uri != null) DesktopTray.windowVisible.value = true
            }
        }
        DisposableEffect(Unit) { onDispose { nativeTray?.shutdown() } }
        LaunchedEffect(pending.size, windowVisible, lockStatus, language) {
            nativeTray?.update(
                tooltip = if (pending.isEmpty()) Strings.get("d_tray_tooltip", language) else Strings.format("d_tray_pending", pending.size, language = language),
                openLabel = if (windowVisible) Strings.get("d_tray_hide", language) else Strings.get("d_tray_open", language),
                lockLabel = Strings.get("d_lock_now", language),
                showLock = lockStatus == PassphraseLock.Status.UNLOCKED,
            )
        }

        val trayUsable = awtTrayUsable || nativeTray != null
        // "Minimize to tray" should keep Amber running in the background so it
        // still answers requests. On Linux we honor it whenever close-to-tray
        // is enabled — even if no tray icon could be published (Wayland trays
        // are flaky) — so the window at least hides instead of quitting; a new
        // request (or the tray icon, when present) brings it back.
        val trayActive = settings.closeToTray && (trayUsable || isLinux)

        // Notify when a new approval request arrives. Do NOT force the window
        // open — a minimized Amber should stay minimized and let the user open
        // it from the notification or the tray. Only surface it as a last
        // resort, when the user could not be reached any other way.
        // Tracks the requests themselves, not the count: answering one from a
        // notification and the client's next request arriving can leave the
        // size unchanged between recompositions.
        var seenPending by remember { mutableStateOf(emptyList<PendingBunkerRequest>()) }
        // The request shown in Amber's own notification popup: on Windows and
        // macOS (the AWT notification cannot carry Approve/Reject buttons) and
        // on Linux daemons that never draw action buttons.
        var popupRequest by remember { mutableStateOf<PendingBunkerRequest?>(null) }
        // Whether the main window has keyboard focus, i.e. the user is looking
        // at Amber, where the request card already shows: no notification then.
        var windowFocused by remember { mutableStateOf(false) }
        LaunchedEffect(pending) {
            val request = pending.lastOrNull { new -> seenPending.none { it.isSameEntry(new) } }
            seenPending = pending
            if (request != null && !(DesktopTray.windowVisible.value && windowFocused)) {
                var notified = false
                if (settings.showNotifications) {
                    val message = "${request.appName} ${request.type.describe(request.kind, request.encryptedContent, language)}"
                    // Daemons that never draw action buttons get Amber's own popup.
                    val nativeButtons = isLinux && withContext(Dispatchers.IO) { Notifier.linuxDaemonShowsButtons() }
                    if (nativeButtons) {
                        // The OS-native notification channel (freedesktop /
                        // notify-send, incl. Wayland/Hyprland), with Approve /
                        // Reject buttons. A connect needs the choices on its
                        // card, so it only offers Reject.
                        val actions = buildList {
                            if (request.type != SignerType.CONNECT) {
                                add(Notifier.Action("approve", Strings.get("d_approve", language)) { answerFromNotification(request, approve = true) })
                            }
                            add(Notifier.Action("reject", Strings.get("reject", language)) { answerFromNotification(request, approve = false) })
                        }
                        notified = withContext(Dispatchers.IO) {
                            Notifier.notify(BuildVariant.appName, message, actions, onActivate = { DesktopTray.windowVisible.value = true })
                        }
                    } else {
                        popupRequest = request
                        notified = true
                    }
                }
                // No notification reached the user and there's no tray to reopen
                // from — surface the window so the request isn't missed.
                if (!notified && !trayUsable) {
                    DesktopTray.windowVisible.value = true
                }
            }
        }
        // Focusing Amber shows the request card, so the popup is redundant.
        LaunchedEffect(windowFocused) { if (windowFocused) popupRequest = null }
        // Close the popup once its request is answered elsewhere or dropped.
        popupRequest?.let { request ->
            if (pending.none { it.isSameEntry(request) }) {
                popupRequest = null
            } else {
                RequestNotificationPopup(
                    request = request,
                    title = request.appName,
                    message = request.type.describe(request.kind, request.encryptedContent, language),
                    language = language,
                    onApprove = {
                        popupRequest = null
                        answerFromNotification(request, approve = true)
                    },
                    onReject = {
                        popupRequest = null
                        answerFromNotification(request, approve = false)
                    },
                    onOpen = {
                        popupRequest = null
                        DesktopTray.windowVisible.value = true
                    },
                    onDismiss = { popupRequest = null },
                )
            }
        }

        if (awtTrayUsable) {
            Tray(
                icon = painterResource("icon.png"),
                state = trayState,
                tooltip = if (pending.isEmpty()) Strings.get("d_tray_tooltip", language) else Strings.format("d_tray_pending", pending.size, language = language),
                onAction = { DesktopTray.windowVisible.value = true },
                menu = {
                    Item(
                        if (windowVisible) Strings.get("d_tray_hide", language) else Strings.get("d_tray_open", language),
                        onClick = { DesktopTray.windowVisible.value = !DesktopTray.windowVisible.value },
                    )
                    if (lockStatus == PassphraseLock.Status.UNLOCKED) {
                        Item(Strings.get("d_lock_now", language), onClick = { PassphraseLock.lock() })
                    }
                    Separator()
                    Item(Strings.get("d_tray_quit", language), onClick = ::exitApplication)
                },
            )
        }

        Window(
            onCloseRequest = {
                if (trayActive) {
                    DesktopTray.windowVisible.value = false
                } else {
                    exitApplication()
                }
            },
            state = windowState,
            visible = windowVisible,
            title = if (pending.isEmpty()) BuildVariant.appName else "${BuildVariant.appName} (${pending.size})",
            icon = painterResource("icon.png"),
            onPreviewKeyEvent = { event ->
                handleShortcut(
                    event,
                    hideWindow = {
                        if (trayActive) DesktopTray.windowVisible.value = false else exitApplication()
                    },
                    quit = ::exitApplication,
                )
            },
        ) {
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(MIN_WINDOW_SIZE.width.value.toInt(), MIN_WINDOW_SIZE.height.value.toInt())
            }
            val focused = LocalWindowInfo.current.isWindowFocused
            LaunchedEffect(focused) { windowFocused = focused }
            NostrSignerTheme {
                App()
            }
        }
    }
}

/**
 * Approve/Reject button on a request notification. The request may have been
 * answered in the window (or dropped on lock/expiry) while the notification
 * was showing, so only act if it is still pending — never answer it twice.
 */
private fun answerFromNotification(request: PendingBunkerRequest, approve: Boolean) {
    if (AmberDesktop.engine.pending.value.none { it.isSameEntry(request) }) return
    if (approve) UiState.approve(request) else UiState.reject(request)
}

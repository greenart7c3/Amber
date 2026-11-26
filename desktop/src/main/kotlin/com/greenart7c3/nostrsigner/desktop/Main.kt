package com.greenart7c3.nostrsigner.desktop

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.d_approve
import com.greenart7c3.nostrsigner.commonsui.d_hidden_no_tray
import com.greenart7c3.nostrsigner.commonsui.d_lock_now
import com.greenart7c3.nostrsigner.commonsui.d_tray_hide
import com.greenart7c3.nostrsigner.commonsui.d_tray_open
import com.greenart7c3.nostrsigner.commonsui.d_tray_pending
import com.greenart7c3.nostrsigner.commonsui.d_tray_quit
import com.greenart7c3.nostrsigner.commonsui.d_tray_tooltip
import com.greenart7c3.nostrsigner.commonsui.getStringBlocking
import com.greenart7c3.nostrsigner.commonsui.reject
import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AccountsStore
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.AmberLogger
import com.greenart7c3.nostrsigner.desktop.core.AppImageIntegration
import com.greenart7c3.nostrsigner.desktop.core.AutoStart
import com.greenart7c3.nostrsigner.desktop.core.BuildVariant
import com.greenart7c3.nostrsigner.desktop.core.BunkerRelays
import com.greenart7c3.nostrsigner.desktop.core.CrashReporter
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.LocalBunkerRelay
import com.greenart7c3.nostrsigner.desktop.core.LocalSigner
import com.greenart7c3.nostrsigner.desktop.core.NetworkConnectivity
import com.greenart7c3.nostrsigner.desktop.core.Notifier
import com.greenart7c3.nostrsigner.desktop.core.PassphraseLock
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.RelayConnections
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import com.greenart7c3.nostrsigner.desktop.core.Strings
import com.greenart7c3.nostrsigner.desktop.core.UriLaunch
import com.greenart7c3.nostrsigner.desktop.core.allowsQuickApprove
import com.greenart7c3.nostrsigner.desktop.core.describe
import com.greenart7c3.nostrsigner.desktop.core.dropPending
import com.greenart7c3.nostrsigner.desktop.core.notificationAccountLine
import com.greenart7c3.nostrsigner.desktop.core.notificationTitle
import com.greenart7c3.nostrsigner.desktop.core.pruneExpired
import com.greenart7c3.nostrsigner.desktop.core.pruneExpiredApplications
import com.greenart7c3.nostrsigner.desktop.ui.App
import com.greenart7c3.nostrsigner.desktop.ui.MIN_WINDOW_SIZE
import com.greenart7c3.nostrsigner.desktop.ui.NostrSignerTheme
import com.greenart7c3.nostrsigner.desktop.ui.RequestChoices
import com.greenart7c3.nostrsigner.desktop.ui.RequestNotificationPopup
import com.greenart7c3.nostrsigner.desktop.ui.amberIconPainter
import com.greenart7c3.nostrsigner.desktop.ui.handleShortcut
import com.greenart7c3.nostrsigner.desktop.ui.initialWindowSize
import java.awt.Dimension
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

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
        RelayConnections.startTor()
        AmberDesktop.applicationIOScope.launch {
            var engineStarted = false
            PassphraseLock.state.collect { status ->
                when (status) {
                    PassphraseLock.Status.LOCKED, PassphraseLock.Status.DISABLED -> {
                        ready.value = false
                        account.value = null
                        loading.value = false
                    }

                    PassphraseLock.Status.UNLOCKED -> {
                        CrashReporter.loadAfterUnlock()
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

private object DesktopTray {
    val isLinux = System.getProperty("os.name").lowercase().let { it.contains("linux") || it.contains("nix") || it.contains("nux") }
    val windowVisible = MutableStateFlow(true)
    val windowFocused = MutableStateFlow(false)
    val quitRequested = MutableStateFlow(false)

    val instance = MutableStateFlow<NativeTray?>(null)
}

@Suppress("NoComments")
private fun setX11WindowClass() {
    // AWT derives X11 WM_CLASS from whichever thread loads the toolkit first;
    // needs `--add-opens java.desktop/sun.awt.X11=ALL-UNNAMED` before window creation.
    runCatching {
        val toolkit = java.awt.Toolkit.getDefaultToolkit()
        toolkit.javaClass.getDeclaredField("awtAppClassName").apply {
            isAccessible = true
            set(toolkit, AppImageIntegration.WINDOW_CLASS)
        }
    }.onFailure { AmberLogger.e("Main", "could not set WM_CLASS", it) }
}

fun main(args: Array<String>) {
    CrashReporter.install()
    Strings.install()
    val launchUri = UriLaunch.extract(args) ?: UriLaunch.readForwardedUri()
    if (!initializeInstance(launchUri)) return

    if (SettingsStore.settings.value.startOnBoot) {
        AutoStart.setEnabled(true)
    }
    if (DesktopTray.isLinux) {
        startLinuxNativeTray()
        setX11WindowClass()
    }

    Session.boot()
    runDesktopApplication()
}

@Suppress("NoComments")
private fun initializeInstance(launchUri: String?): Boolean {
    if (!UriLaunch.tryAcquireSingleInstance()) {
        val handedOff = if (launchUri != null) {
            UriLaunch.forwardToRunningInstance(launchUri)
        } else {
            UriLaunch.askRunningInstanceToShow()
            true
        }
        if (handedOff) return false
    } else {
        // macOS delivers nostrconnect:// links via Apple events; register only in
        // the primary instance because AWT keeps secondary processes alive after main.
        UriLaunch.installMacOpenUriHandler()
        UriLaunch.registerSchemeHandler()
        AppImageIntegration.sync()
        UriLaunch.startIpcServer()
        LocalSigner.start()
        if (SettingsStore.settings.value.localBunkerServer) {
            LocalBunkerRelay.start(BunkerRelays.localBunkerPort())
        }
    }
    if (launchUri != null) UriLaunch.pending.value = launchUri
    return true
}

@Suppress("NoComments")
private fun startLinuxNativeTray() {
    // Dorkbox tray must load GTK before Compose/AWT initializes it, otherwise
    // AppIndicator fails to start on Linux even when libayatana-appindicator is installed.
    if (System.getenv("AMBER_DISABLE_TRAY") != null) return
    NativeTray.createBounded(
        iconStream = { NativeTray::class.java.getResourceAsStream("/icon.png") },
        tooltip = getStringBlocking(Res.string.d_tray_tooltip),
        openLabel = getStringBlocking(Res.string.d_tray_open),
        lockLabel = getStringBlocking(Res.string.d_lock_now),
        quitLabel = getStringBlocking(Res.string.d_tray_quit),
        onToggle = { DesktopTray.windowVisible.value = !DesktopTray.windowVisible.value },
        onLock = { PassphraseLock.lock() },
        onQuit = { DesktopTray.quitRequested.value = true },
        onLateTray = { DesktopTray.instance.value = it },
    )?.let { DesktopTray.instance.value = it }
}

private fun runDesktopApplication() {
    application {
        val windowState = rememberSavedWindowState()
        val pending by AmberDesktop.engine.pending.collectAsState()
        val settings by SettingsStore.settings.collectAsState()
        val language by Strings.currentLanguage.collectAsState()
        val lockStatus by PassphraseLock.state.collectAsState()
        val windowVisible by DesktopTray.windowVisible.collectAsState()
        val nativeTray by DesktopTray.instance.collectAsState()
        val awtTrayUsable = rememberAwtTrayUsable()
        val trayUsable = awtTrayUsable || nativeTray != null
        val closeWindow = rememberCloseWindowAction(settings.closeToTray, trayUsable)

        PersistWindowBoundsEffect(windowState)
        DesktopLifecycleEffects(
            windowState = windowState,
            pendingCount = pending.size,
            windowVisible = windowVisible,
            lockStatus = lockStatus,
            language = language,
            nativeTray = nativeTray,
        )
        RequestNotificationHost(
            pending = pending,
            showNotifications = settings.showNotifications,
            trayUsable = trayUsable,
        )
        if (awtTrayUsable) {
            DesktopAwtTray(
                language = language,
                pendingCount = pending.size,
                windowVisible = windowVisible,
                lockStatus = lockStatus,
            )
        }
        AmberMainWindow(
            windowState = windowState,
            windowVisible = windowVisible,
            pendingCount = pending.size,
            language = language,
            onCloseRequest = closeWindow,
        )
    }
}

@Composable
private fun rememberSavedWindowState(): WindowState = remember {
    val saved = SettingsStore.settings.value
    WindowState(
        placement = if (saved.windowMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
        position = WindowPosition(Alignment.Center),
        size = initialWindowSize(saved.windowWidth, saved.windowHeight),
    )
}

@Suppress("NoComments")
@Composable
private fun rememberAwtTrayUsable(): Boolean = remember {
    // AWT SystemTray requires XEmbed (unavailable on Wayland); Linux uses dorkbox above.
    !DesktopTray.isLinux && isTraySupported && runCatching { java.awt.SystemTray.getSystemTray() }.isSuccess
}

@Composable
private fun ApplicationScope.rememberCloseWindowAction(
    closeToTray: Boolean,
    trayUsable: Boolean,
): () -> Unit {
    val scope = rememberCoroutineScope()
    var noTrayNoticeShown by remember { mutableStateOf(false) }
    val trayActive = closeToTray && (trayUsable || DesktopTray.isLinux)
    return {
        if (!trayActive) {
            exitApplication()
        } else {
            DesktopTray.windowVisible.value = false
            if (!trayUsable && !noTrayNoticeShown) {
                noTrayNoticeShown = true
                scope.launch(Dispatchers.IO) { Notifier.notify(BuildVariant.appName, getString(Res.string.d_hidden_no_tray)) }
            }
        }
    }
}

@Composable
private fun PersistWindowBoundsEffect(windowState: WindowState) {
    LaunchedEffect(windowState) {
        snapshotFlow { windowState.placement to windowState.size }
            .drop(1)
            .collectLatest { (placement, size) ->
                delay(500.milliseconds)
                SettingsStore.update {
                    if (placement == WindowPlacement.Floating) {
                        it.copy(windowMaximized = false, windowWidth = size.width.value.toInt(), windowHeight = size.height.value.toInt())
                    } else {
                        it.copy(windowMaximized = placement == WindowPlacement.Maximized)
                    }
                }
            }
    }
}

@Composable
private fun ApplicationScope.DesktopLifecycleEffects(
    windowState: WindowState,
    pendingCount: Int,
    windowVisible: Boolean,
    lockStatus: PassphraseLock.Status,
    language: String,
    nativeTray: NativeTray?,
) {
    val quitRequested by DesktopTray.quitRequested.collectAsState()
    LaunchedEffect(Unit) {
        while (true) {
            delay(15.seconds)
            AmberDesktop.engine.pruneExpired()
            AmberDesktop.engine.pruneExpiredApplications()
        }
    }
    LaunchedEffect(quitRequested) { if (quitRequested) exitApplication() }
    LaunchedEffect(Unit) {
        UriLaunch.pending.collect { uri ->
            if (uri != null) DesktopTray.windowVisible.value = true
        }
    }
    LaunchedEffect(Unit) {
        UriLaunch.showRequests.collect {
            DesktopTray.windowVisible.value = true
            windowState.isMinimized = false
        }
    }
    DisposableEffect(Unit) { onDispose { DesktopTray.instance.value?.shutdown() } }
    LaunchedEffect(nativeTray, pendingCount, windowVisible, lockStatus, language) {
        nativeTray?.update(
            tooltip = if (pendingCount == 0) getString(Res.string.d_tray_tooltip) else getString(Res.string.d_tray_pending, pendingCount),
            openLabel = if (windowVisible) getString(Res.string.d_tray_hide) else getString(Res.string.d_tray_open),
            lockLabel = getString(Res.string.d_lock_now),
            showLock = lockStatus == PassphraseLock.Status.UNLOCKED,
        )
    }
}

@Suppress("NoComments")
@Composable
private fun RequestNotificationHost(
    pending: List<PendingBunkerRequest>,
    showNotifications: Boolean,
    trayUsable: Boolean,
) {
    var seenPending by remember { mutableStateOf(emptyList<PendingBunkerRequest>()) }
    var popupRequest by remember { mutableStateOf<PendingBunkerRequest?>(null) }
    val requestNotifications = remember { mutableMapOf<String, Notifier.Notification>() }
    val windowFocused by DesktopTray.windowFocused.collectAsState()
    val popupHover = remember { MutableInteractionSource() }
    val popupHovered by popupHover.collectIsHoveredAsState()

    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { Notifier.nativeButtons() } }
    LaunchedEffect(pending) {
        dismissStaleNotifications(pending, requestNotifications)
        val request = pending.lastOrNull { new -> seenPending.none { it.isSameEntry(new) } }
        seenPending = pending
        if (request != null && !(DesktopTray.windowVisible.value && windowFocused)) {
            if (showNotifications) {
                if (!postNativeRequestNotification(request, requestNotifications)) {
                    popupRequest = request
                }
            } else if (!trayUsable) {
                DesktopTray.windowVisible.value = true
            }
        }
    }
    // On macOS clicking the popup activates Amber and focuses the main window
    // before the click reaches the popup's button; keep the popup open while hovered.
    LaunchedEffect(windowFocused, popupHovered) { if (windowFocused && !popupHovered) popupRequest = null }

    popupRequest?.let { request ->
        if (pending.none { it.isSameEntry(request) }) {
            popupRequest = null
        } else {
            RequestNotificationPopup(
                request = request,
                title = request.notificationTitle(),
                message = requestNotificationMessage(request),
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
                hover = popupHover,
            )
        }
    }
}

private fun dismissStaleNotifications(
    pending: List<PendingBunkerRequest>,
    requestNotifications: MutableMap<String, Notifier.Notification>,
) {
    val live = pending.mapTo(mutableSetOf()) { it.queueKey }
    requestNotifications.entries.removeAll { (key, posted) ->
        (key !in live).also { stale -> if (stale) posted.close() }
    }
}

private fun requestNotificationMessage(request: PendingBunkerRequest): String = listOfNotNull(
    request.type.describe(request.kind, request.encryptedContent),
    request.notificationAccountLine(AccountsStore.accounts.value),
).joinToString("\n")

private suspend fun postNativeRequestNotification(
    request: PendingBunkerRequest,
    requestNotifications: MutableMap<String, Notifier.Notification>,
): Boolean {
    val nativeButtons = withContext(Dispatchers.IO) { Notifier.nativeButtons() }
    if (!nativeButtons) return false
    val message = requestNotificationMessage(request)
    val actions = buildList {
        if (request.allowsQuickApprove()) {
            add(Notifier.Action("approve", getString(Res.string.d_approve)) { answerFromNotification(request, approve = true) })
        }
        add(Notifier.Action("reject", getString(Res.string.reject)) { answerFromNotification(request, approve = false) })
    }
    return withContext(Dispatchers.IO) {
        val posted = Notifier.notify(
            request.notificationTitle(),
            message,
            actions,
            onActivate = { DesktopTray.windowVisible.value = true },
        )
        if (posted != null) requestNotifications[request.queueKey] = posted
        posted != null
    }
}

@Composable
private fun ApplicationScope.DesktopAwtTray(
    language: String,
    pendingCount: Int,
    windowVisible: Boolean,
    lockStatus: PassphraseLock.Status,
) {
    val trayState = rememberTrayState()
    key(language) {
        Tray(
            icon = amberIconPainter(),
            state = trayState,
            tooltip = if (pendingCount == 0) getStringBlocking(Res.string.d_tray_tooltip) else getStringBlocking(Res.string.d_tray_pending, pendingCount),
            onAction = { DesktopTray.windowVisible.value = true },
            menu = {
                Item(
                    if (windowVisible) stringResource(Res.string.d_tray_hide) else stringResource(Res.string.d_tray_open),
                    onClick = { DesktopTray.windowVisible.value = !DesktopTray.windowVisible.value },
                )
                if (lockStatus == PassphraseLock.Status.UNLOCKED) {
                    Item(stringResource(Res.string.d_lock_now), onClick = { PassphraseLock.lock() })
                }
                Separator()
                Item(stringResource(Res.string.d_tray_quit), onClick = ::exitApplication)
            },
        )
    }
}

@Composable
private fun ApplicationScope.AmberMainWindow(
    windowState: WindowState,
    windowVisible: Boolean,
    pendingCount: Int,
    language: String,
    onCloseRequest: () -> Unit,
) {
    Window(
        onCloseRequest = onCloseRequest,
        state = windowState,
        visible = windowVisible,
        title = if (pendingCount == 0) BuildVariant.appName else "${BuildVariant.appName} ($pendingCount)",
        icon = amberIconPainter(),
        onPreviewKeyEvent = { event ->
            handleShortcut(
                event,
                hideWindow = onCloseRequest,
                quit = ::exitApplication,
            )
        },
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(MIN_WINDOW_SIZE.width.value.toInt(), MIN_WINDOW_SIZE.height.value.toInt())
        }
        val focused = LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(focused) { DesktopTray.windowFocused.value = focused }
        key(language) {
            NostrSignerTheme {
                App()
            }
        }
    }
}

private fun answerFromNotification(request: PendingBunkerRequest, approve: Boolean) {
    if (AmberDesktop.engine.pending.value.none { it.isSameEntry(request) }) return
    if (approve) RequestChoices.approve(request) else RequestChoices.reject(request)
}

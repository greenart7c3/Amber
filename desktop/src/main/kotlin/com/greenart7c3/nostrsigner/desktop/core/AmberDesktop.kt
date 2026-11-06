package com.greenart7c3.nostrsigner.desktop.core

import com.greenart7c3.nostrsigner.common.relays.LocalRelayServer
import com.greenart7c3.nostrsigner.common.relays.RelayHealthTracker
import com.greenart7c3.nostrsigner.commonsui.Res
import com.greenart7c3.nostrsigner.commonsui.d_local_bunker_port_in_use
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip04Dm.crypto.Nip04
import com.vitorpamplona.quartz.nip44Encryption.Nip44
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * Desktop counterpart of the Android `Amber` Application singleton: owns the
 * shared coroutine scope, the Nostr relay client, per-account stores, and the
 * NIP-46 engine.
 */
object AmberDesktop {
    const val TAG = "Amber"

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        AmberLogger.e("AmberCoroutine", "Caught exception: ${throwable.message}", throwable)
    }

    val applicationIOScope = CoroutineScope(Dispatchers.IO + SupervisorJob() + exceptionHandler)

    // The client is picked per dial, so Tor routing (and the local-relay
    // bypass) applies to every socket opened after a settings change.
    private val socketBuilder = object : WebsocketBuilder {
        override fun build(url: NormalizedRelayUrl, out: WebSocketListener) = OkHttpWebSocket(url, RelayHttpClients::clientFor, out)
    }

    val client: NostrClient by lazy { NostrClient(socketBuilder, applicationIOScope) }

    /** A fresh, throwaway relay client (e.g. to probe a relay before adding it). */
    fun newClient(): NostrClient = NostrClient(socketBuilder, applicationIOScope)

    // Authenticates with relays that request NIP-42 AUTH.
    private val authCoordinator by lazy {
        RelayAuthenticator(client, applicationIOScope) { _, event, _ ->
            accounts().map { it.signer.sign(event) }
        }
    }

    val engine: BunkerEngine by lazy {
        authCoordinator
        BunkerEngine(client, applicationIOScope)
    }

    private val stores = ConcurrentHashMap<String, AccountStore>()
    private val accountCache = ConcurrentHashMap<String, DesktopAccount>()

    fun store(npub: String): AccountStore = stores.computeIfAbsent(npub) { AccountStore(it) }

    suspend fun account(npub: String): DesktopAccount? {
        accountCache[npub]?.let { return it }
        val loaded = AccountManager.loadAccount(npub) ?: return null
        return accountCache.putIfAbsent(npub, loaded) ?: loaded
    }

    suspend fun accounts(): List<DesktopAccount> = AccountsStore.accounts.value.mapNotNull { account(it.npub) }

    /** The already-loaded account for [npub], if any (never decrypts a key). */
    fun loadedAccount(npub: String): DesktopAccount? = accountCache[npub]

    fun evictAccount(npub: String) {
        val evicted = accountCache.remove(npub)
        stores.remove(npub)
        evicted?.let { wipe(listOf(it)) }
    }

    /** Drops every decrypted account key from memory and wipes it (passphrase lock). */
    fun evictAllAccounts() {
        // Persist debounced log/history writes first: once locked, writeSecure
        // refuses to touch the encrypted files, so anything still volatile
        // would sit in memory only until the next unlock.
        stores.values.forEach { it.flushVolatile() }
        val evicted = accountCache.values.toList()
        accountCache.clear()
        wipe(evicted)
    }

    /**
     * Zeroes evicted private keys. Quartz's NIP-04/NIP-44 shared-secret caches
     * hold the private key arrays as map keys alongside derived conversation
     * keys, so they are emptied first (the caches are only a speed-up). A
     * signing call still in flight on an evicted account fails instead of
     * signing.
     */
    private fun wipe(accounts: List<DesktopAccount>) {
        Nip44.clearCache()
        Nip04.clearCache()
        accounts.forEach { it.wipe() }
    }

    /**
     * Rewrites every account's database in the current encryption state.
     * Invoked when the passphrase lock is enabled or removed so the on-disk
     * apps/history/logs are re-encrypted (or decrypted) immediately.
     */
    fun rewriteAllStores() {
        AccountsStore.accounts.value.forEach { store(it.npub).rewriteAll() }
    }

    /**
     * Reads every account's database into memory, so a format migration can
     * rewrite it after the on-disk formats it was read from stop being accepted.
     */
    fun loadAllStores() {
        AccountsStore.accounts.value.forEach { store(it.npub) }
    }

    val settings: DesktopSettings get() = SettingsStore.settings.value

    fun defaultRelays(): List<NormalizedRelayUrl> = settings.normalizedDefaultRelays()

    /**
     * Relays a NEW bunker connection uses: the local WebSocket relay (when the
     * opt-out setting is on and the server is actually serving) followed by
     * the default relays. Clients on this machine can then talk to the bunker
     * without any public relay.
     */
    fun newBunkerRelays(): List<NormalizedRelayUrl> = buildList {
        val local = if (settings.localBunkerServer) LocalBunkerRelay.relayUrl() else null
        if (local != null) add(local)
        addAll(defaultRelays())
    }.distinct()

    /** The relay port: the user's choice, or the automatic default for this build. */
    fun localBunkerPort(): Int = settings.localBunkerPort ?: if (BuildVariant.isDebug) LocalRelayServer.DEBUG_PORT else LocalRelayServer.DEFAULT_PORT

    /**
     * Changes the local relay's port, refused while any saved connection still
     * lists the relay: its bunker:// URI (and its subscription) would keep
     * pointing at the old port. Returns the string for the refusal, or null
     * when the port was applied.
     */
    fun changeLocalBunkerPort(newPort: Int): StringResource? {
        val current = RelayUrlNormalizer.normalizeOrNull("ws://127.0.0.1:${localBunkerPort()}/") ?: return null
        val inUse = AccountsStore.accounts.value.any { record ->
            AmberDesktop.store(record.npub).apps.value.any { app -> app.app.normalizedRelays().contains(current) }
        }
        if (inUse) return Res.string.d_local_bunker_port_in_use
        SettingsStore.update { it.copy(localBunkerPort = newPort) }
        // A running relay must come down first: start() never rebinds a live one.
        LocalBunkerRelay.stop()
        applyLocalBunkerServerSetting()
        return null
    }

    /**
     * Pubkeys the local relay treats as approved senders (never rate limited):
     * the client key of every saved connection (request senders) and the
     * connection key (the engine's own response publishes). Expired
     * connections don't count.
     */
    fun approvedRelaySenders(): Set<String> = buildSet {
        AccountsStore.accounts.value.forEach { record ->
            AmberDesktop.store(record.npub).apps.value.forEach { app ->
                if (!app.app.isExpired()) {
                    add(app.app.key)
                    if (app.app.localKey.isNotEmpty()) add(app.app.localPubKey())
                }
            }
        }
    }

    /**
     * Applies the "local bunker server" setting: starts or stops the loopback
     * relay, then redials so subscriptions follow (a stopped relay's sockets
     * are dropped; a fresh one is dialed at once).
     */
    fun applyLocalBunkerServerSetting() {
        if (settings.localBunkerServer) {
            LocalBunkerRelay.start(localBunkerPort())
        } else {
            LocalBunkerRelay.stop()
        }
        redialRelays()
    }

    /** Union of every connection's relays, mirroring `Amber.getSavedRelays`. */
    fun savedRelays(npub: String): Set<NormalizedRelayUrl> = buildSet {
        store(npub).apps.value.forEach { addAll(it.app.normalizedRelays()) }
        if (isEmpty()) addAll(defaultRelays())
    }

    /**
     * Every relay a saved NIP-46 connection of any account subscribes on, kept
     * live as accounts and connections change. Socket apps have no relays and
     * contribute nothing.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun connectionRelaysFlow(): Flow<Set<NormalizedRelayUrl>> = AccountsStore.accounts.flatMapLatest { records ->
        if (records.isEmpty()) {
            flowOf(emptySet())
        } else {
            combine(records.map { store(it.npub).apps }) { perAccount ->
                perAccount.flatMap { apps ->
                    apps.filter { it.app.localKey.isNotEmpty() }.flatMap { it.app.normalizedRelays() }
                }.toSet()
            }
        }
    }

    /**
     * When the client was disconnected on purpose (network lost, passphrase
     * lock): relay listeners skip their reconnect scheduling for a short
     * window after this timestamp, mirroring `Amber.intentionalDisconnectTime`.
     */
    @Volatile
    var intentionalDisconnectTime = 0L
        private set

    /** Mirrors `Amber.disconnectIntentionally`. */
    fun disconnectIntentionally() {
        intentionalDisconnectTime = System.currentTimeMillis()
        client.disconnect()
    }

    /**
     * Applies the Tor settings: starts or stops built-in Tor and redials every
     * relay so open sockets move to the new route (an open socket never
     * re-checks its proxy on its own).
     */
    fun applyTorSettings() {
        if (settings.torMode == TorMode.BUILTIN) {
            TorManager.start(applicationIOScope)
        } else {
            TorManager.stop(applicationIOScope)
        }
        redialRelays()
    }

    /** Drops every relay socket and reconnects them through the current route. */
    fun redialRelays() {
        if (PassphraseLock.isLocked()) return
        RelayHealthTracker.reset()
        disconnectIntentionally()
        applicationIOScope.launch {
            engine.updateFilter()
            client.connect()
        }
    }

    @Volatile
    private var torObserverStarted = false

    /**
     * Starts built-in Tor at launch when enabled, and redials relays whenever
     * it (re)gains its SOCKS listener — mirrors `Amber.startTorRecoveryObserver`.
     * Until then relays dial the fail-closed placeholder port and never leak.
     */
    fun startTor() {
        if (torObserverStarted) return
        torObserverStarted = true
        applicationIOScope.launch {
            var wasRunning = TorManager.isRunning.value
            TorManager.isRunning.collect { running ->
                if (running && !wasRunning && settings.torMode == TorMode.BUILTIN) {
                    AmberLogger.d(TAG, "Built-in Tor is up; refreshing relay connections")
                    redialRelays()
                }
                wasRunning = running
            }
        }
        if (settings.torMode == TorMode.BUILTIN) {
            TorManager.start(applicationIOScope)
        }
        Runtime.getRuntime().addShutdownHook(Thread { runCatching { TorManager.shutdown() } })
    }

    /** Mirrors `Amber.reconnect`: connect retries failed relays directly. */
    fun reconnect() {
        if (PassphraseLock.isLocked()) return
        val wasActive = client.isActive()
        // Always call connect() so that failed relays (socket == null) are
        // retried directly, bypassing the relay client's internal backoff
        // delay. For relays already connected, connect() is a safe no-op.
        client.connect()
        client.reconnect(wasActive)
    }
}

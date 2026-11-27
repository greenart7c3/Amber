package com.greenart7c3.nostrsigner.desktop.core

import com.greenart7c3.nostrsigner.common.NIP44V3_KIND_REQUIRED
import com.greenart7c3.nostrsigner.common.RELAY_AUTH_KIND
import com.greenart7c3.nostrsigner.common.SignerType
import com.greenart7c3.nostrsigner.common.contentScopedSignerTypes
import com.greenart7c3.nostrsigner.common.isBlockedByAuthWhitelist
import com.greenart7c3.nostrsigner.common.nip44v3SignerTypes
import com.greenart7c3.nostrsigner.common.relayAuthHost
import com.greenart7c3.nostrsigner.common.sanitizeAppName
import com.greenart7c3.nostrsigner.desktop.core.LocalSignerProtocol.RpcException
import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.utils.TimeUtils
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * NIP-5F local signer: serves CLI tools (nak, ngit, agents, ...) on a Unix
 * domain socket at `~/.local/share/nostr/signer.sock`, without relays.
 *
 * Every client goes through the same permission system and approval UI as a
 * NIP-46 connection ([BunkerEngine]): an unknown client first gets a connect
 * approval, which creates a [TRANSPORT_SOCKET] app, and its requests are then
 * auto-accepted, auto-rejected or queued per its stored permissions. Only the
 * transport differs: answers go straight back over the socket.
 *
 * Like NIP-55, a tool is approved through `get_public_key` (the account
 * picked in the approval is the key it gets); other calls from a tool that
 * is not approved are refused. The approval hands the tool a random secret
 * (stored only as a hash). The name a tool gives is just a label: on a new
 * connection it is recognized only by sending that secret in its hello, so
 * another program cannot claim an approved tool's permissions. Within the
 * connection that was approved it stays recognized either way.
 *
 * Access: the socket and its directory are owner-only, and on Linux/macOS a
 * peer running as another OS user is dropped. AF_UNIX also works on
 * Windows 10+, where the user-profile ACLs scope access.
 */
object LocalSigner {
    enum class Status { STOPPED, RUNNING, IN_USE, FAILED }

    private const val CLOSED_BEFORE_APPROVAL = "connection closed before approval"
    private const val NIP44V3_CONTEXT_KIND_PARAM = 1
    private const val NIP44V3_SCOPE_PARAM = 2

    val status = MutableStateFlow(Status.STOPPED)

    /** Debug builds get their own socket so they never take over a release install's. */
    fun socketPath(): Path = Path.of(System.getProperty("user.home"), ".local", "share", "nostr", "signer${BuildVariant.suffix}.sock")

    private var server: ServerSocketChannel? = null
    private val sessions = ConcurrentHashMap<String, ClientSession>()
    private var shutdownHook: Thread? = null

    @Synchronized
    fun start(): Status {
        if (server != null) return status.value
        val path = socketPath()
        status.value = try {
            val dir = path.parent.toFile()
            dir.mkdirs()
            privateDirProblem(path.parent)?.let { problem ->
                AmberLogger.e("LocalSigner", "Not starting: ${path.parent} $problem")
                return Status.FAILED.also { status.value = it }
            }
            if (!isPosix(path)) AppDirs.restrictToOwner(dir)
            if (Files.exists(path)) {
                if (isLive(path)) {
                    AmberLogger.i("LocalSigner", "$path is served by another signer; not taking it over")
                    return Status.IN_USE.also { status.value = it }
                }
                Files.deleteIfExists(path)
            }
            val channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
            channel.bind(UnixDomainSocketAddress.of(path))
            if (isPosix(path)) restrictSocketToOwnerOrClose(channel, path)
            server = channel
            Thread({ acceptLoop(channel) }, "amber-local-signer").apply { isDaemon = true }.start()
            if (shutdownHook == null) {
                shutdownHook = Thread { runCatching { Files.deleteIfExists(path) } }.also { Runtime.getRuntime().addShutdownHook(it) }
            }
            AmberLogger.i("LocalSigner", "Listening on $path")
            Status.RUNNING
        } catch (e: Exception) {
            AmberLogger.e("LocalSigner", "Could not start on $path", e)
            Status.FAILED
        }
        return status.value
    }

    private fun restrictSocketToOwnerOrClose(channel: ServerSocketChannel, path: Path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"))
        } catch (e: Exception) {
            runCatching { channel.close() }
            runCatching { Files.deleteIfExists(path) }
            throw e
        }
    }

    @Suppress("NoComments")
    @Synchronized
    fun stop() {
        val channel = server ?: run {
            if (status.value != Status.STOPPED) status.value = Status.STOPPED
            return
        }
        server = null
        runCatching { channel.close() }
        // Not toList(): it reads size, then next(), and a session ending in
        // between makes next() throw. The map's own iterator tolerates that.
        sessions.values.forEach { it.close() }
        runCatching { Files.deleteIfExists(socketPath()) }
        status.value = Status.STOPPED
    }

    private fun isPosix(path: Path): Boolean = path.fileSystem.supportedFileAttributeViews().contains("posix")

    /**
     * Why [dir] can't hold the socket, or null when it can. On POSIX systems
     * it must be a real directory (not a symlink) owned by this user, and is
     * set to 0700 and read back, so the documented 0600-socket-in-0700-dir
     * guarantee is enforced rather than best-effort. Windows has no POSIX
     * permissions; the user-profile ACLs scope access there.
     */
    @Suppress("NoComments")
    internal fun privateDirProblem(dir: Path): String? {
        if (!isPosix(dir)) return null
        return try {
            when {
                Files.isSymbolicLink(dir) -> "is a symbolic link"
                !Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS) -> "is not a directory"
                Files.getOwner(dir, LinkOption.NOFOLLOW_LINKS).name != System.getProperty("user.name") -> "is owned by another user"
                else -> {
                    val ownerOnly = PosixFilePermissions.fromString("rwx------")
                    Files.setPosixFilePermissions(dir, ownerOnly)
                    if (Files.getPosixFilePermissions(dir, LinkOption.NOFOLLOW_LINKS) == ownerOnly) null else "could not be restricted to its owner"
                }
            }
        } catch (e: Exception) {
            "could not be checked: ${e.message}"
        }
    }

    private fun isLive(path: Path): Boolean = runCatching {
        SocketChannel.open(StandardProtocolFamily.UNIX).use { it.connect(UnixDomainSocketAddress.of(path)) }
    }.isSuccess

    private fun acceptLoop(channel: ServerSocketChannel) {
        while (channel.isOpen) {
            val client = try {
                channel.accept()
            } catch (_: Exception) {
                break
            }
            if (!isSameUser(client)) {
                AmberLogger.i("LocalSigner", "Dropped a connection from another OS user")
                runCatching { client.close() }
                continue
            }
            val session = ClientSession(UUID.randomUUID().toString().take(8), client)
            sessions[session.id] = session
            Thread.ofVirtual().name("amber-local-signer-${session.id}").start { serve(session) }
        }
    }

    private fun isSameUser(client: SocketChannel): Boolean = isSameUserPeer(client, socketPath())

    private class ClientSession(val id: String, val channel: SocketChannel) {
        @Volatile var name: String = ""

        @Volatile var inputClosed = false

        @Volatile var secret: String? = null

        val secretHash: String? get() = secret?.let(::hashSecret)

        val appsApprovedOnThisConnection: MutableSet<String> = ConcurrentHashMap.newKeySet()

        val appResolutionMutex = Mutex()

        private val writeLock = Any()

        val displayName: String get() = name.ifBlank { "Local client" }

        fun send(line: String) {
            synchronized(writeLock) {
                val bytes = ByteBuffer.wrap((line + "\n").toByteArray(Charsets.UTF_8))
                while (bytes.hasRemaining()) channel.write(bytes)
            }
        }

        fun trySend(line: String) {
            runCatching { send(line) }
        }

        fun close() {
            runCatching { channel.close() }
        }

        private val sequence = AtomicLong()

        fun uniqueQueueId(clientId: String) = "socket-$id-${sequence.incrementAndGet()}-$clientId"

        fun owns(req: PendingBunkerRequest) = req.request.id.startsWith("socket-$id-")
    }

    @Suppress("NoComments")
    private fun serve(session: ClientSession) {
        try {
            session.send(LocalSignerProtocol.handshake())
            val input = Channels.newInputStream(session.channel)
            val inFlight = mutableListOf<Job>()
            var first = true
            while (true) {
                val line = readLine(input) ?: break
                if (line.isBlank()) continue
                if (first) {
                    first = false
                    val hello = runCatching { LocalSignerProtocol.parseHello(line) }.getOrNull()
                    if (hello != null) {
                        session.name = sanitizeAppName(hello.name)
                        session.secret = hello.secret
                        continue
                    }
                }
                inFlight += AmberDesktop.applicationIOScope.launch { process(session, line) }
                inFlight.removeAll { it.isCompleted }
            }
            // EOF can be a half-close (`echo request | nc -U ...`): the client is
            // done sending but still reading, so answer what it already asked.
            // It can't be told apart from a tool that exited, though, so prompts
            // don't wait: they're dropped (and answered with an error) instead
            // of lingering in the approval list for a client that is gone.
            session.inputClosed = true
            AmberDesktop.engine.dropPending(CLOSED_BEFORE_APPROVAL, session::owns)
            runBlocking { inFlight.joinAll() }
        } catch (_: LineTooLongException) {
            session.trySend(LocalSignerProtocol.failure("", LocalSignerProtocol.INVALID_REQUEST, "line too long"))
        } catch (e: Exception) {
            AmberLogger.d("LocalSigner", "Client ${session.id} ended: ${e.message}")
        } finally {
            sessions.remove(session.id)
            session.close()
            AmberDesktop.engine.dropPending(CLOSED_BEFORE_APPROVAL, session::owns)
        }
    }

    private class LineTooLongException : Exception()

    private fun readLine(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buffer.size() == 0) null else buffer.toString(Charsets.UTF_8)
            if (b == '\n'.code) return buffer.toString(Charsets.UTF_8).trimEnd('\r')
            if (buffer.size() >= LocalSignerProtocol.MAX_LINE_BYTES) throw LineTooLongException()
            buffer.write(b)
        }
    }

    private suspend fun process(session: ClientSession, line: String) {
        val reply = try {
            val rpc = LocalSignerProtocol.parseRequest(line)
            try {
                val (result, newSecret) = execute(session, rpc)
                LocalSignerProtocol.success(rpc.id, result, newSecret)
            } catch (e: RpcException) {
                LocalSignerProtocol.failure(rpc.id, e.code, e.message ?: "")
            }
        } catch (e: RpcException) {
            LocalSignerProtocol.failure(LocalSignerProtocol.idOf(line), e.code, e.message ?: "")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AmberLogger.e("LocalSigner", "Request failed", e)
            LocalSignerProtocol.failure(LocalSignerProtocol.idOf(line), LocalSignerProtocol.INTERNAL_ERROR, "internal error")
        }
        session.trySend(reply)
    }

    private suspend fun execute(session: ClientSession, rpc: LocalSignerProtocol.Rpc): Pair<com.fasterxml.jackson.databind.JsonNode, String?> {
        val call = LocalSignerProtocol.toCall(rpc)

        if (PassphraseLock.isLocked()) throw RpcException(LocalSignerProtocol.INTERNAL_ERROR, "signer locked")
        val accounts = AmberDesktop.accounts()
        if (accounts.isEmpty()) throw RpcException(LocalSignerProtocol.KEY_NOT_FOUND, "no accounts")
        val target = call.account?.let { requested ->
            val npub = LocalSignerProtocol.npubOf(requested) ?: throw RpcException(LocalSignerProtocol.KEY_NOT_FOUND, "unknown account")
            accounts.firstOrNull { it.npub == npub } ?: throw RpcException(LocalSignerProtocol.KEY_NOT_FOUND, "unknown account")
        }

        val (acc, app, newSecret) = session.appResolutionMutex.withLock {
            resolveOrConnect(session, accounts, target, canConnect = call.type == SignerType.GET_PUBLIC_KEY)
        }
        val store = AmberDesktop.store(acc.npub)
        store.addLog("socket", "local-signer", "${call.method} from ${session.displayName}")

        return evaluate(session, rpc.id, call, acc, app) to newSecret
    }

    private fun findApp(store: AccountStore, session: ClientSession): AppWithPermissions? {
        val unexpiredSocketApps = store.apps.value.filter { it.app.isLocalSocket && !it.app.isExpired() }
        val hash = session.secretHash
        return unexpiredSocketApps.firstOrNull { it.app.key in session.appsApprovedOnThisConnection || (hash != null && constantTimeEquals(it.app.socketSecretHash, hash)) }
    }

    private suspend fun resolveOrConnect(
        session: ClientSession,
        accounts: List<DesktopAccount>,
        target: DesktopAccount?,
        canConnect: Boolean,
    ): Triple<DesktopAccount, AppWithPermissions, String?> {
        val current = SettingsStore.settings.value.currentAccount
        val candidates = target?.let { listOf(it) } ?: accounts.sortedByDescending { it.npub == current }
        for (acc in candidates) {
            findApp(AmberDesktop.store(acc.npub), session)?.let { return Triple(acc, it, null) }
        }
        if (!canConnect) throw RpcException(LocalSignerProtocol.USER_DECLINED, "not approved: call get_public_key first")
        return connect(session, target ?: candidates.first(), allowAccountSwitch = target == null)
    }

    private suspend fun connect(
        session: ClientSession,
        acc: DesktopAccount,
        allowAccountSwitch: Boolean,
    ): Triple<DesktopAccount, AppWithPermissions, String?> {
        val appKey = "socket:${UUID.randomUUID()}"
        val issuedNow = if (session.secret == null) newSecret() else null
        val secret = session.secret ?: issuedNow!!
        val decided = CompletableDeferred<BunkerResponse>()
        queuePrompt(
            session,
            PendingBunkerRequest(
                request = BunkerRequest(session.uniqueQueueId("connect-${UUID.randomUUID().toString().take(8)}"), "connect", emptyArray()),
                type = SignerType.CONNECT,
                account = acc,
                localKey = appKey,
                relays = emptyList(),
                appName = session.displayName,
                result = "ack",
                expiresAt = TimeUtils.now() + BunkerEngine.PENDING_TTL_SECONDS,
                responder = { response -> decided.complete(response) },
                socketSecretHash = hashSecret(secret),
                allowAccountSwitch = allowAccountSwitch,
            ),
        )
        decided.await().error?.let { throw RpcException(LocalSignerProtocol.codeFor(it), it) }
        session.secret = secret
        session.appsApprovedOnThisConnection += appKey

        for (candidate in AmberDesktop.accounts()) {
            AmberDesktop.store(candidate.npub).lookup.getByKey(appKey)?.let { return Triple(candidate, it, issuedNow) }
        }
        throw RpcException(LocalSignerProtocol.INTERNAL_ERROR, "connection was not saved")
    }

    private suspend fun evaluate(
        session: ClientSession,
        clientId: String,
        call: LocalSignerProtocol.Call,
        acc: DesktopAccount,
        app: AppWithPermissions,
    ): com.fasterxml.jackson.databind.JsonNode {
        val store = AmberDesktop.store(acc.npub)
        val type = call.type
        val request = LocalSignerProtocol.bunkerRequest(session.uniqueQueueId(clientId), call)
        val kind = permissionKind(request, call)

        val relayHost = if (request is BunkerRequestSign && kind == RELAY_AUTH_KIND) relayAuthHost(request.event.tags) else ""
        if (isBlockedByAuthWhitelist(kind, relayHost, AmberDesktop.settings.authWhitelist)) {
            store.addLog("socket", "local-signer", "Relay auth for $relayHost is not in the auth whitelist")
            store.addHistory(HistoryRecord(app.app.key, type.toString(), kind, TimeUtils.now(), false))
            throw RpcException(LocalSignerProtocol.USER_DECLINED, LocalSignerProtocol.USER_REJECTED)
        }

        val computed = computeOrRefuse(store, request, type, acc)
        val encryptedContent = if (type in contentScopedSignerTypes) EncryptedContent.classify(computed.preview) else null
        val permission = lookupPermission(store, app.app.key, type, kind, encryptedContent, relayHost)

        val accepted = isRemembered(app.app.signPolicy, permission)
            ?: return ask(
                session,
                PendingBunkerRequest(
                    request = request,
                    type = type,
                    account = acc,
                    localKey = app.app.key,
                    relays = emptyList(),
                    appName = app.app.displayName(),
                    kind = kind,
                    preview = computed.preview,
                    sealPreview = computed.sealPreview,
                    result = computed.result,
                    encryptedContent = encryptedContent,
                    nip44v3Scope = if (type in nip44v3SignerTypes) call.params.getOrElse(NIP44V3_SCOPE_PARAM) { "" } else "",
                    relayHost = relayHost,
                    expiresAt = TimeUtils.now() + BunkerEngine.PENDING_TTL_SECONDS,
                ),
            )
        store.addHistory(HistoryRecord(app.app.key, type.toString(), kind, TimeUtils.now(), accepted))
        if (!accepted) throw RpcException(LocalSignerProtocol.USER_DECLINED, LocalSignerProtocol.USER_REJECTED)
        store.upsert(app.copy(app = app.app.copy(lastUsed = TimeUtils.now())))
        return LocalSignerProtocol.resultNode(type, computed.result)
    }

    private fun permissionKind(request: BunkerRequest, call: LocalSignerProtocol.Call): Int? = when {
        request is BunkerRequestSign -> request.event.kind
        call.type in nip44v3SignerTypes -> call.params.getOrNull(NIP44V3_CONTEXT_KIND_PARAM)?.toIntOrNull()
            ?: throw RpcException(LocalSignerProtocol.INVALID_PARAMS, NIP44V3_KIND_REQUIRED)
        else -> null
    }

    private suspend fun computeOrRefuse(store: AccountStore, request: BunkerRequest, type: SignerType, acc: DesktopAccount): ComputedResult = try {
        computeResult(request, type, acc)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        store.addLog("socket", "local-signer", "Rejecting request that cannot be fulfilled: ${e.message}")
        throw RpcException(LocalSignerProtocol.INVALID_PARAMS, "could not process the request")
    }

    private suspend fun ask(session: ClientSession, req: PendingBunkerRequest): com.fasterxml.jackson.databind.JsonNode {
        val answer = CompletableDeferred<BunkerResponse>()
        queuePrompt(session, req.copy(responder = { response -> answer.complete(response) }))
        val response = answer.await()
        response.error?.let { throw RpcException(LocalSignerProtocol.codeFor(it), it) }
        return LocalSignerProtocol.resultNode(req.type, response.result ?: "")
    }

    @Suppress("NoComments")
    private fun queuePrompt(session: ClientSession, req: PendingBunkerRequest) {
        val engine = AmberDesktop.engine
        engine.addPending(req)
        // Checked after queuing: either this sees the flag or serve's drop sees the request.
        if (session.inputClosed) engine.dropPending(CLOSED_BEFORE_APPROVAL) { it.request.id == req.request.id }
    }

    private fun newSecret(): String = RandomInstance.bytes(32).toHexKey()

    private fun hashSecret(secret: String): String = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8)).toHexKey()
}

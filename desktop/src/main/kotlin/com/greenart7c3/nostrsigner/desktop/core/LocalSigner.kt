package com.greenart7c3.nostrsigner.desktop.core

import com.greenart7c3.nostrsigner.desktop.core.LocalSignerProtocol.RpcException
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.utils.RandomInstance
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

    val status = MutableStateFlow(Status.STOPPED)

    /** Debug builds get their own socket so they never take over a release install's. */
    fun socketPath(): Path = Path.of(System.getProperty("user.home"), ".local", "share", "nostr", "signer${BuildVariant.suffix}.sock")

    private var server: ServerSocketChannel? = null
    private val sessions = ConcurrentHashMap<String, ClientSession>()
    private var shutdownHook: Thread? = null

    /** Starts or stops the server to match the setting. */
    fun sync() {
        if (SettingsStore.settings.value.localSigner) start() else stop()
    }

    @Synchronized
    fun start(): Status {
        if (server != null) return status.value
        val path = socketPath()
        status.value = try {
            val dir = path.parent.toFile()
            dir.mkdirs()
            AppDirs.restrictToOwner(dir)
            if (Files.exists(path)) {
                if (isLive(path)) {
                    AmberLogger.i("LocalSigner", "$path is served by another signer; not taking it over")
                    return Status.IN_USE.also { status.value = it }
                }
                Files.deleteIfExists(path)
            }
            val channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
            channel.bind(UnixDomainSocketAddress.of(path))
            runCatching { Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------")) }
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

    @Synchronized
    fun stop() {
        val channel = server ?: run {
            if (status.value != Status.STOPPED) status.value = Status.STOPPED
            return
        }
        server = null
        runCatching { channel.close() }
        sessions.values.toList().forEach { it.close() }
        runCatching { Files.deleteIfExists(socketPath()) }
        status.value = Status.STOPPED
    }

    private fun isLive(path: Path): Boolean = runCatching {
        SocketChannel.open(StandardProtocolFamily.UNIX).use { it.connect(UnixDomainSocketAddress.of(path)) }
    }.isSuccess

    private fun acceptLoop(channel: ServerSocketChannel) {
        while (channel.isOpen) {
            val client = try {
                channel.accept()
            } catch (e: Exception) {
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

    /** Peer credentials exist on Linux and macOS; elsewhere the owner-only socket is the guard. */
    private fun isSameUser(client: SocketChannel): Boolean = runCatching {
        val peer = client.getOption(jdk.net.ExtendedSocketOptions.SO_PEERCRED)
        peer.user().name == Files.getOwner(socketPath()).name
    }.getOrDefault(true)

    private class ClientSession(val id: String, val channel: SocketChannel) {
        @Volatile var name: String = ""

        /** Set at EOF: from then on nothing of this client may wait for a decision. */
        @Volatile var inputClosed = false

        /** The secret this connection presented in its hello or was given on approval. */
        @Volatile var secret: String? = null

        val secretHash: String? get() = secret?.let(::hashSecret)

        /** Apps approved on this very connection: known even if the tool ignores the secret. */
        val approvedApps: MutableSet<String> = ConcurrentHashMap.newKeySet()

        /** Serializes app resolution so overlapping requests ask for one connect, not several. */
        val connectMutex = Mutex()

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

        /**
         * Queue id for a request: scoped to this connection, and unique even
         * when a client reuses an id (the queue drops duplicate ids).
         */
        fun internalId(clientId: String) = "socket-$id-${sequence.incrementAndGet()}-$clientId"

        fun owns(req: PendingBunkerRequest) = req.request.id.startsWith("socket-$id-")
    }

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
                        session.name = hello.name
                        session.secret = hello.secret
                        continue
                    }
                    // No handshake reply: an unnamed client sending requests right away.
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
        } catch (e: LineTooLongException) {
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

    /** The result, plus the secret when this request got the tool approved. */
    private suspend fun execute(session: ClientSession, rpc: LocalSignerProtocol.Rpc): Pair<com.fasterxml.jackson.databind.JsonNode, String?> {
        // Malformed calls are reported as such before anything else.
        val call = LocalSignerProtocol.toCall(rpc)

        if (PassphraseLock.isLocked()) throw RpcException(LocalSignerProtocol.INTERNAL_ERROR, "signer locked")
        val accounts = AmberDesktop.accounts()
        if (accounts.isEmpty()) throw RpcException(LocalSignerProtocol.KEY_NOT_FOUND, "no accounts")
        val target = call.account?.let { requested ->
            val npub = LocalSignerProtocol.npubOf(requested) ?: throw RpcException(LocalSignerProtocol.KEY_NOT_FOUND, "unknown account")
            accounts.firstOrNull { it.npub == npub } ?: throw RpcException(LocalSignerProtocol.KEY_NOT_FOUND, "unknown account")
        }

        val (acc, app, newSecret) = session.connectMutex.withLock {
            resolveOrConnect(session, accounts, target, canConnect = call.type == SignerType.GET_PUBLIC_KEY)
        }
        val store = AmberDesktop.store(acc.npub)
        store.addLog("socket", "local-signer", "${call.method} from ${session.displayName}")

        return evaluate(session, rpc.id, call, acc, app) to newSecret
    }

    private fun findApp(store: AccountStore, session: ClientSession): AppWithPermissions? {
        val socketApps = store.apps.value.filter { it.app.isLocalSocket }
        val hash = session.secretHash
        return socketApps.firstOrNull { it.app.key in session.approvedApps || (hash != null && it.app.socketSecretHash == hash) }
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

        val acc = target ?: candidates.first()
        if (!canConnect) throw RpcException(LocalSignerProtocol.USER_DECLINED, "not approved: call get_public_key first")
        val appKey = "socket:${UUID.randomUUID()}"
        // One secret per tool: a tool already holding one keeps it for further accounts.
        val issued = if (session.secret == null) newSecret() else null
        val secret = session.secret ?: issued!!
        val decided = CompletableDeferred<BunkerResponse>()
        queuePrompt(
            session,
            PendingBunkerRequest(
                request = BunkerRequest(session.internalId("connect-${UUID.randomUUID().toString().take(8)}"), "connect", emptyArray()),
                type = SignerType.CONNECT,
                account = acc,
                localKey = appKey,
                relays = emptyList(),
                appName = session.displayName,
                result = "ack",
                expiresAt = TimeUtils.now() + BunkerEngine.PENDING_TTL_SECONDS,
                responder = { response -> decided.complete(response) },
                socketSecretHash = hashSecret(secret),
                allowAccountSwitch = target == null,
            ),
        )
        decided.await().error?.let { throw RpcException(LocalSignerProtocol.codeFor(it), it) }
        session.secret = secret
        session.approvedApps += appKey

        // The approval may have picked another account: find where the app landed.
        for (candidate in AmberDesktop.accounts()) {
            AmberDesktop.store(candidate.npub).getByKey(appKey)?.let { return Triple(candidate, it, issued) }
        }
        throw RpcException(LocalSignerProtocol.INTERNAL_ERROR, "connection was not saved")
    }

    /** Mirrors BunkerEngine.handleRequest from the permission check on. */
    private suspend fun evaluate(
        session: ClientSession,
        clientId: String,
        call: LocalSignerProtocol.Call,
        acc: DesktopAccount,
        app: AppWithPermissions,
    ): com.fasterxml.jackson.databind.JsonNode {
        val engine = AmberDesktop.engine
        val store = AmberDesktop.store(acc.npub)
        val type = call.type
        val request = LocalSignerProtocol.bunkerRequest(session.internalId(clientId), call)
        // NIP-44 v3 grants are per context kind, carried at params[1].
        val kind = when {
            request is BunkerRequestSign -> request.event.kind
            type in nip44v3SignerTypes -> call.params.getOrNull(1)?.toIntOrNull()
                ?: throw RpcException(LocalSignerProtocol.INVALID_PARAMS, "kind is required for nip44v3")
            else -> null
        }

        val computed = try {
            engine.computeResult(request, type, acc)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            store.addLog("socket", "local-signer", "Rejecting request that cannot be fulfilled: ${e.message}")
            throw RpcException(LocalSignerProtocol.INVALID_PARAMS, "could not process the request")
        }

        val encryptedContent = if (type in contentScopedSignerTypes) EncryptedContent.classify(computed.preview) else null
        val permission = engine.lookupPermission(store, app.app.key, type, kind, encryptedContent)

        when (isRemembered(app.app.signPolicy, permission)) {
            true -> {
                store.addHistory(HistoryRecord(app.app.key, type.toString(), kind, TimeUtils.now(), true))
                store.upsert(app.copy(app = app.app.copy(lastUsed = TimeUtils.now())))
                return LocalSignerProtocol.resultNode(type, computed.result)
            }

            false -> {
                store.addHistory(HistoryRecord(app.app.key, type.toString(), kind, TimeUtils.now(), false))
                throw RpcException(LocalSignerProtocol.USER_DECLINED, LocalSignerProtocol.USER_REJECTED)
            }

            null -> {
                val answer = CompletableDeferred<BunkerResponse>()
                queuePrompt(
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
                        result = computed.result,
                        encryptedContent = encryptedContent,
                        nip44v3Scope = if (type in nip44v3SignerTypes) call.params.getOrElse(2) { "" } else "",
                        expiresAt = TimeUtils.now() + BunkerEngine.PENDING_TTL_SECONDS,
                        responder = { response -> answer.complete(response) },
                    ),
                )
                val response = answer.await()
                response.error?.let { throw RpcException(LocalSignerProtocol.codeFor(it), it) }
                return LocalSignerProtocol.resultNode(type, response.result ?: "")
            }
        }
    }

    /** Queues an approval; a client whose input already closed gets the error at once. */
    private fun queuePrompt(session: ClientSession, req: PendingBunkerRequest) {
        val engine = AmberDesktop.engine
        engine.addPending(req)
        // Checked after queuing: either this sees the flag or serve's drop sees the request.
        if (session.inputClosed) engine.dropPending(CLOSED_BEFORE_APPROVAL) { it.request.id == req.request.id }
    }

    private fun newSecret(): String = RandomInstance.bytes(32).toHexKey()

    private fun hashSecret(secret: String): String = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8)).toHexKey()
}

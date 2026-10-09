package com.greenart7c3.nostrsigner.desktop

import com.fasterxml.jackson.databind.JsonNode
import com.greenart7c3.nostrsigner.desktop.core.AccountManager
import com.greenart7c3.nostrsigner.desktop.core.AmberDesktop
import com.greenart7c3.nostrsigner.desktop.core.DesktopAccount
import com.greenart7c3.nostrsigner.desktop.core.LocalSigner
import com.greenart7c3.nostrsigner.desktop.core.LocalSignerProtocol
import com.greenart7c3.nostrsigner.desktop.core.PendingBunkerRequest
import com.greenart7c3.nostrsigner.desktop.core.RememberType
import com.greenart7c3.nostrsigner.desktop.core.SettingsStore
import com.greenart7c3.nostrsigner.desktop.core.SignerType
import com.greenart7c3.nostrsigner.desktop.core.TRANSPORT_SOCKET
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The NIP-5F socket end to end: real socket, real engine queue, no relays. */
class LocalSignerTest {
    private val clients = mutableListOf<TestClient>()

    private class TestClient(name: String?, secret: String? = null) : AutoCloseable {
        val channel: SocketChannel = SocketChannel.open(StandardProtocolFamily.UNIX).apply {
            connect(UnixDomainSocketAddress.of(LocalSigner.socketPath()))
        }
        private val lines = LinkedBlockingQueue<String>()
        val handshake: JsonNode

        init {
            Thread {
                runCatching {
                    Channels.newInputStream(channel).bufferedReader().forEachLine { lines.add(it) }
                }
            }.apply { isDaemon = true }.start()
            handshake = JacksonMapper.mapper.readTree(next())
            if (name != null) {
                val hello = JacksonMapper.mapper.createObjectNode().put("client", name)
                if (secret != null) hello.put("secret", secret)
                write(JacksonMapper.mapper.writeValueAsString(hello))
            }
        }

        fun write(line: String) {
            val bytes = ByteBuffer.wrap((line + "\n").toByteArray())
            while (bytes.hasRemaining()) channel.write(bytes)
        }

        fun next(timeoutSeconds: Long = 10): String = lines.poll(timeoutSeconds, TimeUnit.SECONDS) ?: error("no reply from the signer")

        fun call(id: String, method: String, vararg params: Any) {
            val node = JacksonMapper.mapper.createObjectNode().put("id", id).put("method", method)
            val array = node.putArray("params")
            params.forEach { if (it is JsonNode) array.add(it) else array.add(it.toString()) }
            write(JacksonMapper.mapper.writeValueAsString(node))
        }

        fun reply(timeoutSeconds: Long = 10): JsonNode = JacksonMapper.mapper.readTree(next(timeoutSeconds))

        override fun close() {
            runCatching { channel.close() }
        }
    }

    private fun client(name: String?, secret: String? = null) = TestClient(name, secret).also { clients += it }

    private fun uniqueName() = "cli-${UUID.randomUUID().toString().take(6)}"

    private suspend fun awaitPending(type: SignerType, appName: String): PendingBunkerRequest = withTimeout(10_000) {
        AmberDesktop.engine.pending.first { list -> list.any { it.type == type && it.appName == appName } }
            .first { it.type == type && it.appName == appName }
    }

    private suspend fun newAccount(): DesktopAccount = AccountManager.addAccount(KeyPair(), name = "socket-${UUID.randomUUID().toString().take(4)}")

    private suspend fun connected(name: String, account: DesktopAccount): TestClient {
        val c = client(name)
        c.call("pk", "get_public_key", account.hexKey)
        AmberDesktop.engine.approve(awaitPending(SignerType.CONNECT, name), RememberType.NEVER).join()
        val reply = c.reply()
        assertEquals(account.hexKey, reply.get("result").asText())
        return c
    }

    @Before
    fun startServer() {
        LocalSigner.stop()
        assertEquals(LocalSigner.Status.RUNNING, LocalSigner.start())
    }

    @After
    fun stopServer() {
        clients.forEach { it.close() }
        LocalSigner.stop()
    }

    @Test
    fun socketIsOwnerOnlyAndGreetsWithTheMethods() {
        val perms = Files.getPosixFilePermissions(LocalSigner.socketPath())
        assertEquals("rw-------", java.nio.file.attribute.PosixFilePermissions.toString(perms))
        val c = client(uniqueName())
        val methods = c.handshake.get("supported_methods").map { it.asText() }
        assertTrue("sign_event" in methods && "get_public_key" in methods)
        assertTrue("list_public_keys" !in methods)
    }

    @Test
    fun unknownClientIsApprovedThenSignsWithRememberedPermission() = runBlocking {
        val account = newAccount()
        val name = uniqueName()
        val c = connected(name, account)

        // Saved as a local socket app with no relays.
        val app = AmberDesktop.store(account.npub).apps.value.first { it.app.name == name }.app
        assertEquals(TRANSPORT_SOCKET, app.transport)
        assertTrue(app.relays.isEmpty() && app.localKey.isEmpty())

        val template = JacksonMapper.mapper.readTree("""{"kind":1,"content":"hello","tags":[],"created_at":1700000000}""")
        c.call("s1", "sign_event", template, account.hexKey)
        AmberDesktop.engine.approve(awaitPending(SignerType.SIGN_EVENT, name), RememberType.ALWAYS).join()
        val first = c.reply().get("result")
        val event = Event.fromJson(first.toString())
        assertTrue(event.verify())
        assertEquals(account.hexKey, event.pubKey)

        // Remembered: the second one is signed without a prompt.
        c.call("s2", "sign_event", template, account.hexKey)
        val second = c.reply()
        assertTrue(Event.fromJson(second.get("result").toString()).verify())
        assertTrue(AmberDesktop.engine.pending.value.none { it.appName == name })
    }

    @Test
    fun rejectionAnswersUserDeclined() = runBlocking {
        val account = newAccount()
        val name = uniqueName()
        val c = connected(name, account)
        c.call("e1", "nip44_encrypt", KeyPair().pubKey.toHexKey(), "secret", account.hexKey)
        AmberDesktop.engine.reject(awaitPending(SignerType.NIP44_ENCRYPT, name), RememberType.NEVER).join()
        assertEquals(LocalSignerProtocol.USER_DECLINED, c.reply().get("error").get("code").asInt())
    }

    @Test
    fun onlyGetPublicKeyGetsAToolApproved() = runBlocking {
        val account = newAccount()
        val name = uniqueName()
        val c = client(name)
        c.call("s", "sign_event", JacksonMapper.mapper.readTree("""{"kind":1,"content":"x","tags":[],"created_at":1700000000}"""), account.hexKey)
        val reply = c.reply()
        assertEquals(LocalSignerProtocol.USER_DECLINED, reply.get("error").get("code").asInt())
        assertTrue(reply.get("error").get("message").asText().contains("get_public_key"))
        assertTrue("no prompt for an unapproved tool", AmberDesktop.engine.pending.value.none { it.appName == name })

        c.call("c", "connect")
        assertEquals(LocalSignerProtocol.METHOD_NOT_SUPPORTED, c.reply().get("error").get("code").asInt())
    }

    @Test
    fun approvalHandsOutASecretThatAloneIdentifiesTheTool() = runBlocking {
        val account = newAccount()
        val name = uniqueName()
        val first = client(name)
        first.call("pk", "get_public_key", account.hexKey)
        AmberDesktop.engine.approve(awaitPending(SignerType.CONNECT, name), RememberType.NEVER).join()
        val approved = first.reply()
        assertEquals(account.hexKey, approved.get("result").asText())
        val secret = approved.get("secret").asText()
        assertEquals(64, secret.length)
        val app = AmberDesktop.store(account.npub).apps.value.first { it.app.name == name }.app
        assertTrue("only the hash is stored", app.socketSecretHash.length == 64 && app.socketSecretHash != secret)

        // Still recognized on the same connection; no second secret.
        first.call("pk2", "get_public_key", account.hexKey)
        assertTrue(first.reply().get("secret") == null)

        // A new connection with the secret: matched, no prompt, whatever name it uses.
        val again = client("renamed", secret)
        again.call("pk", "get_public_key", account.hexKey)
        assertEquals(account.hexKey, again.reply().get("result").asText())

        // The same name without the secret is a stranger: refused, and only a new approval helps.
        val impostor = client(name)
        impostor.call("s", "sign_event", JacksonMapper.mapper.readTree("""{"kind":1,"content":"x","tags":[],"created_at":1700000000}"""), account.hexKey)
        assertEquals(LocalSignerProtocol.USER_DECLINED, impostor.reply().get("error").get("code").asInt())
        impostor.call("pk", "get_public_key", account.hexKey)
        AmberDesktop.engine.reject(awaitPending(SignerType.CONNECT, name), RememberType.NEVER).join()
        assertEquals(LocalSignerProtocol.USER_DECLINED, impostor.reply().get("error").get("code").asInt())

        // A made-up secret doesn't help either.
        val guesser = client(name, "0".repeat(64))
        guesser.call("s", "sign_event", JacksonMapper.mapper.readTree("""{"kind":1,"content":"x","tags":[],"created_at":1700000000}"""), account.hexKey)
        assertEquals(LocalSignerProtocol.USER_DECLINED, guesser.reply().get("error").get("code").asInt())
    }

    @Test
    fun nip44v3RoundTripsWithPerKindPrompts() = runBlocking {
        val account = newAccount()
        val name = uniqueName()
        val c = connected(name, account)
        val plain = "hello v3"
        val plainB64 = java.util.Base64.getEncoder().encodeToString(plain.toByteArray())

        // The kind may be a JSON number; the scope travels as text.
        c.call("e", "nip44v3_encrypt", account.hexKey, com.fasterxml.jackson.databind.node.IntNode(1059), "dm", plainB64, account.hexKey)
        val encrypt = awaitPending(SignerType.NIP44_V3_ENCRYPT, name)
        assertEquals(1059, encrypt.kind)
        assertEquals("dm", encrypt.nip44v3Scope)
        assertEquals(plain, encrypt.preview)
        AmberDesktop.engine.approve(encrypt, RememberType.NEVER).join()
        val cipher = c.reply().get("result").asText()

        c.call("d", "nip44v3_decrypt", account.hexKey, "1059", "dm", cipher, account.hexKey)
        AmberDesktop.engine.approve(awaitPending(SignerType.NIP44_V3_DECRYPT, name), RememberType.NEVER).join()
        assertEquals(plainB64, c.reply().get("result").asText())

        // Without a usable kind the request is refused as invalid, with no prompt.
        c.call("bad", "nip44v3_encrypt", account.hexKey, "dm", "dm", plainB64, account.hexKey)
        assertEquals(LocalSignerProtocol.INVALID_PARAMS, c.reply().get("error").get("code").asInt())
    }

    @Test
    fun accountParamSelectsTheKeyAndUnknownKeysAreReported() = runBlocking {
        val a = newAccount()
        val b = newAccount()
        val name = uniqueName()
        val c = connected(name, a)

        // Not approved for b yet: a connect for exactly that account (no switching).
        c.call("pk-b", "get_public_key", b.npub)
        val connect = awaitPending(SignerType.CONNECT, name)
        assertEquals(b.npub, connect.account.npub)
        assertTrue(!connect.canSwitchAccount)
        AmberDesktop.engine.approve(connect, RememberType.NEVER).join()
        assertEquals(b.hexKey, c.reply().get("result").asText())

        c.call("pk-x", "get_public_key", KeyPair().pubKey.toHexKey())
        assertEquals(LocalSignerProtocol.KEY_NOT_FOUND, c.reply().get("error").get("code").asInt())
    }

    @Test
    fun droppedRequestsAreAnsweredInsteadOfHanging() = runBlocking {
        val account = newAccount()
        val name = uniqueName()
        val c = connected(name, account)
        c.call("s", "sign_event", JacksonMapper.mapper.readTree("""{"kind":1,"content":"x","tags":[],"created_at":1700000000}"""), account.hexKey)
        awaitPending(SignerType.SIGN_EVENT, name)
        // What PassphraseLock.lock() does to the queue.
        AmberDesktop.engine.dropPending("signer locked") { it.appName == name }
        assertEquals(LocalSignerProtocol.INTERNAL_ERROR, c.reply().get("error").get("code").asInt())
    }

    @Test
    fun overlappingRequestsAreAnsweredById() = runBlocking {
        val account = newAccount()
        val name = uniqueName()
        val c = connected(name, account)
        // The sign waits for approval; the ping behind it must not.
        c.call("slow", "sign_event", JacksonMapper.mapper.readTree("""{"kind":1,"content":"x","tags":[],"created_at":1700000000}"""), account.hexKey)
        awaitPending(SignerType.SIGN_EVENT, name)
        c.call("fast", "get_public_key", account.hexKey)
        assertEquals("fast", c.reply().get("id").asText())
        AmberDesktop.engine.approve(awaitPending(SignerType.SIGN_EVENT, name), RememberType.NEVER).join()
        assertEquals("slow", c.reply().get("id").asText())
    }

    @Test
    fun aClientThatHalfClosesStillGetsItsAnswers() = runBlocking {
        val account = newAccount()
        val name = uniqueName()
        val c = connected(name, account)
        // `echo request | nc -U ...`: write, then close the sending side.
        c.call("pk", "get_public_key", account.hexKey)
        c.call("bad", "no_such_method")
        c.channel.shutdownOutput()
        val replies = listOf(c.reply(), c.reply()).associate { it.get("id").asText() to it }
        assertEquals(account.hexKey, replies.getValue("pk").get("result").asText())
        assertEquals(LocalSignerProtocol.METHOD_NOT_SUPPORTED, replies.getValue("bad").get("error").get("code").asInt())
    }

    @Test
    fun aToolThatExitsTakesItsPromptsWithIt() = runBlocking {
        val account = newAccount()
        val name = uniqueName()
        val c = connected(name, account)
        val otherName = uniqueName()
        val other = connected(otherName, account)
        c.call("s", "sign_event", JacksonMapper.mapper.readTree("""{"kind":1,"content":"x","tags":[],"created_at":1700000000}"""), account.hexKey)
        other.call("o", "sign_event", JacksonMapper.mapper.readTree("""{"kind":1,"content":"y","tags":[],"created_at":1700000000}"""), account.hexKey)
        awaitPending(SignerType.SIGN_EVENT, name)
        val kept = awaitPending(SignerType.SIGN_EVENT, otherName)
        c.close()
        withTimeout(10_000) { AmberDesktop.engine.pending.first { list -> list.none { it.appName == name } } }
        assertTrue("other clients' prompts stay", kept in AmberDesktop.engine.pending.value)
        AmberDesktop.engine.approve(kept, RememberType.NEVER).join()
        assertEquals("o", other.reply().get("id").asText())
    }

    @Test
    fun simultaneousClientsAllGetTheirOwnPromptAndAnswer() = runBlocking {
        val account = newAccount()
        val names = List(8) { uniqueName() }
        val tools = names.map { connected(it, account) }
        val start = java.util.concurrent.CountDownLatch(1)
        val senders = tools.mapIndexed { i, c ->
            Thread {
                start.await()
                c.call("s$i", "sign_event", JacksonMapper.mapper.readTree("""{"kind":1,"content":"$i","tags":[],"created_at":1700000000}"""), account.hexKey)
            }.apply { start() }
        }
        start.countDown()
        senders.forEach { it.join() }
        // Approve in reverse order: each answer must still reach its own connection.
        names.indices.reversed().forEach { i ->
            AmberDesktop.engine.approve(awaitPending(SignerType.SIGN_EVENT, names[i]), RememberType.NEVER).join()
            val reply = tools[i].reply()
            assertEquals("s$i", reply.get("id").asText())
            assertEquals("$i", reply.get("result").get("content").asText())
        }
    }

    @Test
    fun aHalfClosedClientIsToldItsPromptWasDropped() = runBlocking {
        val account = newAccount()
        val name = uniqueName()
        val c = connected(name, account)
        c.call("s", "sign_event", JacksonMapper.mapper.readTree("""{"kind":1,"content":"x","tags":[],"created_at":1700000000}"""), account.hexKey)
        awaitPending(SignerType.SIGN_EVENT, name)
        c.channel.shutdownOutput()
        val reply = c.reply()
        assertEquals("s", reply.get("id").asText())
        assertEquals(LocalSignerProtocol.INTERNAL_ERROR, reply.get("error").get("code").asInt())
        assertTrue(AmberDesktop.engine.pending.value.none { it.appName == name })
    }

    @Test
    fun aStaleSocketIsReplacedButALiveOneIsLeftAlone() {
        LocalSigner.stop()
        val path = LocalSigner.socketPath()
        Files.createDirectories(path.parent)
        Files.deleteIfExists(path)
        Files.createFile(path)
        assertEquals(LocalSigner.Status.RUNNING, LocalSigner.start())
        LocalSigner.stop()

        ServerSocketChannel.open(StandardProtocolFamily.UNIX).use { other ->
            other.bind(UnixDomainSocketAddress.of(path))
            assertEquals(LocalSigner.Status.IN_USE, LocalSigner.start())
        }
        Files.deleteIfExists(path)
    }

    @Test
    fun getPublicKeyLogsInWithTheAccountPickedInTheApproval() = runBlocking {
        // Like NIP-55: an unknown tool asks for the key without naming an
        // account, and whichever account the user picks is the answer.
        val current = newAccount()
        val picked = newAccount()
        SettingsStore.update { it.copy(currentAccount = current.npub) }
        val name = uniqueName()
        val c = client(name)
        c.call("pk", "get_public_key")
        val connect = awaitPending(SignerType.CONNECT, name)
        assertEquals("the picker starts on the current account", current.npub, connect.account.npub)
        assertTrue(connect.canSwitchAccount)
        AmberDesktop.engine.approve(connect, RememberType.NEVER, accountNpub = picked.npub).join()
        assertEquals(picked.hexKey, c.reply().get("result").asText())

        // From then on the tool's requests default to that account.
        c.call("pk2", "get_public_key")
        assertEquals(picked.hexKey, c.reply().get("result").asText())
    }
}

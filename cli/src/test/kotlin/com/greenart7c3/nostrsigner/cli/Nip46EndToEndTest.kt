package com.greenart7c3.nostrsigner.cli

import com.greenart7c3.nostrsigner.common.relays.LocalRelayServer
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The whole CLI against a relay and a signer on this machine: no network, no
 * Amber. [FakeSigner] plays the signer half of NIP-46 the way Amber does (a
 * per-connection key, responses p-tagged to the client, NIP-44).
 */
class Nip46EndToEndTest {
    private companion object {
        const val SECRET = "s3cr3t-from-amber"
        const val TIMEOUT = "20"
    }

    private lateinit var relay: LocalRelayServer
    private lateinit var relayUrl: String
    private lateinit var stateDir: Path
    private lateinit var signer: FakeSigner

    private class Run(
        val exit: Int,
        val out: String,
        val err: String,
    )

    @Before
    fun setUp() {
        relay = LocalRelayServer(
            logger = object : LocalRelayServer.Logger {
                override fun d(message: String) = Unit

                override fun i(message: String) = Unit

                override fun e(
                    message: String,
                    throwable: Throwable?,
                ) = Unit
            },
            approvedSenders = { emptySet() },
            // Like public relays: an answer sent before the client subscribed is lost,
            // so a client that subscribes too late fails here instead of being rescued.
            storesEphemeral = false,
        )
        assertEquals(LocalRelayServer.Status.RUNNING, relay.start(0))
        relayUrl = relay.relayUrl()!!.url
        stateDir = Files.createTempDirectory("amber-cli-test")
        signer = FakeSigner(relayUrl, secret = SECRET)
    }

    @After
    fun tearDown() {
        signer.close()
        relay.stop()
        runCatching { stateDir.toFile().deleteRecursively() }
    }

    private fun amber(
        vararg arguments: String,
        stdin: String = "",
        onPairingUri: (String) -> Unit = {},
    ): Run {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val environment = CliEnvironment(
            out = PrintStream(out, true, "utf-8"),
            err = PrintStream(err, true, "utf-8"),
            stdin = ByteArrayInputStream(stdin.toByteArray(Charsets.UTF_8)),
            env = { null },
            stateDir = stateDir,
            secretStore = FileSecretStore(stateDir.resolve("connection.key")),
            openUri = {},
            onPairingUri = onPairingUri,
        )
        val exit = AmberCli.run(arguments.toList(), environment)
        return Run(exit, out.toString("utf-8"), err.toString("utf-8"))
    }

    private fun bunkerUri(secret: String = SECRET): String = "bunker://${signer.connectionPubKey}?relay=$relayUrl&secret=$secret"

    @Test
    fun loginThenSignEvent() {
        val login = amber("login", bunkerUri(), "--timeout", TIMEOUT)
        assertEquals(login.err, Exit.OK, login.exit)
        assertEquals(signer.accountPubKey, login.out.trim())

        val signed = amber(
            "sign-event",
            """{"kind":1,"content":"hi from the cli","tags":[["t","amber"]]}""",
            "--timeout",
            TIMEOUT,
        )
        assertEquals(signed.err, Exit.OK, signed.exit)
        val event = Event.fromJsonOrNull(signed.out.trim())!!
        assertTrue(event.verify())
        assertEquals(signer.accountPubKey, event.pubKey)
        assertEquals(1, event.kind)
        assertEquals("hi from the cli", event.content)
        assertTrue(event.tags.any { it.size == 2 && it[0] == "t" && it[1] == "amber" })
    }

    @Test
    fun loginReadsTheBunkerUriFromStdin() {
        val piped = amber("login", "-", "--timeout", TIMEOUT, stdin = bunkerUri() + "\n")
        assertEquals(piped.err, Exit.OK, piped.exit)
        assertEquals(signer.accountPubKey, piped.out.trim())
        val pubKey = amber("get-public-key", "--timeout", TIMEOUT)
        assertEquals(pubKey.err, Exit.OK, pubKey.exit)
        assertEquals(signer.accountPubKey, pubKey.out.trim())
        val fromStdin = pairing()

        // The argument form saves the same pairing (a new client key aside).
        assertEquals(Exit.OK, amber("login", bunkerUri(), "--force", "--timeout", TIMEOUT).exit)
        assertEquals(fromStdin, pairing())
    }

    @Test
    fun loginWithEmptyStdinIsAUsageError() {
        assertEquals(Exit.USAGE, amber("login", "-", stdin = "\n").exit)
    }

    /** What `status` reports about the saved pairing: signer, user and relays. */
    private fun pairing(): List<String> {
        val status = amber("status", "--json", "--timeout", TIMEOUT)
        assertEquals(status.err + status.out, Exit.OK, status.exit)
        val node = JacksonMapper.mapper.readTree(status.out.trim()).get("result")
        return listOf(node.get("signer").asText(), node.get("user").asText(), node.get("relays").toString())
    }

    @Test
    fun signEventReadsStdinAndFillsCreatedAt() {
        assertEquals(Exit.OK, amber("login", bunkerUri(), "--timeout", TIMEOUT).exit)
        val signed = amber("sign-event", "-", "--timeout", TIMEOUT, stdin = """{"kind":30023,"content":"from stdin","tags":[]}""")
        assertEquals(signed.err, Exit.OK, signed.exit)
        val event = Event.fromJsonOrNull(signed.out.trim())!!
        assertTrue(event.verify())
        assertEquals(30023, event.kind)
        assertEquals("from stdin", event.content)
        assertTrue(event.createdAt > 0)
    }

    @Test
    fun connectPairsByApprovalThenSigns() {
        val connect = amber(
            "connect",
            "--relay",
            relayUrl,
            "--name",
            "test cli",
            "--perms",
            "sign_event:1",
            "--timeout",
            TIMEOUT,
            onPairingUri = { uri -> signer.approve(uri) },
        )
        assertEquals(connect.err, Exit.OK, connect.exit)
        assertTrue(connect.out.startsWith("nostrconnect://"))

        val pubKey = amber("get-public-key", "--timeout", TIMEOUT)
        assertEquals(pubKey.err, Exit.OK, pubKey.exit)
        assertEquals(signer.accountPubKey, pubKey.out.trim())

        val signed = amber("sign-event", """{"kind":1,"content":"post-connect","tags":[]}""", "--timeout", TIMEOUT)
        assertEquals(signed.err, Exit.OK, signed.exit)
        assertEquals("post-connect", Event.fromJsonOrNull(signed.out.trim())!!.content)
    }

    @Test
    fun wrongSecretIsRefused() {
        val login = amber("login", bunkerUri(secret = "not-the-secret"), "--timeout", TIMEOUT)
        assertEquals(Exit.REJECTED, login.exit)
        assertTrue(login.err.contains("wrong secret"))
    }

    @Test
    fun refusedRequestExitsWithFour() {
        assertEquals(Exit.OK, amber("login", bunkerUri(), "--timeout", TIMEOUT).exit)
        signer.mode = FakeSigner.Mode.REFUSE
        val signed = amber("sign-event", """{"kind":1,"content":"no","tags":[]}""", "--timeout", TIMEOUT)
        assertEquals(Exit.REJECTED, signed.exit)
        assertTrue(signed.err.contains("user rejected"))
    }

    @Test
    fun silentSignerTimesOut() {
        assertEquals(Exit.OK, amber("login", bunkerUri(), "--timeout", TIMEOUT).exit)
        signer.mode = FakeSigner.Mode.SILENT
        val startedAt = System.nanoTime()
        val ping = amber("ping", "--timeout", "1")
        assertEquals(Exit.TIMEOUT, ping.exit)
        assertTrue((System.nanoTime() - startedAt) < 30_000_000_000L)
    }

    @Test
    fun unpairedProfileExitsWithThree() {
        val status = amber("status")
        assertEquals(Exit.NOT_PAIRED, status.exit)
    }

    @Test
    fun unreachableRelayExitsWithSix() {
        val port = ServerSocket(0).use { it.localPort }
        val login = amber(
            "login",
            "bunker://${signer.connectionPubKey}?relay=ws://127.0.0.1:$port/&secret=x",
            "--timeout",
            "1",
        )
        assertEquals(login.err, Exit.UNREACHABLE, login.exit)
    }

    @Test
    fun logoutDeletesTheProfile() {
        assertEquals(Exit.OK, amber("login", bunkerUri(), "--timeout", TIMEOUT).exit)
        val logout = amber("logout", "--timeout", TIMEOUT)
        assertEquals(logout.err, Exit.OK, logout.exit)
        assertEquals(Exit.NOT_PAIRED, amber("status").exit)
    }

    @Test
    fun jsonOutputCarriesTheResultAndTheErrorCode() {
        assertEquals(Exit.OK, amber("login", bunkerUri(), "--timeout", TIMEOUT).exit)
        val ok = amber("get-public-key", "--json", "--timeout", TIMEOUT)
        val okNode = JacksonMapper.mapper.readTree(ok.out.trim())
        assertEquals(true, okNode.get("ok").asBoolean())
        assertEquals(signer.accountPubKey, okNode.get("result").asText())

        val bad = amber("--json", "status", "--profile", "missing")
        val badNode = JacksonMapper.mapper.readTree(bad.out.trim())
        assertEquals(false, badNode.get("ok").asBoolean())
        assertEquals(Exit.NOT_PAIRED, badNode.get("code").asInt())
    }

    @Test
    fun pingAnswersPong() {
        assertEquals(Exit.OK, amber("login", bunkerUri(), "--timeout", TIMEOUT).exit)
        val ping = amber("ping", "--timeout", TIMEOUT)
        assertEquals(ping.err + "|" + ping.out, Exit.OK, ping.exit)
        assertEquals("pong", ping.out.trim())
        // Quartz's own DEBUG logging must not leak onto the CLI's stderr.
        assertTrue(!ping.err.contains("DEBUG"))
    }

    @Test
    fun statusReportsThePairingAndTheSigner() {
        assertEquals(Exit.OK, amber("login", bunkerUri(), "--timeout", TIMEOUT).exit)
        val status = amber("status", "--json", "--timeout", TIMEOUT)
        assertEquals(status.err + status.out, Exit.OK, status.exit)
        val node = JacksonMapper.mapper.readTree(status.out.trim()).get("result")
        assertEquals(signer.connectionPubKey, node.get("signer").asText())
        assertEquals(signer.accountPubKey, node.get("user").asText())
        assertEquals(true, node.get("online").asBoolean())
    }
}

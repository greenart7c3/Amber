package com.greenart7c3.nostrsigner.cli

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ArgsAndHelpTest {
    private lateinit var stateDir: java.nio.file.Path

    private val commands = listOf(
        "login",
        "connect",
        "sign-event",
        "get-public-key",
        "nip44-encrypt",
        "nip44-decrypt",
        "nip04-encrypt",
        "nip04-decrypt",
        "ping",
        "status",
        "logout",
    )

    @Before
    fun setUp() {
        stateDir = Files.createTempDirectory("amber-cli-args")
    }

    @After
    fun tearDown() {
        runCatching { stateDir.toFile().deleteRecursively() }
    }

    private fun run(vararg arguments: String): Triple<Int, String, String> {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val exit = AmberCli.run(
            arguments.toList(),
            CliEnvironment(
                out = PrintStream(out, true, "utf-8"),
                err = PrintStream(err, true, "utf-8"),
                stdin = ByteArrayInputStream(ByteArray(0)),
                env = { null },
                stateDir = stateDir,
                secretStore = FileSecretStore(stateDir.resolve("connection.key")),
                openUri = {},
            ),
        )
        return Triple(exit, out.toString("utf-8"), err.toString("utf-8"))
    }

    @Test
    fun helpCoversEveryCommand() {
        assertTrue(Help.overview().contains("sign-event"))
        for (command in commands) {
            val text = Help.forCommand(command)
            assertTrue("$command has no usage line", text.contains("amber $command"))
            assertTrue("$command does not document exit codes", text.contains("EXIT CODES"))
        }
        assertEquals(Help.overview(), Help.forCommand(null))
    }

    @Test
    fun everyCommandIsDispatched() {
        // A port nothing listens on keeps `connect` off the network.
        val closedPort = java.net.ServerSocket(0).use { it.localPort }
        for (command in commands) {
            // Without a saved connection each of them stops with "not paired",
            // a usage error for the arguments it needs, or (connect) an
            // unreachable relay: never with "unknown command".
            // Its own profile, so one command's saved pending pairing cannot
            // leak into the next one's "not paired" expectation.
            val arguments = if (command == "connect") {
                arrayOf(command, "--relay", "ws://127.0.0.1:$closedPort/", "--timeout", "1", "--profile", command)
            } else {
                arrayOf(command, "--profile", command)
            }
            val (exit, _, err) = run(*arguments)
            assertTrue("$command exited $exit: $err", exit in setOf(Exit.USAGE, Exit.NOT_PAIRED, Exit.UNREACHABLE))
            assertTrue("$command was not dispatched: $err", !err.contains("unknown command"))
        }
    }

    @Test
    fun withoutArgumentsPrintsTheHelp() {
        val (exit, out, _) = run()
        assertEquals(Exit.OK, exit)
        assertTrue(out.startsWith("amber "))
        assertTrue(out.contains("COMMANDS"))
    }

    @Test
    fun helpCommandPrintsTheCommandsHelp() {
        val (exit, out, _) = run("help", "sign-event")
        assertEquals(Exit.OK, exit)
        assertTrue(out.contains("amber sign-event"))
    }

    @Test
    fun unknownCommandAndOptionAreUsageErrors() {
        assertEquals(Exit.USAGE, run("frobnicate").first)
        assertEquals(Exit.USAGE, run("status", "--nonsense").first)
        assertEquals(Exit.USAGE, run("help", "frobnicate").first)
        assertEquals(Exit.USAGE, run("--profile").first)
    }

    @Test
    fun versionIsPrinted() {
        val (exit, out, _) = run("--version")
        assertEquals(Exit.OK, exit)
        assertTrue(out.startsWith("amber "))
    }

    @Test
    fun jsonModeWrapsErrors() {
        val (exit, out, err) = run("--json", "status")
        assertEquals(Exit.NOT_PAIRED, exit)
        val node = com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper.mapper.readTree(out.trim())
        assertEquals(false, node.get("ok").asBoolean())
        assertEquals(Exit.NOT_PAIRED, node.get("code").asInt())
        assertTrue(node.get("error").asText().isNotEmpty())
        assertTrue(err.isNotEmpty())
    }

    @Test
    fun flagsMayComeBeforeOrAfterTheCommand() {
        assertEquals(Exit.NOT_PAIRED, run("--profile", "work", "status").first)
        assertEquals(Exit.NOT_PAIRED, run("status", "--profile=work").first)
    }
}

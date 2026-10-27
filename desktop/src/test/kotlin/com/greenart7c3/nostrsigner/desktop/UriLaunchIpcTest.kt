package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.BuildVariant
import com.greenart7c3.nostrsigner.desktop.core.UriLaunch
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test

/**
 * The nostrconnect forward socket, end to end and without a GUI: a second
 * launch's [UriLaunch.forwardToRunningInstance] lands the URI in the running
 * instance's [UriLaunch.pending].
 *
 * The socket is owner-only and foreign connections are dropped (SO_PEERCRED);
 * the foreign-user drop itself needs a second OS account and is exercised on
 * a VM, not here.
 */
class UriLaunchIpcTest {
    private val runtimeDir = Files.createTempDirectory("amber-uri-ipc-test")
    private val socketPath: Path = Path.of(
        runtimeDir.toString(),
        "amber${BuildVariant.suffix}-nostrconnect.sock",
    )

    init {
        UriLaunch.runtimeDirOverride = runtimeDir.toString()
        UriLaunch.pending.value = null
    }

    @After
    fun tearDown() {
        UriLaunch.pending.value = null
        UriLaunch.runtimeDirOverride = null
        runtimeDir.toFile().deleteRecursively()
    }

    private fun await(
        timeoutMs: Long = 5_000,
        condition: () -> Boolean,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }

    @Test
    fun forwardedUriEndsUpPendingOnAnOwnerOnlySocket() {
        Assume.assumeTrue(UriLaunch.isLinux)

        UriLaunch.startIpcServer()
        assertTrue("the IPC socket never appeared", await { Files.exists(socketPath) })

        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            assertTrue(
                "the socket must be rw-------",
                await {
                    Files.getPosixFilePermissions(socketPath) ==
                        setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                },
            )
        }

        val uri = "nostrconnect://e2e?relay=wss%3A%2F%2Frelay.example.com"
        assertTrue("forwarding to the running instance failed", UriLaunch.forwardToRunningInstance(uri))
        assertTrue("the forwarded URI never reached pending", await { UriLaunch.pending.value == uri })

        // Coalescing: while a URI is uncollected, later forwards are ignored
        // instead of overwriting it (flood hardening on the socket).
        assertTrue(UriLaunch.forwardToRunningInstance("nostrconnect://second"))
        assertFalse("a forwarded URI overwrote the uncollected one", await(600) { UriLaunch.pending.value != uri })
        assertEquals(uri, UriLaunch.pending.value)
    }

    @Test
    fun nonNostrconnectPayloadsAreIgnored() {
        Assume.assumeTrue(UriLaunch.isLinux)

        UriLaunch.startIpcServer()
        assertTrue(await { Files.exists(socketPath) })

        assertTrue(UriLaunch.forwardToRunningInstance("not-a-nostrconnect-uri"))
        assertFalse("garbage was published as pending", await(600) { UriLaunch.pending.value != null })
    }
}

package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.isSameUserPeer
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import jdk.net.ExtendedSocketOptions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The local sockets (NIP-5F signer, URI hand-off) accept only peers running
 * as the user that owns the socket. Where peer credentials exist, a check
 * that cannot be completed must refuse the peer, not let it in.
 */
class PeerCredentialsTest {
    private fun <T> withConnectedPair(block: (accepted: SocketChannel, path: java.nio.file.Path) -> T): T {
        val dir = Files.createTempDirectory("amber-peer")
        val path = dir.resolve("t.sock")
        ServerSocketChannel.open(StandardProtocolFamily.UNIX).use { server ->
            server.bind(UnixDomainSocketAddress.of(path))
            SocketChannel.open(UnixDomainSocketAddress.of(path)).use {
                server.accept().use { accepted -> return block(accepted, path) }
            }
        }
    }

    @Test
    fun aPeerOfTheSameUserIsAccepted() = withConnectedPair { accepted, path ->
        assumeTrue(ExtendedSocketOptions.SO_PEERCRED in accepted.supportedOptions())
        assertTrue(isSameUserPeer(accepted, path))
    }

    @Test
    fun `a check that cannot complete, as the socket owner can no longer be read, refuses the peer`() = withConnectedPair { accepted, path ->
        assumeTrue(ExtendedSocketOptions.SO_PEERCRED in accepted.supportedOptions())
        Files.delete(path)
        assertFalse(isSameUserPeer(accepted, path))
    }
}

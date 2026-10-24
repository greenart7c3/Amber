package com.greenart7c3.nostrsigner.desktop.core

import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import jdk.net.ExtendedSocketOptions

/**
 * Whether the peer of a Unix-domain socket runs as the same OS user that owns
 * [socketPath] (the socket Amber created). Peer credentials exist on Linux and
 * macOS, and there any failure to read or compare them refuses the peer.
 * Windows has none for AF_UNIX; the owner-only socket under the user's
 * profile is the guard there, so a socket without the option is accepted.
 */
internal fun isSameUserPeer(client: SocketChannel, socketPath: Path): Boolean {
    if (ExtendedSocketOptions.SO_PEERCRED !in client.supportedOptions()) return true
    return runCatching {
        client.getOption(ExtendedSocketOptions.SO_PEERCRED).user().name == Files.getOwner(socketPath).name
    }.getOrDefault(false)
}

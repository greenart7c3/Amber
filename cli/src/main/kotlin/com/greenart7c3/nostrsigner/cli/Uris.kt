package com.greenart7c3.nostrsigner.cli

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import java.security.SecureRandom

private val random = SecureRandom()

/** A random secret binding a nostrconnect pairing to this connection. */
fun randomSecret(): String = ByteArray(16).apply { random.nextBytes(this) }.joinToString("") { "%02x".format(it) }

/**
 * The `nostrconnect://` URI a client shows to Amber. Quartz builds and encodes
 * it (and parses it back), so client and signer agree on how a name with `+`,
 * `&` or a space survives the trip.
 */
fun buildNostrConnectUri(
    clientPubKey: String,
    relays: List<String>,
    secret: String,
    name: String,
    perms: String,
): String = NostrConnectURI.buildNostrConnect(
    clientPubKey = clientPubKey,
    relays = relays.map { RelayUrlNormalizer.normalize(it) },
    secret = secret,
    perms = perms.ifBlank { null },
    name = name.ifBlank { null },
)

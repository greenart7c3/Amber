package com.greenart7c3.nostrsigner.cli

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.TextNode
import com.greenart7c3.nostrsigner.common.relays.LocalRelayServer
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils
import java.io.InputStream
import java.io.PrintStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Path

/**
 * Where the CLI reads its input and writes its output. Tests swap the streams,
 * the state directory and the OS-URL hook; the real run uses the process's.
 */
class CliEnvironment(
    val out: PrintStream = System.out,
    val err: PrintStream = System.err,
    val stdin: InputStream = System.`in`,
    val env: (String) -> String? = { System.getenv(it) },
    val stateDir: Path = ConnectionStore.defaultDirectory(),
    /** Where the connections file's key lives; the OS credential store by default. */
    val secretStore: SecretStore = SecretStore.default(stateDir, env),
    val openUri: (String) -> Unit = ::openInBrowser,
    /** Called with the nostrconnect URI right after it is printed (tests pair from here). */
    val onPairingUri: (String) -> Unit = {},
)

/**
 * The same four relays Amber itself uses for new connections: `defaultAppRelays`
 * in the Android app (`app/models/AmberSettings.kt`) and `DesktopSettings.defaultRelays`
 * in the desktop app. Keep them in step.
 */
private val DEFAULT_RELAYS = listOf(
    "wss://auth.nostr1.com/",
    "wss://bucket.coracle.social/",
    "wss://nrs.primal.net/",
    "wss://relay.nip46.com/",
)

/** The release local relay, and the debug build's one, when a running Amber listens there. */
private val LOCAL_RELAY_PORTS = listOf(LocalRelayServer.DEFAULT_PORT, LocalRelayServer.DEBUG_PORT)

private val HEX_PUB_KEY = Regex("^[0-9a-f]{64}$")

/**
 * Entry point: `amber <command> [arguments] [flags]`, exit code as documented
 * in `amber help`.
 */
object AmberCli {
    fun run(
        arguments: List<String>,
        environment: CliEnvironment = CliEnvironment(),
    ): Int {
        // Also for the parse errors below, which happen before Args exists.
        val json = arguments.any { it == "--json" || it.startsWith("--json=") }
        val args = try {
            Args.parse(arguments)
        } catch (e: CliError) {
            return fail(environment, json, e)
        }
        return Session(environment).run(args)
    }
}

internal class Session(
    private val environment: CliEnvironment,
) {
    private val out = environment.out
    private val err = environment.err
    private val mapper = JacksonMapper.mapper
    private val store = ConnectionStore(environment.stateDir, environment.secretStore)

    fun run(args: Args): Int {
        QuartzLogs.install(err, args.has("verbose"))
        if (args.has("version")) {
            out.println("amber ${BuildInfo.version}")
            return Exit.OK
        }
        val command = args.command
        if (command == null) {
            out.println(Help.overview())
            return Exit.OK
        }
        return try {
            if (command == "help" || args.has("help")) {
                out.println(Help.forCommand(if (command == "help") args.positionals.firstOrNull() else command))
                return Exit.OK
            }
            when (command) {
                "login" -> login(args)
                "connect" -> connect(args)
                "sign-event" -> signEvent(args)
                "get-public-key" -> getPublicKey(args)
                "nip44-encrypt" -> crypto(args, encrypt = true, nip44 = true)
                "nip44-decrypt" -> crypto(args, encrypt = false, nip44 = true)
                "nip04-encrypt" -> crypto(args, encrypt = true, nip44 = false)
                "nip04-decrypt" -> crypto(args, encrypt = false, nip44 = false)
                "ping" -> ping(args)
                "status" -> status(args)
                "logout" -> logout(args)
                else -> throw CliError("unknown command \"$command\" (run `amber help`)", Exit.USAGE)
            }
        } catch (e: CliError) {
            fail(environment, args.has("json"), e)
        } catch (e: Exception) {
            fail(environment, args.has("json"), CliError("$command failed: ${e.message ?: e.toString()}"))
        }
    }

    // ---- commands ---------------------------------------------------------

    /**
     * Pairing from a `bunker://` URI Amber created for a new connection. Amber
     * asks the user to approve a first-time client; after that the connection
     * is saved here and every command goes straight through it.
     */
    private fun login(args: Args): Int {
        val missing = CliError(
            "login needs the bunker:// URI Amber shows for a connection: " +
                "amber login 'bunker://<pubkey>?relay=wss://...&secret=...', or - to read it from stdin",
            Exit.USAGE,
        )
        val input = args.positionals.firstOrNull() ?: throw missing
        // "-" keeps the URI's secret out of the process list and the shell history.
        val uri = if (input == "-") readInput().trim().ifEmpty { throw missing } else input
        val profile = profile(args)
        val existing = store.get(profile)
        if (existing != null && existing.isPaired && !args.has("force")) {
            throw CliError(
                "profile \"$profile\" is already paired. Use `amber logout` to drop it, " +
                    "--force to replace it, or --profile NAME to keep both.",
            )
        }
        val seconds = timeout(args)
        val keyPair = KeyPair()
        // The relays come from the URI, which Quartz parses: none are needed up front.
        val login = Nip46Client(keyPair, emptyList(), seconds, err, environment.openUri).use { client ->
            client.login(uri)
        }
        val connection = SavedConnection(
            clientPrivKey = keyPair.privKey!!.toHexKey(),
            signerPubKey = login.signerPubKey,
            userPubKey = login.userPubKey,
            relays = login.relays,
            name = args.value("name") ?: "amber-cli",
            pairedAt = TimeUtils.now(),
        )
        store.put(profile, connection)
        err.println(
            "paired profile \"$profile\": signs as ${shortKey(connection.userPubKey)} " +
                "through ${shortKey(connection.signerPubKey)}",
        )
        emit(args, TextNode(connection.userPubKey), connection.userPubKey)
        return Exit.OK
    }

    /**
     * Pairing by URI: prints a `nostrconnect://` link (and QR) that a human
     * pastes or scans into Amber, then waits for the approval. The connection
     * is saved as soon as it is printed, so a timed-out pairing resumes with
     * the same URI instead of stranding the key and secret.
     */
    private fun connect(args: Args): Int {
        val profile = profile(args)
        val existing = store.get(profile)
        if (existing != null && existing.isPaired && !args.has("force")) {
            throw CliError(
                "profile \"$profile\" is already paired. Use `amber logout` to drop it, " +
                    "--force to replace it, or --profile NAME to keep both.",
            )
        }
        val seconds = timeout(args)
        val requestedRelays = args.values("relay").takeIf { it.isNotEmpty() }?.let { normalizeRelays(it) }
        val resumable = existing?.takeIf { pending ->
            !pending.isPaired &&
                pending.pendingSecret.isNotEmpty() &&
                (requestedRelays == null || pending.relays == requestedRelays.map { it.url })
        }
        val relays = requestedRelays ?: resumable?.relays?.let { normalizeRelays(it) } ?: defaultRelays()
        val keyPair = resumable?.let { clientKeyPair(it.clientPrivKey) } ?: KeyPair()
        val secret = resumable?.pendingSecret?.takeIf { it.isNotEmpty() } ?: randomSecret()
        val name = args.value("name") ?: resumable?.name?.takeIf { it.isNotBlank() } ?: "amber-cli"
        val perms = args.value("perms").orEmpty()
        val uri = buildNostrConnectUri(keyPair.pubKey.toHexKey(), relays.map { it.url }, secret, name, perms)
        val clientPrivKey = keyPair.privKey!!.toHexKey()
        store.put(
            profile,
            SavedConnection(
                clientPrivKey = clientPrivKey,
                relays = relays.map { it.url },
                name = name,
                pendingSecret = secret,
            ),
        )
        val paired = Nip46Client(keyPair, relays, seconds, err, environment.openUri).use { client ->
            // The URI leaves the process only once the relays hold the subscription
            // for the answer: they do not replay an approval that came before it.
            val signerPubKey = client.awaitNostrConnectApproval(secret) {
                emit(
                    args,
                    mapper.createObjectNode().put("uri", uri).put("state", "waiting-for-approval"),
                    uri,
                )
                if (args.has("qr")) printQrCode(err, uri)
                err.println("Waiting up to ${seconds}s for Amber to approve the connection \"$name\".")
                err.println("In Amber: paste the URI into \"New connection\" (or scan the QR), then approve it.")
                if (args.has("open")) environment.openUri(uri)
                environment.onPairingUri(uri)
            }
            client.open(signerPubKey, userPubKey = "")
            SavedConnection(
                clientPrivKey = clientPrivKey,
                signerPubKey = client.signerPubKey().orEmpty(),
                userPubKey = client.getPublicKey(),
                relays = relays.map { it.url },
                name = name,
                pairedAt = TimeUtils.now(),
            )
        }
        if (paired.signerPubKey.isEmpty()) throw CliError("Amber approved without naming a connection key", Exit.ERROR)
        store.put(profile, paired)
        err.println(
            "paired profile \"$profile\": signs as ${shortKey(paired.userPubKey)} " +
                "through ${shortKey(paired.signerPubKey)}",
        )
        if (args.has("json")) {
            emit(
                args,
                mapper.createObjectNode()
                    .put("uri", uri)
                    .put("state", "paired")
                    .put("profile", profile)
                    .put("signer", paired.signerPubKey)
                    .put("user", paired.userPubKey),
                uri,
            )
        }
        return Exit.OK
    }

    private fun signEvent(args: Args): Int {
        val connection = requireConnection(profile(args))
        val input = args.positionals.firstOrNull()
            ?: throw CliError(
                "sign-event needs the unsigned event JSON, or - to read it from stdin: " +
                    "amber sign-event '{\"kind\":1,\"content\":\"hello\",\"tags\":[]}'",
                Exit.USAGE,
            )
        val template = eventTemplate(if (input == "-") readInput() else input)
        val event = session(connection, timeout(args)).use { it.signEvent(template) }
        if (!event.verify()) {
            throw CliError("Amber returned an event whose id or signature does not verify", Exit.ERROR)
        }
        if (event.pubKey != connection.userPubKey) {
            throw CliError(
                "Amber signed as ${event.pubKey}, not as the paired account ${connection.userPubKey}. " +
                    "Signing with another account is not supported; pair a connection to that account.",
                Exit.ERROR,
            )
        }
        val json = event.toJson()
        emit(args, runCatching { mapper.readTree(json) }.getOrNull() ?: TextNode(json), json)
        return Exit.OK
    }

    private fun getPublicKey(args: Args): Int {
        val connection = requireConnection(profile(args))
        val pubKey = session(connection, timeout(args)).use { it.getPublicKey() }
        if (pubKey != connection.userPubKey) {
            throw CliError("Amber now signs as $pubKey, not as the paired ${connection.userPubKey}", Exit.ERROR)
        }
        emit(args, TextNode(pubKey), pubKey)
        return Exit.OK
    }

    private fun crypto(
        args: Args,
        encrypt: Boolean,
        nip44: Boolean,
    ): Int {
        val command = "${if (nip44) "nip44" else "nip04"}-${if (encrypt) "encrypt" else "decrypt"}"
        val pubKey = args.positionals.firstOrNull()?.lowercase()
            ?: throw CliError("$command needs the other party's hex public key", Exit.USAGE)
        if (!HEX_PUB_KEY.matches(pubKey)) {
            throw CliError("\"$pubKey\" is not a 64-character hex public key", Exit.USAGE)
        }
        val connection = requireConnection(profile(args))
        val input = args.positionals.getOrNull(1)
            ?: throw CliError("$command needs the ${if (encrypt) "plaintext" else "ciphertext"}, or - to read it from stdin", Exit.USAGE)
        val text = if (input == "-") readInput() else input
        val result = session(connection, timeout(args)).use { client ->
            when {
                nip44 && encrypt -> client.nip44Encrypt(pubKey, text)
                nip44 -> client.nip44Decrypt(pubKey, text)
                encrypt -> client.nip04Encrypt(pubKey, text)
                else -> client.nip04Decrypt(pubKey, text)
            }
        }
        emit(args, TextNode(result), result)
        return Exit.OK
    }

    private fun ping(args: Args): Int {
        val connection = requireConnection(profile(args))
        session(connection, timeout(args)).use { it.ping() }
        emit(args, TextNode("pong"), "pong")
        return Exit.OK
    }

    /**
     * What is saved and whether the signer answers right now: the first thing
     * an agent should run before a batch of requests.
     */
    private fun status(args: Args): Int {
        val profile = profile(args)
        val connection = store.get(profile)
            ?: throw CliError(
                "profile \"$profile\" is not paired yet: run `amber login <bunker://...>` or `amber connect`",
                Exit.NOT_PAIRED,
            )
        if (!connection.isPaired) {
            val waiting = CliError(
                "profile \"$profile\" is waiting for Amber to approve its pairing: run `amber connect` to show the URI again",
                Exit.NOT_PAIRED,
            )
            err.println(waiting.message)
            emit(
                args,
                mapper.createObjectNode()
                    .put("profile", profile)
                    .put("name", connection.name)
                    .put("state", "waiting-for-approval"),
                "state: waiting-for-approval",
                waiting,
            )
            return waiting.code
        }
        // Kept whole: the exit code says why it is offline (5 silent, 6 unreachable, 4 refused).
        val failure = try {
            session(connection, timeout(args, defaultSeconds = 15)).use { it.ping() }
            null
        } catch (e: CliError) {
            e
        }
        val online = failure == null
        val node = mapper.createObjectNode()
            .put("profile", profile)
            .put("name", connection.name)
            .put("signer", connection.signerPubKey)
            .put("user", connection.userPubKey)
            .put("online", online)
            .put("state", if (online) "online" else "offline")
        val relays = node.putArray("relays")
        connection.relays.forEach { relays.add(it) }
        emit(
            args,
            node,
            buildString {
                appendLine("profile: $profile")
                appendLine("name: ${connection.name}")
                appendLine("signer: ${connection.signerPubKey}")
                appendLine("user: ${connection.userPubKey}")
                appendLine("relays: ${connection.relays.joinToString(", ")}")
                append("state: " + if (online) "online" else "offline")
            },
            failure,
        )
        failure?.let { err.println(it.message) }
        return failure?.code ?: Exit.OK
    }

    /** Tells Amber to forget the connection and deletes the local profile. */
    private fun logout(args: Args): Int {
        val profile = profile(args)
        val connection = store.get(profile)
            ?: throw CliError("profile \"$profile\" is not paired, nothing to log out of", Exit.NOT_PAIRED)
        if (connection.isPaired) {
            try {
                session(connection, timeout(args)).use { it.logout() }
            } catch (e: CliError) {
                err.println("warning: ${e.message}")
                err.println("deleted profile \"$profile\" here anyway; also delete the connection in Amber")
            }
        }
        store.remove(profile)
        emit(args, TextNode("logged out"), "logged out profile \"$profile\"")
        return Exit.OK
    }

    // ---- helpers ----------------------------------------------------------

    private fun profile(args: Args): String = args.value("profile")?.takeIf { it.isNotBlank() }
        ?: environment.env("AMBER_PROFILE")?.takeIf { it.isNotBlank() }
        ?: "default"

    private fun timeout(
        args: Args,
        defaultSeconds: Long = 120,
    ): Long {
        val raw = args.value("timeout") ?: return defaultSeconds
        return raw.toLongOrNull()?.takeIf { it in 1..86_400 }
            ?: throw CliError("--timeout must be a number of seconds between 1 and 86400", Exit.USAGE)
    }

    private fun requireConnection(profile: String): SavedConnection {
        val connection = store.get(profile)
            ?: throw CliError(
                "profile \"$profile\" is not paired yet: run `amber login <bunker://...>` or `amber connect`",
                Exit.NOT_PAIRED,
            )
        if (!connection.isPaired) {
            throw CliError(
                "profile \"$profile\" is still waiting for Amber to approve its pairing: run `amber connect` to show the URI again",
                Exit.NOT_PAIRED,
            )
        }
        return connection
    }

    private fun session(
        connection: SavedConnection,
        seconds: Long,
    ): Nip46Client {
        val client = Nip46Client(
            clientKey = clientKeyPair(connection.clientPrivKey),
            relays = normalizeRelays(connection.relays),
            timeoutSeconds = seconds,
            err = err,
            openUri = environment.openUri,
        )
        client.open(connection.signerPubKey, connection.userPubKey)
        return client
    }

    private fun clientKeyPair(privKey: String): KeyPair = try {
        KeyPair(privKey = privKey.hexToByteArray())
    } catch (_: Exception) {
        throw CliError("the saved connection key is unusable: run `amber connect --force` to pair again")
    }

    private fun normalizeRelays(urls: List<String>): List<NormalizedRelayUrl> = urls.map { url ->
        RelayUrlNormalizer.normalizeOrNull(url.trim())
            ?: throw CliError("\"$url\" is not a relay URL (use wss://... or ws://...)", Exit.USAGE)
    }.distinct()

    /**
     * The relays a new nostrconnect URI names: Amber's own defaults, plus the
     * local relay of a running Amber on this machine so pairing works without
     * any public relay at all.
     */
    private fun defaultRelays(): List<NormalizedRelayUrl> {
        val local = LOCAL_RELAY_PORTS.filter { isListening(it) }.map { "ws://127.0.0.1:$it/" }
        return normalizeRelays(local + DEFAULT_RELAYS)
    }

    private fun isListening(port: Int): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), 250)
            true
        }
    }.getOrDefault(false)

    /** The unsigned event as Amber's `sign_event` expects it: created_at filled in when absent. */
    private fun eventTemplate(text: String): EventTemplate<Event> {
        val node = runCatching { mapper.readTree(text) }.getOrNull()
        if (node == null || !node.isObject) throw CliError("the event must be a JSON object", Exit.USAGE)
        val kind = node.get("kind")
        if (kind == null || !kind.canConvertToInt()) throw CliError("the event needs a numeric \"kind\"", Exit.USAGE)
        val contentNode = node.get("content")
        val content = when {
            contentNode == null || contentNode.isNull -> ""
            contentNode.isTextual -> contentNode.asText()
            else -> throw CliError("the event's \"content\" must be a string", Exit.USAGE)
        }
        val createdAt = node.get("created_at")?.takeIf { it.canConvertToLong() }?.asLong()?.takeIf { it > 0 } ?: TimeUtils.now()
        return EventTemplate(createdAt, kind.asInt(), readTags(node.get("tags")), content)
    }

    private fun readTags(node: JsonNode?): Array<Array<String>> {
        if (node == null || node.isNull) return emptyArray()
        if (!node.isArray) throw CliError("the event's \"tags\" must be an array of string arrays", Exit.USAGE)
        val tags = mutableListOf<Array<String>>()
        node.forEach { tag ->
            if (!tag.isArray) throw CliError("every tag must be an array of strings", Exit.USAGE)
            val values = mutableListOf<String>()
            tag.forEach { value ->
                if (!value.isTextual) throw CliError("every tag element must be a string", Exit.USAGE)
                values += value.asText()
            }
            tags += values.toTypedArray()
        }
        return tags.toTypedArray()
    }

    /** stdin, minus the newline a shell (`echo`) adds. */
    private fun readInput(): String = environment.stdin.readBytes()
        .toString(Charsets.UTF_8)
        .removeSuffix("\r\n")
        .removeSuffix("\n")

    /** The command's result on stdout: the plain value, or one `{"ok":…}` object with --json. */
    /**
     * With [error], the command still has a [result] to show but exits with the
     * error's code: the JSON then says `"ok":false` and carries `error` and
     * `code` beside the result, like the failure envelope.
     */
    private fun emit(
        args: Args,
        result: JsonNode,
        text: String,
        error: CliError? = null,
    ) {
        if (!args.has("json")) {
            out.println(text)
            return
        }
        val node = mapper.createObjectNode()
        node.put("ok", error == null)
        node.set<JsonNode>("result", result)
        if (error != null) {
            node.put("error", error.message)
            node.put("code", error.code)
        }
        out.println(mapper.writeValueAsString(node))
    }

    private fun shortKey(hex: String): String = if (hex.length <= 14) hex else hex.take(8) + "\u2026" + hex.takeLast(4)
}

private fun fail(
    environment: CliEnvironment,
    json: Boolean,
    error: CliError,
): Int {
    environment.err.println("amber: ${error.message}")
    if (json) {
        val node = JacksonMapper.mapper.createObjectNode()
        node.put("ok", false)
        node.put("error", error.message)
        node.put("code", error.code)
        environment.out.println(JacksonMapper.mapper.writeValueAsString(node))
    }
    return error.code
}

/** Hands a URI to the OS handler (`--open`), best effort. */
fun openInBrowser(uri: String) {
    val os = System.getProperty("os.name").lowercase()
    val command = when {
        os.contains("win") -> listOf("rundll32", "url.dll,FileProtocolHandler", uri)
        os.contains("mac") -> listOf("open", uri)
        else -> listOf("xdg-open", uri)
    }
    runCatching {
        ProcessBuilder(command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectErrorStream(true)
            .start()
    }
}

package com.greenart7c3.nostrsigner.desktop.core

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest

/**
 * Wire format of the NIP-5F local signer (nostr-protocol/nips#1862): one
 * JSON object per line. The signer greets with
 * `{"name", "supported_methods"}`, the client answers `{"client": name}`
 * (plus the `secret` it was given when approved), then sends `{id, method, params}` and
 * gets `{id, result, error}` back.
 *
 * Amber follows NIP-55 rather than the draft's `list_public_keys`: a tool
 * learns its key from `get_public_key`, answered with the account the user
 * picks when approving it, and may name an account (hex or npub) as an
 * optional trailing param on any method. It also adds the NIP-04/NIP-44 and
 * zap methods and NIP-44 v3. Like NIP-55, `get_public_key` is the only call that gets a
 * new tool approved; anything else from an unknown tool is refused.
 */
object LocalSignerProtocol {
    const val INVALID_REQUEST = 1
    const val METHOD_NOT_SUPPORTED = 2
    const val INVALID_PARAMS = 3
    const val KEY_NOT_FOUND = 4
    const val USER_DECLINED = 5
    const val INTERNAL_ERROR = 10

    /** Longest line accepted from a client. */
    const val MAX_LINE_BYTES = 1 shl 20

    /** Error text the engine answers a rejection with (see BunkerEngine.doReject). */
    const val USER_REJECTED = "user rejected"

    /** Method -> (type, number of params before the optional account). */
    private val methods: Map<String, Pair<SignerType, Int>> = linkedMapOf(
        "get_public_key" to (SignerType.GET_PUBLIC_KEY to 0),
        "sign_event" to (SignerType.SIGN_EVENT to 1),
        "nip04_encrypt" to (SignerType.NIP04_ENCRYPT to 2),
        "nip04_decrypt" to (SignerType.NIP04_DECRYPT to 2),
        "nip44_encrypt" to (SignerType.NIP44_ENCRYPT to 2),
        "nip44_decrypt" to (SignerType.NIP44_DECRYPT to 2),
        // NIP-44 v3, NIP-46 layout: [pubkey, kind, scope, base64 plaintext | ciphertext].
        "nip44v3_encrypt" to (SignerType.NIP44_V3_ENCRYPT to 4),
        "nip44v3_decrypt" to (SignerType.NIP44_V3_DECRYPT to 4),
        "decrypt_zap_event" to (SignerType.DECRYPT_ZAP_EVENT to 1),
        "ping" to (SignerType.PING to 0),
    )

    val supportedMethods: List<String> = methods.keys.toList()

    class RpcException(val code: Int, message: String) : Exception(message)

    data class Rpc(val id: String, val method: String, val params: List<JsonNode>)

    data class ClientHello(val name: String, val secret: String?)

    /** A request mapped onto the NIP-46 shape the engine already understands. */
    data class Call(val type: SignerType, val method: String, val params: List<String>, val account: String?)

    private val mapper get() = JacksonMapper.mapper
    private val nodes = JsonNodeFactory.instance

    fun handshake(): String = mapper.writeValueAsString(
        nodes.objectNode().apply {
            put("name", BuildVariant.appName)
            set<JsonNode>("supported_methods", nodes.arrayNode().apply { supportedMethods.forEach { add(it) } })
        },
    )

    private fun readObject(line: String): ObjectNode {
        val node = try {
            mapper.readTree(line)
        } catch (e: Exception) {
            throw RpcException(INVALID_REQUEST, "invalid JSON")
        }
        return node as? ObjectNode ?: throw RpcException(INVALID_REQUEST, "expected a JSON object")
    }

    /** The client's reply to [handshake], or null when it skipped straight to a request. */
    fun parseHello(line: String): ClientHello? {
        val node = readObject(line)
        if (node.has("method")) return null
        val name = node.get("client")?.takeIf { it.isTextual }?.asText()?.trim().orEmpty()
        val secret = node.get("secret")?.takeIf { it.isTextual }?.asText()?.takeIf { it.isNotBlank() }
        return ClientHello(name, secret)
    }

    fun parseRequest(line: String): Rpc {
        val node = readObject(line)
        val idNode = node.get("id")
        val id = when {
            idNode == null || idNode.isNull -> throw RpcException(INVALID_REQUEST, "missing id")
            idNode.isTextual || idNode.isNumber -> idNode.asText()
            else -> throw RpcException(INVALID_REQUEST, "invalid id")
        }
        val method = node.get("method")?.takeIf { it.isTextual }?.asText()
            ?: throw RpcException(INVALID_REQUEST, "missing method")
        val paramsNode = node.get("params")
        val params = when {
            paramsNode == null || paramsNode.isNull -> emptyList()
            paramsNode.isArray -> paramsNode.toList()
            else -> throw RpcException(INVALID_PARAMS, "params must be an array")
        }
        return Rpc(id, method, params)
    }

    /** Best-effort id for error replies to a line [parseRequest] rejected. */
    fun idOf(line: String): String = runCatching { mapper.readTree(line)?.get("id")?.asText() }.getOrNull().orEmpty()

    fun toCall(rpc: Rpc): Call {
        val (type, arity) = methods[rpc.method] ?: throw RpcException(METHOD_NOT_SUPPORTED, "method not supported: ${rpc.method}")
        if (rpc.params.size < arity || rpc.params.size > arity + 1) {
            throw RpcException(INVALID_PARAMS, "${rpc.method} takes $arity param(s) plus an optional account")
        }
        val params = rpc.params.take(arity).map { param ->
            when {
                param.isTextual -> param.asText()
                // The v3 context kind may come as a JSON number.
                param.isIntegralNumber && type in nip44v3SignerTypes -> param.asText()
                // Events travel as objects here and as JSON strings in NIP-46.
                param.isObject && type in eventParamTypes -> mapper.writeValueAsString(param)
                else -> throw RpcException(INVALID_PARAMS, "invalid params for ${rpc.method}")
            }
        }
        val account = rpc.params.getOrNull(arity)?.let { node ->
            if (!node.isTextual || node.asText().isBlank()) throw RpcException(INVALID_PARAMS, "account must be a hex pubkey or npub")
            node.asText().trim()
        }
        return Call(type, rpc.method, params, account)
    }

    private val eventParamTypes = setOf(SignerType.SIGN_EVENT, SignerType.DECRYPT_ZAP_EVENT)

    /** The account [value] (hex pubkey or npub) refers to, as an npub; null when it is neither. */
    fun npubOf(value: String): String? = runCatching {
        when {
            value.startsWith("npub1") -> value.bechToBytes().toNpub()
            value.length == 64 -> value.hexToByteArray().toNpub()
            else -> null
        }
    }.getOrNull()

    /** The call as a NIP-46 request (`sign_event` gets its typed subclass), under [internalId]. */
    fun bunkerRequest(internalId: String, call: Call): BunkerRequest {
        val json = nodes.objectNode().apply {
            put("id", internalId)
            put("method", call.method)
            set<JsonNode>("params", nodes.arrayNode().apply { call.params.forEach { add(it) } })
        }
        return try {
            mapper.readValue(mapper.writeValueAsString(json), BunkerRequest::class.java)
        } catch (e: Exception) {
            throw RpcException(INVALID_PARAMS, "invalid params for ${call.method}")
        }
    }

    /** Signed events (and decrypted zaps) go back as objects, everything else as a string. */
    fun resultNode(type: SignerType, result: String): JsonNode = if (type in eventParamTypes) {
        runCatching { mapper.readTree(result) }.getOrNull()?.takeIf { it.isObject } ?: nodes.textNode(result)
    } else {
        nodes.textNode(result)
    }

    /**
     * [secret] is set only on the `get_public_key` answer that approved the
     * tool: the tool keeps it and sends it in later hellos to prove it is the
     * approved app. It sits beside `result`, which stays the plain pubkey.
     */
    fun success(id: String, result: JsonNode, secret: String? = null): String = mapper.writeValueAsString(
        nodes.objectNode().apply {
            put("id", id)
            set<JsonNode>("result", result)
            if (secret != null) put("secret", secret)
            putNull("error")
        },
    )

    fun failure(id: String, code: Int, message: String): String = mapper.writeValueAsString(
        nodes.objectNode().apply {
            put("id", id)
            putNull("result")
            set<JsonNode>(
                "error",
                nodes.objectNode().apply {
                    put("code", code)
                    put("message", message)
                },
            )
        },
    )

    /** Code for an engine-side error answer (rejection, lock, expiry, ...). */
    fun codeFor(engineError: String): Int = if (engineError == USER_REJECTED) USER_DECLINED else INTERNAL_ERROR
}

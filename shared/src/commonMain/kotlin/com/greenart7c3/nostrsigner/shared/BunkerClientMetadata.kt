package com.greenart7c3.nostrsigner.shared

import com.fasterxml.jackson.databind.JsonNode
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper

/**
 * Optional client identification metadata a client MAY include as the 4th parameter
 * (`optional_client_metadata`) of a NIP-46 `connect` request — see nostr-protocol/nips#2381.
 *
 * It is a JSON-stringified object carrying display-only `name`, `url` and `image` fields,
 * letting the signer show who is connecting over the `bunker://` flow. It is for display only
 * and MUST NOT be used for authorization.
 */
data class BunkerClientMetadata(
    val name: String = "",
    val url: String = "",
    val image: String = "",
) {
    fun isEmpty(): Boolean = name.isBlank() && url.isBlank() && image.isBlank()

    companion object {
        /**
         * Parses the `optional_client_metadata` JSON string. Returns null when the input is
         * absent, blank or malformed — the field is optional and purely informational, so a
         * bad value must never break the connect flow.
         */
        fun parseOrNull(json: String?): BunkerClientMetadata? {
            if (json.isNullOrBlank()) return null
            return try {
                fromJson(JacksonMapper.mapper.readTree(json)).takeUnless { it.isEmpty() }
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Extracts the client metadata from a raw decrypted NIP-46 `connect` request JSON,
         * reading the 4th element of its `params` array.
         *
         * This deliberately works off the raw request rather than a parsed `BunkerRequest`:
         * Quartz only keeps `params[0..2]` (remote key, secret, permissions) and rebuilds the
         * `params` array from those, so the `optional_client_metadata` element would otherwise
         * be lost.
         */
        fun fromConnectRequest(requestJson: String?): BunkerClientMetadata? {
            if (requestJson.isNullOrBlank()) return null
            return try {
                val params = JacksonMapper.mapper.readTree(requestJson).get("params")
                if (params == null || !params.isArray) return null
                parseOrNull(params.get(3)?.asText())
            } catch (_: Exception) {
                null
            }
        }

        private fun fromJson(node: JsonNode): BunkerClientMetadata = BunkerClientMetadata(
            name = node.get("name")?.asText()?.trim().orEmpty(),
            url = node.get("url")?.asText()?.trim().orEmpty(),
            image = node.get("image")?.asText()?.trim().orEmpty(),
        )
    }
}

package com.greenart7c3.nostrsigner.models

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/**
 * Optional client identification metadata a client MAY include as the 4th
 * parameter (`optional_client_metadata`) of a NIP-46 `connect` request — see
 * nostr-protocol/nips#2381.
 *
 * It is a JSON-stringified object carrying the same display-only `name`, `url`
 * and `image` fields already advertised by `nostrconnect://` URIs, letting the
 * signer show who is connecting over the `bunker://` flow. It is for display
 * only and MUST NOT be used for authorization.
 */
data class BunkerClientMetadata(
    val name: String = "",
    val url: String = "",
    val image: String = "",
) {
    fun isEmpty(): Boolean = name.isBlank() && url.isBlank() && image.isBlank()

    companion object {
        private val mapper: ObjectMapper =
            jacksonObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        /**
         * Parses the `optional_client_metadata` JSON string. Returns null when the
         * input is absent, blank or malformed — the field is optional and purely
         * informational, so a bad value must never break the connect flow.
         */
        fun parseOrNull(json: String?): BunkerClientMetadata? {
            if (json.isNullOrBlank()) return null
            return try {
                fromJson(mapper.readTree(json)).takeUnless { it.isEmpty() }
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

package com.greenart7c3.nostrsigner.models

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

data class BunkerRequest(
    val id: String,
    val method: String,
    val params: Array<String>,
    var localKey: String,
    var relays: List<String>,
    var secret: String,
    var currentAccount: String,
) {
    fun toJson(): String {
        return mapper.writeValueAsString(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BunkerRequest

        if (id != other.id) return false
        if (method != other.method) return false
        if (!params.contentEquals(other.params)) return false
        if (localKey != other.localKey) return false
        if (relays != other.relays) return false
        if (secret != other.secret) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + method.hashCode()
        result = 31 * result + params.contentHashCode()
        result = 31 * result + localKey.hashCode()
        result = 31 * result + relays.hashCode()
        result = 31 * result + secret.hashCode()
        return result
    }

    companion object {
        val mapper: ObjectMapper =
            jacksonObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .registerModule(
                    SimpleModule()
                        .addDeserializer(BunkerRequest::class.java, BunkerRequestDeserializer()),
                )

        fun fromJson(jsonObject: JsonNode): BunkerRequest {
            return BunkerRequest(
                id = jsonObject.get("id").asText().intern(),
                method = jsonObject.get("method").asText().intern(),
                params = jsonObject.get("params").asIterable().toList().map {
                    it.asText().intern()
                }.toTypedArray(),
                localKey = jsonObject.get("localKey")?.asText()?.intern() ?: "",
                relays = jsonObject.get("relays")?.asIterable()?.toList()?.map {
                    it.asText().intern()
                } ?: listOf(),
                secret = jsonObject.get("secret")?.asText()?.intern() ?: "",
                currentAccount = jsonObject.get("currentAccount")?.asText()?.intern() ?: "",
            )
        }

        private class BunkerRequestDeserializer : StdDeserializer<BunkerRequest>(BunkerRequest::class.java) {
            override fun deserialize(
                jp: JsonParser,
                ctxt: DeserializationContext,
            ): BunkerRequest {
                return fromJson(jp.codec.readTree(jp))
            }
        }
    }
}

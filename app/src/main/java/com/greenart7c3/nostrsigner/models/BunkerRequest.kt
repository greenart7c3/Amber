package com.greenart7c3.nostrsigner.models

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.greenart7c3.nostrsigner.Amber
import com.vitorpamplona.ammolite.relays.COMMON_FEED_TYPES
import com.vitorpamplona.ammolite.relays.RelaySetupInfo

data class BunkerRequest(
    val id: String,
    val method: String,
    val params: Array<String>,
    var localKey: String,
    var relays: List<RelaySetupInfo>,
    var secret: String,
    var currentAccount: String,
    var encryptionType: EncryptionType,
    var nostrConnectSecret: String,
    var closeApplication: Boolean,
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
        if (currentAccount != other.currentAccount) return false
        if (encryptionType != other.encryptionType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + method.hashCode()
        result = 31 * result + params.contentHashCode()
        result = 31 * result + localKey.hashCode()
        result = 31 * result + relays.hashCode()
        result = 31 * result + secret.hashCode()
        result = 31 * result + currentAccount.hashCode()
        result = 31 * result + encryptionType.hashCode()
        return result
    }

    companion object {
        val mapper: ObjectMapper =
            jacksonObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .registerModule(
                    SimpleModule()
                        .addDeserializer(BunkerRequest::class.java, BunkerRequestDeserializer())
                        .addSerializer(BunkerRequest::class.java, BunkerRequestSerializer()),
                )

        fun fromJson(jsonObject: JsonNode): BunkerRequest {
            val encryptionTypeString = jsonObject.get("encryptionType")?.asText()?.intern() ?: ""
            val encryptionType = when (encryptionTypeString) {
                "NIP44" -> EncryptionType.NIP44
                "NIP04" -> EncryptionType.NIP04
                else -> EncryptionType.NIP04
            }

            return BunkerRequest(
                id = jsonObject.get("id").asText().intern(),
                method = jsonObject.get("method").asText().intern(),
                params = jsonObject.get("params").asIterable().toList().map {
                    it.asText().intern()
                }.toTypedArray(),
                localKey = jsonObject.get("localKey")?.asText()?.intern() ?: "",
                relays = jsonObject.get("relays")?.asIterable()?.toList()?.map {
                    var relayUrl = it.asText().intern()
                    if (relayUrl.endsWith("/")) relayUrl = relayUrl.dropLast(1)
                    RelaySetupInfo(relayUrl, read = true, write = true, feedTypes = COMMON_FEED_TYPES)
                } ?: Amber.instance.getSavedRelays().toList(),
                secret = jsonObject.get("secret")?.asText()?.intern() ?: "",
                currentAccount = jsonObject.get("currentAccount")?.asText()?.intern() ?: "",
                encryptionType = encryptionType,
                nostrConnectSecret = jsonObject.get("nostrConnectSecret")?.asText()?.intern() ?: "",
                closeApplication = jsonObject.get("closeApplication")?.asBoolean() != false,
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

        private class BunkerRequestSerializer : StdSerializer<BunkerRequest>(BunkerRequest::class.java) {
            override fun serialize(
                value: BunkerRequest,
                gen: JsonGenerator,
                provider: SerializerProvider,
            ) {
                gen.writeStartObject()
                gen.writeStringField("id", value.id)
                gen.writeStringField("method", value.method)
                gen.writeArrayFieldStart("params")
                value.params.forEach {
                    gen.writeString(it)
                }
                gen.writeEndArray()
                gen.writeStringField("localKey", value.localKey)
                gen.writeArrayFieldStart("relays")
                value.relays.forEach {
                    gen.writeString(it.url)
                }
                gen.writeEndArray()
                gen.writeStringField("secret", value.secret)
                gen.writeStringField("currentAccount", value.currentAccount)
                gen.writeStringField("encryptionType", value.encryptionType.toString())
                gen.writeStringField("nostrConnectSecret", value.nostrConnectSecret)
                gen.writeBooleanField("closeApplication", value.closeApplication)
                gen.writeEndObject()
            }
        }
    }
}

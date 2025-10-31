package com.greenart7c3.nostrsigner.models

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
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
import com.fasterxml.jackson.module.kotlin.readValue
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.ui.RememberType
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import kotlin.collections.asIterable

enum class EncryptionType {
    NIP44,
    NIP04,
}

data class AmberBunkerRequest(
    val request: BunkerRequest,
    val localKey: String,
    val relays: List<NormalizedRelayUrl>,
    val currentAccount: String,
    val nostrConnectSecret: String,
    val closeApplication: Boolean,
    val name: String,
    val signedEvent: Event?,
    val encryptDecryptResponse: String?,
    val checked: MutableState<Boolean> = mutableStateOf(true),
    val rememberType: MutableState<RememberType> = mutableStateOf(RememberType.NEVER),
    val encryptionType: EncryptionType,
) {
    fun toJson(): String {
        return mapper.writeValueAsString(this)
    }

    companion object {
        val mapper: ObjectMapper =
            jacksonObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .registerModule(
                    SimpleModule()
                        .addDeserializer(AmberBunkerRequest::class.java, AmberBunkerRequestDeserializer())
                        .addSerializer(AmberBunkerRequest::class.java, AmberBunkerRequestSerializer()),
                )

        fun fromJson(jsonObject: JsonNode): AmberBunkerRequest {
            return AmberBunkerRequest(
                request = JacksonMapper.mapper.readValue(jsonObject.get("request").asText().intern()),
                localKey = jsonObject.get("localKey")?.asText()?.intern() ?: "",
                relays = jsonObject.get("relays")?.asIterable()?.toList()?.mapNotNull {
                    RelayUrlNormalizer.normalizeOrNull(it.asText())
                } ?: Amber.instance.getSavedRelays().toList(),
                currentAccount = jsonObject.get("currentAccount")?.asText()?.intern() ?: "",
                nostrConnectSecret = jsonObject.get("nostrConnectSecret")?.asText()?.intern() ?: "",
                closeApplication = jsonObject.get("closeApplication")?.asBoolean() != false,
                name = jsonObject.get("name")?.asText()?.intern() ?: "",
                signedEvent = jsonObject.get("signedEvent")?.asText()?.intern()?.let {
                    try {
                        JacksonMapper.fromJson(it)
                    } catch (_: Exception) {
                        null
                    }
                },
                encryptDecryptResponse = jsonObject.get("encryptDecryptResponse")?.asText()?.intern(),
                encryptionType = EncryptionType.valueOf(jsonObject.get("encryptionType")?.asText()?.intern() ?: "NIP44"),
            )
        }

        private class AmberBunkerRequestDeserializer : StdDeserializer<AmberBunkerRequest>(AmberBunkerRequest::class.java) {
            override fun deserialize(
                jp: JsonParser,
                ctxt: DeserializationContext,
            ): AmberBunkerRequest {
                return fromJson(jp.codec.readTree(jp))
            }
        }

        private class AmberBunkerRequestSerializer : StdSerializer<AmberBunkerRequest>(AmberBunkerRequest::class.java) {
            override fun serialize(
                value: AmberBunkerRequest,
                gen: JsonGenerator,
                provider: SerializerProvider,
            ) {
                gen.writeStartObject()
                gen.writeStringField("request", mapper.writeValueAsString(value.request))
                gen.writeStringField("localKey", value.localKey)
                gen.writeArrayFieldStart("relays")
                value.relays.forEach {
                    gen.writeString(it.url)
                }
                gen.writeEndArray()
                gen.writeStringField("currentAccount", value.currentAccount)
                gen.writeStringField("nostrConnectSecret", value.nostrConnectSecret)
                gen.writeBooleanField("closeApplication", value.closeApplication)
                gen.writeStringField("name", value.name)
                gen.writeStringField("signedEvent", value.signedEvent?.toJson())
                gen.writeStringField("encryptDecryptResponse", value.encryptDecryptResponse)
                gen.writeStringField("encryptionType", value.encryptionType.name)
                gen.writeEndObject()
            }
        }
    }
}

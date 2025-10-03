package com.greenart7c3.nostrsigner.service.model

import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.jackson.toTypedArray
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
open class AmberEvent(
    var id: HexKey,
    var pubKey: HexKey,
    val createdAt: Long,
    val kind: Int,
    val tags: Array<Array<String>>,
    val content: String,
    val sig: HexKey,
) {
    private class EventSerializer : StdSerializer<AmberEvent>(AmberEvent::class.java) {
        override fun serialize(
            event: AmberEvent,
            gen: JsonGenerator,
            provider: SerializerProvider,
        ) {
            gen.writeStartObject()
            gen.writeStringField("id", event.id)
            gen.writeStringField("pubkey", event.pubKey)
            gen.writeNumberField("created_at", event.createdAt)
            gen.writeNumberField("kind", event.kind)
            gen.writeArrayFieldStart("tags")
            event.tags.forEach { tag -> gen.writeArray(tag, 0, tag.size) }
            gen.writeEndArray()
            gen.writeStringField("content", event.content)
            gen.writeStringField("sig", event.sig)
            gen.writeEndObject()
        }
    }

    private class EventDeserializer : StdDeserializer<AmberEvent>(AmberEvent::class.java) {
        override fun deserialize(
            jp: JsonParser,
            ctxt: DeserializationContext,
        ): AmberEvent = EventManualDeserializer.fromJson(jp.codec.readTree(jp))
    }

    private class EventManualDeserializer {
        companion object {
            fun fromJson(jsonObject: JsonNode): AmberEvent =
                AmberEvent(
                    id = jsonObject.get("id")?.asText()?.intern() ?: "",
                    pubKey = jsonObject.get("pubkey")?.asText()?.intern() ?: "",
                    createdAt = jsonObject.get("created_at")?.asLong() ?: TimeUtils.now(),
                    kind = jsonObject.get("kind").asInt(),
                    tags = jsonObject.get("tags")?.toTypedArray {
                        it.toTypedArray { s -> if (s.isNull) "" else s.asText().intern() }
                    } ?: emptyArray(),
                    content = jsonObject.get("content").asText(),
                    sig = jsonObject.get("sig")?.asText() ?: "",
                )
        }
    }

    companion object {
        val mapper: ObjectMapper =
            jacksonObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
                .setDefaultPrettyPrinter(JacksonMapper.defaultPrettyPrinter)
                .registerModule(
                    SimpleModule()
                        .addSerializer(AmberEvent::class.java, EventSerializer())
                        .addDeserializer(AmberEvent::class.java, EventDeserializer()),
                )

        fun fromJson(json: String): AmberEvent = mapper.readValue(json, AmberEvent::class.java)

        fun toEvent(amberEvent: AmberEvent): Event {
            return Event(
                amberEvent.id,
                amberEvent.pubKey,
                amberEvent.createdAt,
                amberEvent.kind,
                amberEvent.tags,
                amberEvent.content,
                amberEvent.sig,
            )
        }

        fun relay(event: Event): String {
            return event.tags.filter { it.size > 1 && it[0] == "relay" }.map { it[1] }.first()
        }
    }
}

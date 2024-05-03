package com.greenart7c3.nostrsigner.service.model

import androidx.compose.runtime.Immutable
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.SerializedName
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.events.Event
import java.lang.reflect.Type

@Immutable
open class AmberEvent(
    var id: HexKey,
    @SerializedName("pubkey") var pubKey: HexKey,
    @SerializedName("created_at") val createdAt: Long,
    val kind: Int,
    val tags: Array<Array<String>>,
    val content: String,
    val sig: HexKey,
) {
    private class EventDeserializer : JsonDeserializer<AmberEvent> {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type?,
            context: JsonDeserializationContext?,
        ): AmberEvent {
            val jsonObject = json.asJsonObject
            return AmberEvent(
                id = jsonObject.get("id")?.asString ?: "",
                pubKey = jsonObject.get("pubkey")?.asString ?: "",
                createdAt = jsonObject.get("created_at")?.asLong ?: TimeUtils.now(),
                kind = jsonObject.get("kind").asInt,
                tags =
                    jsonObject.get("tags")?.asJsonArray?.map {
                        it.asJsonArray.mapNotNull { s -> if (s.isJsonNull) null else s.asString }.toTypedArray()
                    }?.toTypedArray() ?: emptyArray(),
                content = jsonObject.get("content").asString,
                sig = jsonObject.get("sig")?.asString ?: "",
            )
        }
    }

    private class EventSerializer : JsonSerializer<AmberEvent> {
        override fun serialize(
            src: AmberEvent,
            typeOfSrc: Type?,
            context: JsonSerializationContext?,
        ): JsonElement {
            return JsonObject().apply {
                addProperty("id", src.id)
                addProperty("pubkey", src.pubKey)
                addProperty("created_at", src.createdAt)
                addProperty("kind", src.kind)
                add(
                    "tags",
                    JsonArray().also { jsonTags ->
                        src.tags.forEach { tag ->
                            jsonTags.add(
                                JsonArray().also { jsonTagElement ->
                                    tag.forEach { tagElement ->
                                        jsonTagElement.add(tagElement)
                                    }
                                },
                            )
                        }
                    },
                )
                addProperty("content", src.content)
                addProperty("sig", src.sig)
            }
        }
    }

    companion object {
        private val gson: Gson =
            GsonBuilder()
                .disableHtmlEscaping()
                .registerTypeAdapter(AmberEvent::class.java, EventSerializer())
                .registerTypeAdapter(AmberEvent::class.java, EventDeserializer())
                .create()

        fun fromJson(json: String): AmberEvent = gson.fromJson(json, AmberEvent::class.java)

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

package com.greenart7c3.nostrsigner.models

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

data class BunkerMetadata(
    val name: String,
    val url: String,
    val description: String,
    val perms: String,
) {
    companion object {
        val mapper: ObjectMapper =
            jacksonObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .registerModule(
                    SimpleModule()
                        .addDeserializer(BunkerMetadata::class.java, BunkerMetadataDeserializer()),
                )

        fun fromJson(jsonObject: JsonNode): BunkerMetadata = BunkerMetadata(
            name = jsonObject.get("name")?.asText()?.intern() ?: "",
            url = jsonObject.get("url")?.asText()?.intern() ?: "",
            description = jsonObject.get("description")?.asText()?.intern() ?: "",
            perms = jsonObject.get("perms")?.asText()?.intern() ?: "",
        )

        private class BunkerMetadataDeserializer : StdDeserializer<BunkerMetadata>(BunkerMetadata::class.java) {
            override fun deserialize(
                jp: JsonParser,
                ctxt: DeserializationContext,
            ): BunkerMetadata = fromJson(jp.codec.readTree(jp))
        }
    }
}

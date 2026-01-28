package com.greenart7c3.nostrsigner.models

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

data class Result(
    val `package`: String?,
    val signature: String?,
    val result: String?,
    val rejected: Boolean?,
    val id: String?,
) {
    companion object {
        val mapper =
            jacksonObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .registerModule(
                    SimpleModule().addSerializer(Result::class.java, ResultSerializer()),
                )

        private class ResultSerializer : StdSerializer<Result>(Result::class.java) {
            override fun serialize(
                result: Result,
                gen: JsonGenerator,
                provider: SerializerProvider,
            ) {
                gen.writeStartObject()
                gen.writeStringField("id", result.id)
                gen.writeStringField("package", result.`package`)
                gen.writeStringField("signature", result.signature)
                gen.writeStringField("result", result.result)
                result.rejected?.let {
                    gen.writeBooleanField("rejected", it)
                }
                gen.writeEndObject()
            }
        }
    }
}

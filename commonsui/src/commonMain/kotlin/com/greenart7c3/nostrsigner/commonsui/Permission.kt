package com.greenart7c3.nostrsigner.commonsui

import androidx.compose.runtime.Composable
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The text to show for a permission: a string resource, or the raw type. */
@Immutable
sealed interface PermissionText {
    /** The string [resource], formatted with [args] (`%1$s`, …). */
    data class Resource(val resource: StringResource, val args: List<String> = emptyList()) : PermissionText {
        constructor(resource: StringResource, arg: String) : this(resource, listOf(arg))
    }

    /** Text shown as is: a permission type Amber has no description for. */
    data class Literal(val text: String) : PermissionText
}

/** The text in the UI language, recomposing when it changes. */
@Composable
fun PermissionText.localized(): String = when (this) {
    is PermissionText.Literal -> text
    is PermissionText.Resource -> stringResource(resource, *args.toTypedArray())
}

/** The text in the UI language, for code outside composition. */
fun PermissionText.localizedBlocking(): String = when (this) {
    is PermissionText.Literal -> text
    is PermissionText.Resource -> getStringBlocking(resource, *args.toTypedArray())
}

/** What [this] permission lets an app do (see [Permission.description]). */
@Composable
fun Permission.localized(shouldTranslateConnect: Boolean = false): String = description(shouldTranslateConnect).localized()

/** What [this] permission lets an app do, for code outside composition. */
fun Permission.localizedBlocking(shouldTranslateConnect: Boolean = false): String = description(shouldTranslateConnect).localizedBlocking()

/**
 * A permission an app asks for or holds: a request method ([type]) and, for
 * `sign_event` / `nip`, the event [kind] or NIP number. Shared by the Android
 * and desktop apps, with its JSON form (`{"type": …, "kind": …}`) and what it
 * means to the user ([description]).
 */
@Immutable
data class Permission(
    val type: String,
    val kind: Int?,
    var checked: Boolean = true,
) {
    private class PermissionSerializer : StdSerializer<Permission>(Permission::class.java) {
        override fun serialize(
            permission: Permission,
            gen: JsonGenerator,
            provider: SerializerProvider,
        ) {
            gen.writeStartObject()
            gen.writeStringField("type", permission.type)
            permission.kind?.let {
                gen.writeNumberField("kind", it)
            }
            gen.writeEndObject()
        }
    }

    private class PermissionDeserializer : StdDeserializer<Permission>(Permission::class.java) {
        override fun deserialize(
            jp: JsonParser,
            ctxt: DeserializationContext,
        ): Permission = fromJson(jp.codec.readTree(jp))
    }

    companion object {
        private fun fromJson(jsonObject: JsonNode): Permission = Permission(
            type = jsonObject.get("type").asText().intern(),
            kind = jsonObject.get("kind")?.asText()?.toIntOrNull(),
        )

        val mapper: ObjectMapper =
            ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
                .registerModule(
                    SimpleModule()
                        .addSerializer(Permission::class.java, PermissionSerializer())
                        .addDeserializer(Permission::class.java, PermissionDeserializer()),
                )
    }

    /**
     * What this permission lets an app do, as the string to show (see
     * [localized] / [localizedBlocking]). With [shouldTranslateConnect], `connect` is the short
     * "Connect" label instead of the full sentence.
     */
    // LAST_COMMIT_CHECKED = "14ec14dac92d891715239bfb83ed1c9bdf2ac382"
    fun description(shouldTranslateConnect: Boolean = false): PermissionText = when (type.trim().lowercase()) {
        "get_public_key" -> PermissionText.Resource(Res.string.read_your_public_key)
        "nip04_encrypt" -> {
            PermissionText.Resource(Res.string.encrypt_data_using_nip_4)
        }
        "nip04_decrypt" -> {
            PermissionText.Resource(Res.string.decrypt_data_using_nip_4)
        }
        "nip44_decrypt" -> {
            PermissionText.Resource(Res.string.decrypt_data_using_nip_44)
        }
        "nip44_encrypt" -> {
            PermissionText.Resource(Res.string.encrypt_data_using_nip_44)
        }
        "nip44_v3_encrypt", "nip44v3_encrypt" -> PermissionText.Resource(Res.string.encrypt_data_using_nip_44)
        "nip44_v3_decrypt", "nip44v3_decrypt" -> PermissionText.Resource(Res.string.decrypt_data_using_nip_44)
        "ping" -> PermissionText.Resource(Res.string.ping)
        "decrypt_zap_event" -> {
            PermissionText.Resource(Res.string.decrypt_private_zaps)
        }
        "sign_psbt" -> {
            PermissionText.Resource(Res.string.sign_psbt)
        }
        "encrypt_clear_text" -> {
            PermissionText.Resource(Res.string.encrypt_clear_text)
        }
        "decrypt_clear_text" -> {
            PermissionText.Resource(Res.string.decrypt_clear_text)
        }
        "encrypt_event" -> {
            PermissionText.Resource(Res.string.encrypt_event)
        }
        "decrypt_event" -> {
            PermissionText.Resource(Res.string.decrypt_event)
        }
        "encrypt_tag_array" -> {
            PermissionText.Resource(Res.string.encrypt_tag_array)
        }
        "decrypt_tag_array" -> {
            PermissionText.Resource(Res.string.decrypt_tag_array)
        }
        "sign_event" -> {
            when (kind) {
                0 -> PermissionText.Resource(Res.string.event_kind_0)
                1 -> PermissionText.Resource(Res.string.event_kind_1)
                3 -> PermissionText.Resource(Res.string.event_kind_3)
                4 -> PermissionText.Resource(Res.string.event_kind_4)
                5 -> PermissionText.Resource(Res.string.event_kind_5)
                6 -> PermissionText.Resource(Res.string.event_kind_6)
                7 -> PermissionText.Resource(Res.string.event_kind_7)
                8 -> PermissionText.Resource(Res.string.event_kind_8)
                9 -> PermissionText.Resource(Res.string.event_kind_9)
                10 -> PermissionText.Resource(Res.string.event_kind_10)
                11 -> PermissionText.Resource(Res.string.event_kind_11)
                12 -> PermissionText.Resource(Res.string.event_kind_12)
                13 -> PermissionText.Resource(Res.string.event_kind_13)
                14 -> PermissionText.Resource(Res.string.event_kind_14)
                15 -> PermissionText.Resource(Res.string.event_kind_15)
                16 -> PermissionText.Resource(Res.string.event_kind_16)
                17 -> PermissionText.Resource(Res.string.event_kind_17)
                20 -> PermissionText.Resource(Res.string.event_kind_20)
                21 -> PermissionText.Resource(Res.string.event_kind_21)
                22 -> PermissionText.Resource(Res.string.event_kind_22)
                24 -> PermissionText.Resource(Res.string.event_kind_24)
                30 -> PermissionText.Resource(Res.string.event_kind_30)
                40 -> PermissionText.Resource(Res.string.event_kind_40)
                41 -> PermissionText.Resource(Res.string.event_kind_41)
                42 -> PermissionText.Resource(Res.string.event_kind_42)
                43 -> PermissionText.Resource(Res.string.event_kind_43)
                44 -> PermissionText.Resource(Res.string.event_kind_44)
                54 -> PermissionText.Resource(Res.string.event_kind_54)
                62 -> PermissionText.Resource(Res.string.event_kind_62)
                64 -> PermissionText.Resource(Res.string.event_kind_64)
                78 -> PermissionText.Resource(Res.string.event_kind_78)
                82 -> PermissionText.Resource(Res.string.event_kind_82)
                178 -> PermissionText.Resource(Res.string.event_kind_178)
                443 -> PermissionText.Resource(Res.string.event_kind_443)
                444 -> PermissionText.Resource(Res.string.event_kind_444)
                445 -> PermissionText.Resource(Res.string.event_kind_445)
                446 -> PermissionText.Resource(Res.string.event_kind_446)
                447 -> PermissionText.Resource(Res.string.event_kind_447)
                448 -> PermissionText.Resource(Res.string.event_kind_448)
                449 -> PermissionText.Resource(Res.string.event_kind_449)
                450 -> PermissionText.Resource(Res.string.event_kind_450)
                451 -> PermissionText.Resource(Res.string.event_kind_451)
                452 -> PermissionText.Resource(Res.string.event_kind_452)
                818 -> PermissionText.Resource(Res.string.event_kind_818)
                1009 -> PermissionText.Resource(Res.string.event_kind_1009)
                1010 -> PermissionText.Resource(Res.string.event_kind_1010)
                1011 -> PermissionText.Resource(Res.string.event_kind_1011)
                1018 -> PermissionText.Resource(Res.string.event_kind_1018)
                1021 -> PermissionText.Resource(Res.string.event_kind_1021)
                1022 -> PermissionText.Resource(Res.string.event_kind_1022)
                1040 -> PermissionText.Resource(Res.string.event_kind_1040)
                1059 -> PermissionText.Resource(Res.string.event_kind_1059)
                1063 -> PermissionText.Resource(Res.string.event_kind_1063)
                1064 -> PermissionText.Resource(Res.string.event_kind_1064)
                1065 -> PermissionText.Resource(Res.string.event_kind_1065)
                1068 -> PermissionText.Resource(Res.string.event_kind_1068)
                1111 -> PermissionText.Resource(Res.string.event_kind_1111)
                1163 -> PermissionText.Resource(Res.string.event_kind_1163)
                1200 -> PermissionText.Resource(Res.string.event_kind_1200)
                1201 -> PermissionText.Resource(Res.string.event_kind_1201)
                1202 -> PermissionText.Resource(Res.string.event_kind_1202)
                1209 -> PermissionText.Resource(Res.string.event_kind_1209)
                1210 -> PermissionText.Resource(Res.string.event_kind_1210)
                1222 -> PermissionText.Resource(Res.string.event_kind_1222)
                1244 -> PermissionText.Resource(Res.string.event_kind_1244)
                1301 -> PermissionText.Resource(Res.string.event_kind_1301)
                1311 -> PermissionText.Resource(Res.string.event_kind_1311)
                1312 -> PermissionText.Resource(Res.string.event_kind_1312)
                1313 -> PermissionText.Resource(Res.string.event_kind_1313)
                1315 -> PermissionText.Resource(Res.string.event_kind_1315)
                1316 -> PermissionText.Resource(Res.string.event_kind_1316)
                1337 -> PermissionText.Resource(Res.string.event_kind_1337)
                1617 -> PermissionText.Resource(Res.string.event_kind_1617)
                1618 -> PermissionText.Resource(Res.string.event_kind_1618)
                1619 -> PermissionText.Resource(Res.string.event_kind_1619)
                1621 -> PermissionText.Resource(Res.string.event_kind_1621)
                1622 -> PermissionText.Resource(Res.string.event_kind_1622)
                in 1630..1633 -> PermissionText.Resource(Res.string.event_kind_1630_1633)
                1808 -> PermissionText.Resource(Res.string.event_kind_1808)
                1971 -> PermissionText.Resource(Res.string.event_kind_1971)
                1984 -> PermissionText.Resource(Res.string.event_kind_1984)
                1985 -> PermissionText.Resource(Res.string.event_kind_1985)
                1986 -> PermissionText.Resource(Res.string.event_kind_1986)
                1987 -> PermissionText.Resource(Res.string.event_kind_1987)
                3079 -> PermissionText.Resource(Res.string.event_kind_3079)
                3080 -> PermissionText.Resource(Res.string.event_kind_3080)
                3083 -> PermissionText.Resource(Res.string.event_kind_3083)
                4242 -> PermissionText.Resource(Res.string.event_kind_4242)
                4891 -> PermissionText.Resource(Res.string.event_kind_4891)
                20001 -> PermissionText.Resource(Res.string.event_kind_20001)
                20002 -> PermissionText.Resource(Res.string.event_kind_20002)
                2003 -> PermissionText.Resource(Res.string.event_kind_2003)
                2004 -> PermissionText.Resource(Res.string.event_kind_2004)
                2022 -> PermissionText.Resource(Res.string.event_kind_2022)
                2473 -> PermissionText.Resource(Res.string.event_kind_2473)
                22236 -> PermissionText.Resource(Res.string.event_kind_22236)
                25742 -> PermissionText.Resource(Res.string.event_kind_25742)
                3063 -> PermissionText.Resource(Res.string.event_kind_3063)
                3314 -> PermissionText.Resource(Res.string.event_kind_3314)
                3367 -> PermissionText.Resource(Res.string.event_kind_3367)
                25910 -> PermissionText.Resource(Res.string.event_kind_25910)
                34238 -> PermissionText.Resource(Res.string.event_kind_34238)
                39307 -> PermissionText.Resource(Res.string.event_kind_39307)
                40003 -> PermissionText.Resource(Res.string.event_kind_40003)
                41010 -> PermissionText.Resource(Res.string.event_kind_41010)
                4223 -> PermissionText.Resource(Res.string.event_kind_4223)
                4312 -> PermissionText.Resource(Res.string.event_kind_4312)
                4454 -> PermissionText.Resource(Res.string.event_kind_4454)
                4455 -> PermissionText.Resource(Res.string.event_kind_4455)
                45001 -> PermissionText.Resource(Res.string.event_kind_45001)
                45002 -> PermissionText.Resource(Res.string.event_kind_45002)
                45003 -> PermissionText.Resource(Res.string.event_kind_45003)
                4550 -> PermissionText.Resource(Res.string.event_kind_4550)
                in 5000..5999 -> PermissionText.Resource(Res.string.event_kind_5000_5999)
                in 6000..6999 -> PermissionText.Resource(Res.string.event_kind_6000_6999)
                7000 -> PermissionText.Resource(Res.string.event_kind_7000)
                7374 -> PermissionText.Resource(Res.string.event_kind_7374)
                7375 -> PermissionText.Resource(Res.string.event_kind_7375)
                7376 -> PermissionText.Resource(Res.string.event_kind_7376)
                7516 -> PermissionText.Resource(Res.string.event_kind_7516)
                8000 -> PermissionText.Resource(Res.string.event_kind_8000)
                8001 -> PermissionText.Resource(Res.string.event_kind_8001)
                8211 -> PermissionText.Resource(Res.string.event_kind_8211)
                8333 -> PermissionText.Resource(Res.string.event_kind_8333)
                9000 -> PermissionText.Resource(Res.string.event_kind_9000)
                9001 -> PermissionText.Resource(Res.string.event_kind_9001)
                9002 -> PermissionText.Resource(Res.string.event_kind_9002)
                9003 -> PermissionText.Resource(Res.string.event_kind_9003)
                9004 -> PermissionText.Resource(Res.string.event_kind_9004)
                9005 -> PermissionText.Resource(Res.string.event_kind_9005)
                9006 -> PermissionText.Resource(Res.string.event_kind_9006)
                9007 -> PermissionText.Resource(Res.string.event_kind_9007)
                9008 -> PermissionText.Resource(Res.string.event_kind_9008)
                9009 -> PermissionText.Resource(Res.string.event_kind_9009)
                9010 -> PermissionText.Resource(Res.string.event_kind_9010)
                9021 -> PermissionText.Resource(Res.string.event_kind_9021)
                9022 -> PermissionText.Resource(Res.string.event_kind_9022)
                in 9000..9030 -> PermissionText.Resource(Res.string.event_kind_9000_9030)
                9041 -> PermissionText.Resource(Res.string.event_kind_9041)
                9321 -> PermissionText.Resource(Res.string.event_kind_9321)
                9450 -> PermissionText.Resource(Res.string.event_kind_9450)
                9467 -> PermissionText.Resource(Res.string.event_kind_9467)
                9733 -> PermissionText.Resource(Res.string.event_kind_9733)
                9734 -> PermissionText.Resource(Res.string.event_kind_9734)
                9735 -> PermissionText.Resource(Res.string.event_kind_9735)
                9736 -> PermissionText.Resource(Res.string.event_kind_9736)
                9802 -> PermissionText.Resource(Res.string.event_kind_9802)
                9840 -> PermissionText.Resource(Res.string.event_kind_9840)
                9843 -> PermissionText.Resource(Res.string.event_kind_9843)
                9844 -> PermissionText.Resource(Res.string.event_kind_9844)
                10000 -> PermissionText.Resource(Res.string.event_kind_10000)
                10001 -> PermissionText.Resource(Res.string.event_kind_10001)
                10002 -> PermissionText.Resource(Res.string.event_kind_10002)
                10003 -> PermissionText.Resource(Res.string.event_kind_10003)
                10004 -> PermissionText.Resource(Res.string.event_kind_10004)
                10005 -> PermissionText.Resource(Res.string.event_kind_10005)
                10006 -> PermissionText.Resource(Res.string.event_kind_10006)
                10007 -> PermissionText.Resource(Res.string.event_kind_10007)
                10008 -> PermissionText.Resource(Res.string.event_kind_10008)
                10009 -> PermissionText.Resource(Res.string.event_kind_10009)
                10011 -> PermissionText.Resource(Res.string.event_kind_10011)
                10012 -> PermissionText.Resource(Res.string.event_kind_10012)
                10013 -> PermissionText.Resource(Res.string.event_kind_10013)
                10015 -> PermissionText.Resource(Res.string.event_kind_10015)
                10017 -> PermissionText.Resource(Res.string.event_kind_10017)
                10018 -> PermissionText.Resource(Res.string.event_kind_10018)
                10019 -> PermissionText.Resource(Res.string.event_kind_10019)
                10020 -> PermissionText.Resource(Res.string.event_kind_10020)
                10023 -> PermissionText.Resource(Res.string.event_kind_10023)
                10030 -> PermissionText.Resource(Res.string.event_kind_10030)
                10040 -> PermissionText.Resource(Res.string.event_kind_10040)
                10044 -> PermissionText.Resource(Res.string.event_kind_10044)
                10050 -> PermissionText.Resource(Res.string.event_kind_10050)
                10051 -> PermissionText.Resource(Res.string.event_kind_10051)
                10054 -> PermissionText.Resource(Res.string.event_kind_10054)
                10058 -> PermissionText.Resource(Res.string.event_kind_10058)
                10063 -> PermissionText.Resource(Res.string.event_kind_10063)
                10064 -> PermissionText.Resource(Res.string.event_kind_10064)
                10081 -> PermissionText.Resource(Res.string.event_kind_10081)
                10086 -> PermissionText.Resource(Res.string.event_kind_10086)
                10087 -> PermissionText.Resource(Res.string.event_kind_10087)
                10088 -> PermissionText.Resource(Res.string.event_kind_10088)
                10089 -> PermissionText.Resource(Res.string.event_kind_10089)
                10090 -> PermissionText.Resource(Res.string.event_kind_10090)
                10096 -> PermissionText.Resource(Res.string.event_kind_10096)
                10101 -> PermissionText.Resource(Res.string.event_kind_10101)
                10102 -> PermissionText.Resource(Res.string.event_kind_10102)
                10112 -> PermissionText.Resource(Res.string.event_kind_10112)
                10133 -> PermissionText.Resource(Res.string.event_kind_10133)
                10154 -> PermissionText.Resource(Res.string.event_kind_10154)
                10166 -> PermissionText.Resource(Res.string.event_kind_10166)
                10312 -> PermissionText.Resource(Res.string.event_kind_10312)
                10317 -> PermissionText.Resource(Res.string.event_kind_10317)
                10443 -> PermissionText.Resource(Res.string.event_kind_10443)
                11125 -> PermissionText.Resource(Res.string.event_kind_11125)
                11871 -> PermissionText.Resource(Res.string.event_kind_11871)
                12022 -> PermissionText.Resource(Res.string.event_kind_12022)
                12473 -> PermissionText.Resource(Res.string.event_kind_12473)
                13194 -> PermissionText.Resource(Res.string.event_kind_13194)
                13302 -> PermissionText.Resource(Res.string.event_kind_13302)
                13303 -> PermissionText.Resource(Res.string.event_kind_13303)
                13534 -> PermissionText.Resource(Res.string.event_kind_13534)
                14919 -> PermissionText.Resource(Res.string.event_kind_14919)
                14920 -> PermissionText.Resource(Res.string.event_kind_14920)
                14921 -> PermissionText.Resource(Res.string.event_kind_14921)
                15128 -> PermissionText.Resource(Res.string.event_kind_15128)
                15129 -> PermissionText.Resource(Res.string.event_kind_15129)
                16158 -> PermissionText.Resource(Res.string.event_kind_16158)
                16767 -> PermissionText.Resource(Res.string.event_kind_16767)
                16769 -> PermissionText.Resource(Res.string.event_kind_16769)
                17375 -> PermissionText.Resource(Res.string.event_kind_17375)
                20013 -> PermissionText.Resource(Res.string.event_kind_20013)
                20014 -> PermissionText.Resource(Res.string.event_kind_20014)
                21000 -> PermissionText.Resource(Res.string.event_kind_21000)
                21001 -> PermissionText.Resource(Res.string.event_kind_21001)
                21002 -> PermissionText.Resource(Res.string.event_kind_21002)
                21003 -> PermissionText.Resource(Res.string.event_kind_21003)
                21059 -> PermissionText.Resource(Res.string.event_kind_21059)
                22242 -> PermissionText.Resource(Res.string.event_kind_22242)
                23194 -> PermissionText.Resource(Res.string.event_kind_23194)
                23195 -> PermissionText.Resource(Res.string.event_kind_23195)
                23197 -> PermissionText.Resource(Res.string.event_kind_23197)
                23333 -> PermissionText.Resource(Res.string.event_kind_23333)
                24450 -> PermissionText.Resource(Res.string.event_kind_24450)
                23903 -> PermissionText.Resource(Res.string.event_kind_23903)
                24133 -> PermissionText.Resource(Res.string.event_kind_24133)
                24242 -> PermissionText.Resource(Res.string.event_kind_24242)
                25050 -> PermissionText.Resource(Res.string.event_kind_25050)
                25051 -> PermissionText.Resource(Res.string.event_kind_25051)
                25052 -> PermissionText.Resource(Res.string.event_kind_25052)
                25053 -> PermissionText.Resource(Res.string.event_kind_25053)
                25054 -> PermissionText.Resource(Res.string.event_kind_25054)
                25055 -> PermissionText.Resource(Res.string.event_kind_25055)
                27235 -> PermissionText.Resource(Res.string.event_kind_27235)
                22456 -> PermissionText.Resource(Res.string.event_kind_22456)
                28934 -> PermissionText.Resource(Res.string.event_kind_28934)
                28935 -> PermissionText.Resource(Res.string.event_kind_28935)
                28936 -> PermissionText.Resource(Res.string.event_kind_28936)
                30000 -> PermissionText.Resource(Res.string.event_kind_30000)
                30001 -> PermissionText.Resource(Res.string.event_kind_30001)
                30002 -> PermissionText.Resource(Res.string.event_kind_30002)
                30003 -> PermissionText.Resource(Res.string.event_kind_30003)
                30004 -> PermissionText.Resource(Res.string.event_kind_30004)
                30005 -> PermissionText.Resource(Res.string.event_kind_30005)
                30006 -> PermissionText.Resource(Res.string.event_kind_30006)
                30007 -> PermissionText.Resource(Res.string.event_kind_30007)
                30008 -> PermissionText.Resource(Res.string.event_kind_30008)
                30009 -> PermissionText.Resource(Res.string.event_kind_30009)
                30015 -> PermissionText.Resource(Res.string.event_kind_30015)
                30017 -> PermissionText.Resource(Res.string.event_kind_30017)
                30018 -> PermissionText.Resource(Res.string.event_kind_30018)
                30019 -> PermissionText.Resource(Res.string.event_kind_30019)
                30020 -> PermissionText.Resource(Res.string.event_kind_30020)
                30023 -> PermissionText.Resource(Res.string.event_kind_30023)
                30024 -> PermissionText.Resource(Res.string.event_kind_30024)
                30030 -> PermissionText.Resource(Res.string.event_kind_30030)
                30040 -> PermissionText.Resource(Res.string.event_kind_30040)
                30041 -> PermissionText.Resource(Res.string.event_kind_30041)
                30053 -> PermissionText.Resource(Res.string.event_kind_30053)
                30054 -> PermissionText.Resource(Res.string.event_kind_30054)
                30055 -> PermissionText.Resource(Res.string.event_kind_30055)
                30063 -> PermissionText.Resource(Res.string.event_kind_30063)
                30064 -> PermissionText.Resource(Res.string.event_kind_30064)
                30065 -> PermissionText.Resource(Res.string.event_kind_30065)
                30066 -> PermissionText.Resource(Res.string.event_kind_30066)
                30067 -> PermissionText.Resource(Res.string.event_kind_30067)
                30068 -> PermissionText.Resource(Res.string.event_kind_30068)
                30078 -> PermissionText.Resource(Res.string.event_kind_30078)
                30166 -> PermissionText.Resource(Res.string.event_kind_30166)
                30267 -> PermissionText.Resource(Res.string.event_kind_30267)
                30296 -> PermissionText.Resource(Res.string.event_kind_30296)
                30297 -> PermissionText.Resource(Res.string.event_kind_30297)
                30298 -> PermissionText.Resource(Res.string.event_kind_30298)
                30311 -> PermissionText.Resource(Res.string.event_kind_30311)
                30312 -> PermissionText.Resource(Res.string.event_kind_30312)
                30313 -> PermissionText.Resource(Res.string.event_kind_30313)
                30315 -> PermissionText.Resource(Res.string.event_kind_30315)
                30382 -> PermissionText.Resource(Res.string.event_kind_30382)
                30383 -> PermissionText.Resource(Res.string.event_kind_30383)
                30384 -> PermissionText.Resource(Res.string.event_kind_30384)
                30385 -> PermissionText.Resource(Res.string.event_kind_30385)
                30402 -> PermissionText.Resource(Res.string.event_kind_30402)
                30403 -> PermissionText.Resource(Res.string.event_kind_30403)
                30443 -> PermissionText.Resource(Res.string.event_kind_30443)
                30617 -> PermissionText.Resource(Res.string.event_kind_30617)
                30618 -> PermissionText.Resource(Res.string.event_kind_30618)
                30624 -> PermissionText.Resource(Res.string.event_kind_30624)
                30817 -> PermissionText.Resource(Res.string.event_kind_30817)
                30818 -> PermissionText.Resource(Res.string.event_kind_30818)
                31124 -> PermissionText.Resource(Res.string.event_kind_31124)
                31125 -> PermissionText.Resource(Res.string.event_kind_31125)
                31234 -> PermissionText.Resource(Res.string.event_kind_31234)
                31337 -> PermissionText.Resource(Res.string.event_kind_31337)
                31871 -> PermissionText.Resource(Res.string.event_kind_31871)
                31872 -> PermissionText.Resource(Res.string.event_kind_31872)
                31873 -> PermissionText.Resource(Res.string.event_kind_31873)
                31890 -> PermissionText.Resource(Res.string.event_kind_31890)
                31922 -> PermissionText.Resource(Res.string.event_kind_31922)
                31923 -> PermissionText.Resource(Res.string.event_kind_31923)
                31924 -> PermissionText.Resource(Res.string.event_kind_31924)
                31925 -> PermissionText.Resource(Res.string.event_kind_31925)
                31989 -> PermissionText.Resource(Res.string.event_kind_31989)
                31990 -> PermissionText.Resource(Res.string.event_kind_31990)
                33302 -> PermissionText.Resource(Res.string.event_kind_33302)
                33401 -> PermissionText.Resource(Res.string.event_kind_33401)
                33863 -> PermissionText.Resource(Res.string.event_kind_33863)
                34235 -> PermissionText.Resource(Res.string.event_kind_34235)
                34236 -> PermissionText.Resource(Res.string.event_kind_34236)
                34237 -> PermissionText.Resource(Res.string.event_kind_34237)
                32022 -> PermissionText.Resource(Res.string.event_kind_32022)
                32267 -> PermissionText.Resource(Res.string.event_kind_32267)
                34139 -> PermissionText.Resource(Res.string.event_kind_34139)
                34550 -> PermissionText.Resource(Res.string.event_kind_34550)
                34551 -> PermissionText.Resource(Res.string.event_kind_34551)
                35128 -> PermissionText.Resource(Res.string.event_kind_35128)
                35129 -> PermissionText.Resource(Res.string.event_kind_35129)
                36767 -> PermissionText.Resource(Res.string.event_kind_36767)
                36787 -> PermissionText.Resource(Res.string.event_kind_36787)
                37375 -> PermissionText.Resource(Res.string.event_kind_37375)
                37516 -> PermissionText.Resource(Res.string.event_kind_37516)
                38000 -> PermissionText.Resource(Res.string.event_kind_38000)
                38172 -> PermissionText.Resource(Res.string.event_kind_38172)
                38173 -> PermissionText.Resource(Res.string.event_kind_38173)
                38192 -> PermissionText.Resource(Res.string.event_kind_38192)
                38383 -> PermissionText.Resource(Res.string.event_kind_38383)
                39000 -> PermissionText.Resource(Res.string.event_kind_39000)
                39001 -> PermissionText.Resource(Res.string.event_kind_39001)
                39002 -> PermissionText.Resource(Res.string.event_kind_39002)
                in 39003..39009 -> PermissionText.Resource(Res.string.event_kind_39000_39009)
                39089 -> PermissionText.Resource(Res.string.event_kind_39089)
                39092 -> PermissionText.Resource(Res.string.event_kind_39092)
                39701 -> PermissionText.Resource(Res.string.event_kind_39701)
                10000300 -> PermissionText.Resource(Res.string.event_kind_10000300)
                else -> nipDescription(kind) ?: PermissionText.Resource(Res.string.event_kind, kind.toString())
            }
        }
        "connect" -> if (shouldTranslateConnect) PermissionText.Resource(Res.string.connect) else PermissionText.Resource(Res.string.would_like_your_permission_to_read_your_public_key_and_sign_events_on_your_behalf)
        "nip" -> nipDescription(kind) ?: PermissionText.Resource(Res.string.nip_kind, kind.toString())
        else -> PermissionText.Literal(type)
    }
}

/** The NIP that defines [kind], when Amber has a description for it. */
fun nipDescription(kind: Int?): PermissionText.Resource? = when (kind) {
    1 -> PermissionText.Resource(Res.string.nip_01)
    2 -> PermissionText.Resource(Res.string.nip_02)
    3 -> PermissionText.Resource(Res.string.nip_03)
    4 -> PermissionText.Resource(Res.string.nip_04)
    5 -> PermissionText.Resource(Res.string.nip_05)
    6 -> PermissionText.Resource(Res.string.nip_06)
    7 -> PermissionText.Resource(Res.string.nip_07)
    8 -> PermissionText.Resource(Res.string.nip_08)
    9 -> PermissionText.Resource(Res.string.nip_09)
    10 -> PermissionText.Resource(Res.string.nip_10)
    11 -> PermissionText.Resource(Res.string.nip_11)
    13 -> PermissionText.Resource(Res.string.nip_13)
    14 -> PermissionText.Resource(Res.string.nip_14)
    15 -> PermissionText.Resource(Res.string.nip_15)
    17 -> PermissionText.Resource(Res.string.nip_17)
    18 -> PermissionText.Resource(Res.string.nip_18)
    19 -> PermissionText.Resource(Res.string.nip_19)
    21 -> PermissionText.Resource(Res.string.nip_21)
    23 -> PermissionText.Resource(Res.string.nip_23)
    24 -> PermissionText.Resource(Res.string.nip_24)
    25 -> PermissionText.Resource(Res.string.nip_25)
    26 -> PermissionText.Resource(Res.string.nip_26)
    27 -> PermissionText.Resource(Res.string.nip_27)
    28 -> PermissionText.Resource(Res.string.nip_28)
    29 -> PermissionText.Resource(Res.string.nip_29)
    30 -> PermissionText.Resource(Res.string.nip_30)
    31 -> PermissionText.Resource(Res.string.nip_31)
    32 -> PermissionText.Resource(Res.string.nip_32)
    34 -> PermissionText.Resource(Res.string.nip_34)
    35 -> PermissionText.Resource(Res.string.nip_35)
    36 -> PermissionText.Resource(Res.string.nip_36)
    38 -> PermissionText.Resource(Res.string.nip_38)
    39 -> PermissionText.Resource(Res.string.nip_39)
    40 -> PermissionText.Resource(Res.string.nip_40)
    42 -> PermissionText.Resource(Res.string.nip_42)
    44 -> PermissionText.Resource(Res.string.nip_44)
    45 -> PermissionText.Resource(Res.string.nip_45)
    46 -> PermissionText.Resource(Res.string.nip_46)
    47 -> PermissionText.Resource(Res.string.nip_47)
    48 -> PermissionText.Resource(Res.string.nip_48)
    49 -> PermissionText.Resource(Res.string.nip_49)
    50 -> PermissionText.Resource(Res.string.nip_50)
    51 -> PermissionText.Resource(Res.string.nip_51)
    52 -> PermissionText.Resource(Res.string.nip_52)
    53 -> PermissionText.Resource(Res.string.nip_53)
    54 -> PermissionText.Resource(Res.string.nip_54)
    55 -> PermissionText.Resource(Res.string.nip_55)
    56 -> PermissionText.Resource(Res.string.nip_56)
    57 -> PermissionText.Resource(Res.string.nip_57)
    58 -> PermissionText.Resource(Res.string.nip_58)
    59 -> PermissionText.Resource(Res.string.nip_59)
    65 -> PermissionText.Resource(Res.string.nip_65)
    70 -> PermissionText.Resource(Res.string.nip_70)
    71 -> PermissionText.Resource(Res.string.nip_71)
    72 -> PermissionText.Resource(Res.string.nip_72)
    75 -> PermissionText.Resource(Res.string.nip_75)
    78 -> PermissionText.Resource(Res.string.nip_78)
    84 -> PermissionText.Resource(Res.string.nip_84)
    89 -> PermissionText.Resource(Res.string.nip_89)
    90 -> PermissionText.Resource(Res.string.nip_90)
    92 -> PermissionText.Resource(Res.string.nip_92)
    94 -> PermissionText.Resource(Res.string.nip_94)
    96 -> PermissionText.Resource(Res.string.nip_96)
    98 -> PermissionText.Resource(Res.string.nip_98)
    99 -> PermissionText.Resource(Res.string.nip_99)
    else -> null
}

data class KindNip(val kind: IntRange, val nip: String)

fun Int.containsNip(): Boolean = kindsByNip.any {
    val nip = if (this < 10) {
        this.toString().padStart(2, '0')
    } else {
        this.toString()
    }
    it.nip.contains(nip)
}

fun Int.kindToNip(): String? = kindsByNip.firstOrNull { it.kind.contains(this) }?.nip

val kindsByNip = listOf(
    KindNip(0..2, "01"),
    KindNip(3..3, "02"),
    KindNip(4..4, "04"),
    KindNip(5..5, "09"),
    KindNip(6..6, "18"),
    KindNip(16..16, "18"),
    KindNip(7..7, "25"),
    KindNip(8..8, "58"),
    KindNip(9..12, "29"),
    KindNip(13..13, "59"),
    KindNip(14..14, "17"),
    KindNip(40..44, "28"),
    KindNip(818..818, "54"),
    KindNip(1021..1022, "15"),
    KindNip(1040..1040, "03"),
    KindNip(1059..1059, "59"),
    KindNip(1063..1063, "94"),
    KindNip(1311..1311, "53"),
    KindNip(10312..10312, "53"),
    KindNip(1617..1633, "34"),
    KindNip(1971..1971, "https://github.com/nostrocket/NIPS/blob/main/Problems.md"),
    KindNip(1984..1985, "56"),
    KindNip(2003..2004, "35"),
    KindNip(2022..2022, "https://gitlab.com/1440000bytes/joinstr/-/blob/main/NIP.md"),
    KindNip(4550..4550, "72"),
    KindNip(5000..5999, "90"),
    KindNip(6000..6999, "90"),
    KindNip(7000..7000, "90"),
    KindNip(9000..9030, "29"),
    KindNip(9041..9041, "75"),
    KindNip(9734..9735, "57"),
    KindNip(9802..9802, "84"),
    KindNip(10009..10009, "29"),
    KindNip(10000..10030, "51"),
    KindNip(10050..10050, "17"),
    KindNip(10096..10096, "96"),
    KindNip(13194..13194, "47"),
    KindNip(23194..23195, "47"),
    KindNip(21000..21000, "https://github.com/shocknet/Lightning.Pub/blob/master/proto/autogenerated/client.md"),
    KindNip(22242..22242, "42"),
    KindNip(24133..24133, "46"),
    KindNip(27235..27235, "98"),
    KindNip(30000..30005, "51"),
    KindNip(30008..30009, "58"),
    KindNip(30017..30020, "15"),
    KindNip(30023..30024, "23"),
    KindNip(30078..30078, "78"),
    KindNip(30311..30311, "53"),
    KindNip(30315..30315, "38"),
    KindNip(30402..30403, "99"),
    KindNip(30617..30617, "34"),
    KindNip(30818..30819, "54"),
    KindNip(31890..31890, "https://wikifreedia.xyz/cip-01"),
    KindNip(31922..31925, "52"),
    KindNip(31989..31990, "89"),
    KindNip(34235..34237, "71"),
    KindNip(34550..34550, "72"),
    KindNip(39000..39009, "29"),
    KindNip(15..15, "17"),
    KindNip(21..22, "71"),
    KindNip(30..30, "64"),
    KindNip(62..62, "62"),
    KindNip(78..78, "78"),
    KindNip(1312..1313, "53"),
    KindNip(8000..8001, "43"),
    KindNip(10040..10040, "85"),
    KindNip(10166..10166, "66"),
    KindNip(10317..10317, "34"),
    KindNip(13534..13534, "43"),
    KindNip(17375..17375, "60"),
    KindNip(21059..21059, "59"),
    KindNip(23197..23197, "47"),
    KindNip(28934..28936, "43"),
    KindNip(30006..30007, "51"),
    KindNip(30064..30068, "64"),
    KindNip(30166..30166, "66"),
    KindNip(30312..30313, "53"),
    KindNip(30382..30385, "85"),
    KindNip(34551..34551, "72"),
    KindNip(38000..38000, "87"),
    KindNip(38172..38173, "87"),
    KindNip(31234..31234, "https://github.com/nostr-protocol/nips/pull/1124"),
    KindNip(9467..9467, "https://wikistr.com/tidal-nostr"),
    KindNip(24242..24242, "https://wikistr.com/blossom"),
    KindNip(10063..10063, "https://wikistr.com/blossom"),
    KindNip(30040..30041, "https://wikistr.com/nkbip-01"),
    KindNip(1987..1987, "https://wikistr.com/nkbip-02"),
    KindNip(13302..13302, "https://github.com/concord-protocol/concord"),
    KindNip(20013..20014, "https://github.com/concord-protocol/concord"),
)

val supportedKindNumbers = listOf(
    Permission("sign_event", 0),
    Permission("sign_event", 1),
    Permission("sign_event", 3),
    Permission("sign_event", 4),
    Permission("sign_event", 5),
    Permission("sign_event", 6),
    Permission("sign_event", 7),
    Permission("sign_event", 8),
    Permission("sign_event", 9),
    Permission("sign_event", 10),
    Permission("sign_event", 11),
    Permission("sign_event", 12),
    Permission("sign_event", 13),
    Permission("sign_event", 15),
    Permission("sign_event", 16),
    Permission("sign_event", 17),
    Permission("sign_event", 20),
    Permission("sign_event", 21),
    Permission("sign_event", 22),
    Permission("sign_event", 24),
    Permission("sign_event", 30),
    Permission("sign_event", 40),
    Permission("sign_event", 41),
    Permission("sign_event", 42),
    Permission("sign_event", 43),
    Permission("sign_event", 44),
    Permission("sign_event", 54),
    Permission("sign_event", 62),
    Permission("sign_event", 64),
    Permission("sign_event", 78),
    Permission("sign_event", 82),
    Permission("sign_event", 443),
    Permission("sign_event", 444),
    Permission("sign_event", 445),
    Permission("sign_event", 446),
    Permission("sign_event", 447),
    Permission("sign_event", 448),
    Permission("sign_event", 449),
    Permission("sign_event", 450),
    Permission("sign_event", 451),
    Permission("sign_event", 452),
    Permission("sign_event", 1009),
    Permission("sign_event", 1200),
    Permission("sign_event", 1210),
    Permission("sign_event", 30443),
    Permission("sign_event", 818),
    Permission("sign_event", 1010),
    Permission("sign_event", 1018),
    Permission("sign_event", 1021),
    Permission("sign_event", 1022),
    Permission("sign_event", 1040),
    Permission("sign_event", 1059),
    Permission("sign_event", 1063),
    Permission("sign_event", 1064),
    Permission("sign_event", 1065),
    Permission("sign_event", 1068),
    Permission("sign_event", 1111),
    Permission("sign_event", 1163),
    Permission("sign_event", 1222),
    Permission("sign_event", 1244),
    Permission("sign_event", 1301),
    Permission("sign_event", 1311),
    Permission("sign_event", 1312),
    Permission("sign_event", 1313),
    Permission("sign_event", 1315),
    Permission("sign_event", 1316),
    Permission("sign_event", 1337),
    Permission("sign_event", 1617),
    Permission("sign_event", 1618),
    Permission("sign_event", 1619),
    Permission("sign_event", 1621),
    Permission("sign_event", 1622),
    Permission("sign_event", 1630),
    Permission("sign_event", 1631),
    Permission("sign_event", 1632),
    Permission("sign_event", 1633),
    Permission("sign_event", 1808),
    Permission("sign_event", 1971),
    Permission("sign_event", 1984),
    Permission("sign_event", 1985),
    Permission("sign_event", 1986),
    Permission("sign_event", 1987),
    Permission("sign_event", 2003),
    Permission("sign_event", 2004),
    Permission("sign_event", 2022),
    Permission("sign_event", 2473),
    Permission("sign_event", 4312),
    Permission("sign_event", 8000),
    Permission("sign_event", 8001),
    Permission("sign_event", 8333),
    Permission("sign_event", 9736),
    Permission("sign_event", 10008),
    Permission("sign_event", 10011),
    Permission("sign_event", 10017),
    Permission("sign_event", 10019),
    Permission("sign_event", 10023),
    Permission("sign_event", 10040),
    Permission("sign_event", 10054),
    Permission("sign_event", 10058),
    Permission("sign_event", 10064),
    Permission("sign_event", 10081),
    Permission("sign_event", 10090),
    Permission("sign_event", 10112),
    Permission("sign_event", 10133),
    Permission("sign_event", 10154),
    Permission("sign_event", 10166),
    Permission("sign_event", 10317),
    Permission("sign_event", 11871),
    Permission("sign_event", 12022),
    Permission("sign_event", 12473),
    Permission("sign_event", 13534),
    Permission("sign_event", 15129),
    Permission("sign_event", 17375),
    Permission("sign_event", 21001),
    Permission("sign_event", 21002),
    Permission("sign_event", 21003),
    Permission("sign_event", 21059),
    Permission("sign_event", 23197),
    Permission("sign_event", 23333),
    Permission("sign_event", 23903),
    Permission("sign_event", 25051),
    Permission("sign_event", 25052),
    Permission("sign_event", 25053),
    Permission("sign_event", 25054),
    Permission("sign_event", 25055),
    Permission("sign_event", 28934),
    Permission("sign_event", 28935),
    Permission("sign_event", 28936),
    Permission("sign_event", 30005),
    Permission("sign_event", 30006),
    Permission("sign_event", 30007),
    Permission("sign_event", 30053),
    Permission("sign_event", 30054),
    Permission("sign_event", 30055),
    Permission("sign_event", 30064),
    Permission("sign_event", 30065),
    Permission("sign_event", 30066),
    Permission("sign_event", 30067),
    Permission("sign_event", 30068),
    Permission("sign_event", 30166),
    Permission("sign_event", 30296),
    Permission("sign_event", 30297),
    Permission("sign_event", 30298),
    Permission("sign_event", 30312),
    Permission("sign_event", 30313),
    Permission("sign_event", 30382),
    Permission("sign_event", 30383),
    Permission("sign_event", 30384),
    Permission("sign_event", 30385),
    Permission("sign_event", 30617),
    Permission("sign_event", 30817),
    Permission("sign_event", 30818),
    Permission("sign_event", 31337),
    Permission("sign_event", 31871),
    Permission("sign_event", 31872),
    Permission("sign_event", 31873),
    Permission("sign_event", 31890),
    Permission("sign_event", 32022),
    Permission("sign_event", 4550),
    Permission("sign_event", 5000),
    Permission("sign_event", 5999),
    Permission("sign_event", 6000),
    Permission("sign_event", 6999),
    Permission("sign_event", 7000),
    Permission("sign_event", 7374),
    Permission("sign_event", 7375),
    Permission("sign_event", 7376),
    Permission("sign_event", 9000),
    Permission("sign_event", 9001),
    Permission("sign_event", 9002),
    Permission("sign_event", 9003),
    Permission("sign_event", 9004),
    Permission("sign_event", 9005),
    Permission("sign_event", 9006),
    Permission("sign_event", 9007),
    Permission("sign_event", 9021),
    Permission("sign_event", 9000),
    Permission("sign_event", 9001),
    Permission("sign_event", 9002),
    Permission("sign_event", 9003),
    Permission("sign_event", 9004),
    Permission("sign_event", 9005),
    Permission("sign_event", 9006),
    Permission("sign_event", 9007),
    Permission("sign_event", 9021),
    Permission("sign_event", 9000),
    Permission("sign_event", 9001),
    Permission("sign_event", 9002),
    Permission("sign_event", 9003),
    Permission("sign_event", 9004),
    Permission("sign_event", 9005),
    Permission("sign_event", 9006),
    Permission("sign_event", 9007),
    Permission("sign_event", 9021),
    Permission("sign_event", 9000),
    Permission("sign_event", 9001),
    Permission("sign_event", 9002),
    Permission("sign_event", 9003),
    Permission("sign_event", 9004),
    Permission("sign_event", 9005),
    Permission("sign_event", 9006),
    Permission("sign_event", 9007),
    Permission("sign_event", 9021),
    Permission("sign_event", 9041),
    Permission("sign_event", 9321),
    Permission("sign_event", 9467),
    Permission("sign_event", 9734),
    Permission("sign_event", 9735),
    Permission("sign_event", 9802),
    Permission("sign_event", 10000),
    Permission("sign_event", 10001),
    Permission("sign_event", 10002),
    Permission("sign_event", 10003),
    Permission("sign_event", 10004),
    Permission("sign_event", 10005),
    Permission("sign_event", 10006),
    Permission("sign_event", 10007),
    Permission("sign_event", 10009),
    Permission("sign_event", 10013),
    Permission("sign_event", 10015),
    Permission("sign_event", 10018),
    Permission("sign_event", 10020),
    Permission("sign_event", 10030),
    Permission("sign_event", 10050),
    Permission("sign_event", 10051),
    Permission("sign_event", 10063),
    Permission("sign_event", 10096),
    Permission("sign_event", 10312),
    Permission("sign_event", 13194),
    Permission("sign_event", 13302),
    Permission("sign_event", 20013),
    Permission("sign_event", 20014),
    Permission("sign_event", 21000),
    Permission("sign_event", 22242),
    Permission("sign_event", 23194),
    Permission("sign_event", 23195),
    Permission("sign_event", 24133),
    Permission("sign_event", 24242),
    Permission("sign_event", 27235),
    Permission("sign_event", 22456),
    Permission("sign_event", 30000),
    Permission("sign_event", 30001),
    Permission("sign_event", 30002),
    Permission("sign_event", 30003),
    Permission("sign_event", 30004),
    Permission("sign_event", 30008),
    Permission("sign_event", 30009),
    Permission("sign_event", 30015),
    Permission("sign_event", 30017),
    Permission("sign_event", 30018),
    Permission("sign_event", 30019),
    Permission("sign_event", 30020),
    Permission("sign_event", 30023),
    Permission("sign_event", 30024),
    Permission("sign_event", 30030),
    Permission("sign_event", 30040),
    Permission("sign_event", 30041),
    Permission("sign_event", 30063),
    Permission("sign_event", 30078),
    Permission("sign_event", 30311),
    Permission("sign_event", 30315),
    Permission("sign_event", 30402),
    Permission("sign_event", 30403),
    Permission("sign_event", 31234),
    Permission("sign_event", 31922),
    Permission("sign_event", 31923),
    Permission("sign_event", 31924),
    Permission("sign_event", 31925),
    Permission("sign_event", 31989),
    Permission("sign_event", 31990),
    Permission("sign_event", 33401),
    Permission("sign_event", 33863),
    Permission("sign_event", 34235),
    Permission("sign_event", 34236),
    Permission("sign_event", 34550),
    Permission("sign_event", 34551),
    Permission("sign_event", 35128),
    Permission("sign_event", 35129),
    Permission("sign_event", 37375),
    Permission("sign_event", 38000),
    Permission("sign_event", 38172),
    Permission("sign_event", 38173),
    Permission("sign_event", 38192),
    Permission("sign_event", 38383),
    Permission("sign_event", 39000),
    Permission("sign_event", 39001),
    Permission("sign_event", 39002),
    Permission("sign_event", 39089),
    Permission("sign_event", 39092),
    Permission("sign_event", 10000300),
    *(39003..39009).toList().map { Permission("sign_event", it) }.toTypedArray(),
    *(9000..9030).toList().map { Permission("sign_event", it) }.toTypedArray(),
    Permission("connect", null),
    Permission("nip04_encrypt", null),
    Permission("nip04_decrypt", null),
    Permission("nip44_decrypt", null),
    Permission("nip44_encrypt", null),
    Permission("decrypt_zap_event", null),
    Permission("encrypt_clear_text", null),
    Permission("decrypt_clear_text", null),
    Permission("encrypt_event", null),
    Permission("decrypt_event", null),
    Permission("encrypt_tag_array", null),
    Permission("decrypt_tag_array", null),
    Permission("get_public_key", null),
)

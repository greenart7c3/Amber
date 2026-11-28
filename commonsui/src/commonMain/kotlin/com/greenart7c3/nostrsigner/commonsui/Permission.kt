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
    // Kinds last checked against https://github.com/nostr-protocol/nips/tree/14ec14dac92d891715239bfb83ed1c9bdf2ac382
    fun description(shouldTranslateConnect: Boolean = false): PermissionText = when (val normalized = type.trim().lowercase()) {
        "sign_event" -> eventKindDescription(kind)
        "connect" -> if (shouldTranslateConnect) PermissionText.Resource(Res.string.connect) else PermissionText.Resource(Res.string.would_like_your_permission_to_read_your_public_key_and_sign_events_on_your_behalf)
        "nip" -> nipDescription(kind) ?: PermissionText.Resource(Res.string.nip_kind, kind.toString())
        else -> kindIndependentTypeStrings[normalized]?.let { PermissionText.Resource(it) } ?: PermissionText.Literal(type)
    }
}

private val kindIndependentTypeStrings: Map<String, StringResource> = mapOf(
    "get_public_key" to Res.string.read_your_public_key,
    "nip04_encrypt" to Res.string.encrypt_data_using_nip_4,
    "nip04_decrypt" to Res.string.decrypt_data_using_nip_4,
    "nip44_decrypt" to Res.string.decrypt_data_using_nip_44,
    "nip44_encrypt" to Res.string.encrypt_data_using_nip_44,
    "nip44_v3_encrypt" to Res.string.encrypt_data_using_nip_44,
    "nip44v3_encrypt" to Res.string.encrypt_data_using_nip_44,
    "nip44_v3_decrypt" to Res.string.decrypt_data_using_nip_44,
    "nip44v3_decrypt" to Res.string.decrypt_data_using_nip_44,
    "ping" to Res.string.ping,
    "decrypt_zap_event" to Res.string.decrypt_private_zaps,
    "sign_psbt" to Res.string.sign_psbt,
    "encrypt_clear_text" to Res.string.encrypt_clear_text,
    "decrypt_clear_text" to Res.string.decrypt_clear_text,
    "encrypt_event" to Res.string.encrypt_event,
    "decrypt_event" to Res.string.decrypt_event,
    "encrypt_tag_array" to Res.string.encrypt_tag_array,
    "decrypt_tag_array" to Res.string.decrypt_tag_array,
)

/** The NIP that defines [kind], when Amber has a description for it. */
fun nipDescription(kind: Int?): PermissionText.Resource? = kind?.let { nipStrings[it] }?.let { PermissionText.Resource(it) }

private fun eventKindDescription(kind: Int?): PermissionText {
    val resource = kind?.let { eventKindStrings[it] ?: eventKindRangeString(it) }
    return resource?.let { PermissionText.Resource(it) }
        ?: nipDescription(kind)
        ?: PermissionText.Resource(Res.string.event_kind, kind.toString())
}

private fun eventKindRangeString(kind: Int): StringResource? = when (kind) {
    in 1630..1633 -> Res.string.event_kind_1630_1633
    in 5000..5999 -> Res.string.event_kind_5000_5999
    in 6000..6999 -> Res.string.event_kind_6000_6999
    in 9000..9030 -> Res.string.event_kind_9000_9030
    in 39003..39009 -> Res.string.event_kind_39000_39009
    else -> null
}

private val nipStrings: Map<Int, StringResource> = mapOf(
    1 to Res.string.nip_01,
    2 to Res.string.nip_02,
    3 to Res.string.nip_03,
    4 to Res.string.nip_04,
    5 to Res.string.nip_05,
    6 to Res.string.nip_06,
    7 to Res.string.nip_07,
    8 to Res.string.nip_08,
    9 to Res.string.nip_09,
    10 to Res.string.nip_10,
    11 to Res.string.nip_11,
    13 to Res.string.nip_13,
    14 to Res.string.nip_14,
    15 to Res.string.nip_15,
    17 to Res.string.nip_17,
    18 to Res.string.nip_18,
    19 to Res.string.nip_19,
    21 to Res.string.nip_21,
    23 to Res.string.nip_23,
    24 to Res.string.nip_24,
    25 to Res.string.nip_25,
    26 to Res.string.nip_26,
    27 to Res.string.nip_27,
    28 to Res.string.nip_28,
    29 to Res.string.nip_29,
    30 to Res.string.nip_30,
    31 to Res.string.nip_31,
    32 to Res.string.nip_32,
    34 to Res.string.nip_34,
    35 to Res.string.nip_35,
    36 to Res.string.nip_36,
    38 to Res.string.nip_38,
    39 to Res.string.nip_39,
    40 to Res.string.nip_40,
    42 to Res.string.nip_42,
    44 to Res.string.nip_44,
    45 to Res.string.nip_45,
    46 to Res.string.nip_46,
    47 to Res.string.nip_47,
    48 to Res.string.nip_48,
    49 to Res.string.nip_49,
    50 to Res.string.nip_50,
    51 to Res.string.nip_51,
    52 to Res.string.nip_52,
    53 to Res.string.nip_53,
    54 to Res.string.nip_54,
    55 to Res.string.nip_55,
    56 to Res.string.nip_56,
    57 to Res.string.nip_57,
    58 to Res.string.nip_58,
    59 to Res.string.nip_59,
    65 to Res.string.nip_65,
    70 to Res.string.nip_70,
    71 to Res.string.nip_71,
    72 to Res.string.nip_72,
    75 to Res.string.nip_75,
    78 to Res.string.nip_78,
    84 to Res.string.nip_84,
    89 to Res.string.nip_89,
    90 to Res.string.nip_90,
    92 to Res.string.nip_92,
    94 to Res.string.nip_94,
    96 to Res.string.nip_96,
    98 to Res.string.nip_98,
    99 to Res.string.nip_99,
)

private val eventKindStrings: Map<Int, StringResource> = mapOf(
    0 to Res.string.event_kind_0, 1 to Res.string.event_kind_1, 3 to Res.string.event_kind_3, 4 to Res.string.event_kind_4, 5 to Res.string.event_kind_5, 6 to Res.string.event_kind_6,
    7 to Res.string.event_kind_7, 8 to Res.string.event_kind_8, 9 to Res.string.event_kind_9, 10 to Res.string.event_kind_10, 11 to Res.string.event_kind_11, 12 to Res.string.event_kind_12,
    13 to Res.string.event_kind_13, 14 to Res.string.event_kind_14, 15 to Res.string.event_kind_15, 16 to Res.string.event_kind_16, 17 to Res.string.event_kind_17, 20 to Res.string.event_kind_20,
    21 to Res.string.event_kind_21, 22 to Res.string.event_kind_22, 24 to Res.string.event_kind_24, 30 to Res.string.event_kind_30, 40 to Res.string.event_kind_40, 41 to Res.string.event_kind_41,
    42 to Res.string.event_kind_42, 43 to Res.string.event_kind_43, 44 to Res.string.event_kind_44, 54 to Res.string.event_kind_54, 62 to Res.string.event_kind_62, 64 to Res.string.event_kind_64,
    78 to Res.string.event_kind_78, 82 to Res.string.event_kind_82, 178 to Res.string.event_kind_178, 443 to Res.string.event_kind_443, 444 to Res.string.event_kind_444, 445 to Res.string.event_kind_445,
    446 to Res.string.event_kind_446, 447 to Res.string.event_kind_447, 448 to Res.string.event_kind_448, 449 to Res.string.event_kind_449, 450 to Res.string.event_kind_450, 451 to Res.string.event_kind_451,
    452 to Res.string.event_kind_452, 818 to Res.string.event_kind_818, 1009 to Res.string.event_kind_1009, 1010 to Res.string.event_kind_1010, 1011 to Res.string.event_kind_1011, 1018 to Res.string.event_kind_1018,
    1021 to Res.string.event_kind_1021, 1022 to Res.string.event_kind_1022, 1040 to Res.string.event_kind_1040, 1059 to Res.string.event_kind_1059, 1063 to Res.string.event_kind_1063, 1064 to Res.string.event_kind_1064,
    1065 to Res.string.event_kind_1065, 1068 to Res.string.event_kind_1068, 1111 to Res.string.event_kind_1111, 1163 to Res.string.event_kind_1163, 1200 to Res.string.event_kind_1200, 1201 to Res.string.event_kind_1201,
    1202 to Res.string.event_kind_1202, 1209 to Res.string.event_kind_1209, 1210 to Res.string.event_kind_1210, 1222 to Res.string.event_kind_1222, 1244 to Res.string.event_kind_1244, 1301 to Res.string.event_kind_1301,
    1311 to Res.string.event_kind_1311, 1312 to Res.string.event_kind_1312, 1313 to Res.string.event_kind_1313, 1315 to Res.string.event_kind_1315, 1316 to Res.string.event_kind_1316, 1337 to Res.string.event_kind_1337,
    1617 to Res.string.event_kind_1617, 1618 to Res.string.event_kind_1618, 1619 to Res.string.event_kind_1619, 1621 to Res.string.event_kind_1621, 1622 to Res.string.event_kind_1622, 1808 to Res.string.event_kind_1808,
    1971 to Res.string.event_kind_1971, 1984 to Res.string.event_kind_1984, 1985 to Res.string.event_kind_1985, 1986 to Res.string.event_kind_1986, 1987 to Res.string.event_kind_1987, 3079 to Res.string.event_kind_3079,
    3080 to Res.string.event_kind_3080, 3083 to Res.string.event_kind_3083, 4242 to Res.string.event_kind_4242, 4891 to Res.string.event_kind_4891, 20001 to Res.string.event_kind_20001, 20002 to Res.string.event_kind_20002,
    2003 to Res.string.event_kind_2003, 2004 to Res.string.event_kind_2004, 2022 to Res.string.event_kind_2022, 2473 to Res.string.event_kind_2473, 22236 to Res.string.event_kind_22236, 25742 to Res.string.event_kind_25742,
    3063 to Res.string.event_kind_3063, 3314 to Res.string.event_kind_3314, 3367 to Res.string.event_kind_3367, 25910 to Res.string.event_kind_25910, 34238 to Res.string.event_kind_34238, 39307 to Res.string.event_kind_39307,
    40003 to Res.string.event_kind_40003, 41010 to Res.string.event_kind_41010, 4223 to Res.string.event_kind_4223, 4312 to Res.string.event_kind_4312, 4454 to Res.string.event_kind_4454, 4455 to Res.string.event_kind_4455,
    45001 to Res.string.event_kind_45001, 45002 to Res.string.event_kind_45002, 45003 to Res.string.event_kind_45003, 4550 to Res.string.event_kind_4550, 7000 to Res.string.event_kind_7000, 7374 to Res.string.event_kind_7374,
    7375 to Res.string.event_kind_7375, 7376 to Res.string.event_kind_7376, 7516 to Res.string.event_kind_7516, 8000 to Res.string.event_kind_8000, 8001 to Res.string.event_kind_8001, 8211 to Res.string.event_kind_8211,
    8333 to Res.string.event_kind_8333, 9000 to Res.string.event_kind_9000, 9001 to Res.string.event_kind_9001, 9002 to Res.string.event_kind_9002, 9003 to Res.string.event_kind_9003, 9004 to Res.string.event_kind_9004,
    9005 to Res.string.event_kind_9005, 9006 to Res.string.event_kind_9006, 9007 to Res.string.event_kind_9007, 9008 to Res.string.event_kind_9008, 9009 to Res.string.event_kind_9009, 9010 to Res.string.event_kind_9010,
    9021 to Res.string.event_kind_9021, 9022 to Res.string.event_kind_9022, 9041 to Res.string.event_kind_9041, 9321 to Res.string.event_kind_9321, 9450 to Res.string.event_kind_9450, 9467 to Res.string.event_kind_9467,
    9733 to Res.string.event_kind_9733, 9734 to Res.string.event_kind_9734, 9735 to Res.string.event_kind_9735, 9736 to Res.string.event_kind_9736, 9802 to Res.string.event_kind_9802, 9840 to Res.string.event_kind_9840,
    9843 to Res.string.event_kind_9843, 9844 to Res.string.event_kind_9844, 10000 to Res.string.event_kind_10000, 10001 to Res.string.event_kind_10001, 10002 to Res.string.event_kind_10002, 10003 to Res.string.event_kind_10003,
    10004 to Res.string.event_kind_10004, 10005 to Res.string.event_kind_10005, 10006 to Res.string.event_kind_10006, 10007 to Res.string.event_kind_10007, 10008 to Res.string.event_kind_10008, 10009 to Res.string.event_kind_10009,
    10011 to Res.string.event_kind_10011, 10012 to Res.string.event_kind_10012, 10013 to Res.string.event_kind_10013, 10015 to Res.string.event_kind_10015, 10017 to Res.string.event_kind_10017, 10018 to Res.string.event_kind_10018,
    10019 to Res.string.event_kind_10019, 10020 to Res.string.event_kind_10020, 10023 to Res.string.event_kind_10023, 10030 to Res.string.event_kind_10030, 10040 to Res.string.event_kind_10040, 10044 to Res.string.event_kind_10044,
    10050 to Res.string.event_kind_10050, 10051 to Res.string.event_kind_10051, 10054 to Res.string.event_kind_10054, 10058 to Res.string.event_kind_10058, 10063 to Res.string.event_kind_10063, 10064 to Res.string.event_kind_10064,
    10081 to Res.string.event_kind_10081, 10086 to Res.string.event_kind_10086, 10087 to Res.string.event_kind_10087, 10088 to Res.string.event_kind_10088, 10089 to Res.string.event_kind_10089, 10090 to Res.string.event_kind_10090,
    10096 to Res.string.event_kind_10096, 10101 to Res.string.event_kind_10101, 10102 to Res.string.event_kind_10102, 10112 to Res.string.event_kind_10112, 10133 to Res.string.event_kind_10133, 10154 to Res.string.event_kind_10154,
    10166 to Res.string.event_kind_10166, 10312 to Res.string.event_kind_10312, 10317 to Res.string.event_kind_10317, 10443 to Res.string.event_kind_10443, 11125 to Res.string.event_kind_11125, 11871 to Res.string.event_kind_11871,
    12022 to Res.string.event_kind_12022, 12473 to Res.string.event_kind_12473, 13194 to Res.string.event_kind_13194, 13302 to Res.string.event_kind_13302, 13303 to Res.string.event_kind_13303, 13534 to Res.string.event_kind_13534,
    14919 to Res.string.event_kind_14919, 14920 to Res.string.event_kind_14920, 14921 to Res.string.event_kind_14921, 15128 to Res.string.event_kind_15128, 15129 to Res.string.event_kind_15129, 16158 to Res.string.event_kind_16158,
    16767 to Res.string.event_kind_16767, 16769 to Res.string.event_kind_16769, 17375 to Res.string.event_kind_17375, 20013 to Res.string.event_kind_20013, 20014 to Res.string.event_kind_20014, 21000 to Res.string.event_kind_21000,
    21001 to Res.string.event_kind_21001, 21002 to Res.string.event_kind_21002, 21003 to Res.string.event_kind_21003, 21059 to Res.string.event_kind_21059, 22242 to Res.string.event_kind_22242, 23194 to Res.string.event_kind_23194,
    23195 to Res.string.event_kind_23195, 23197 to Res.string.event_kind_23197, 23333 to Res.string.event_kind_23333, 24450 to Res.string.event_kind_24450, 23903 to Res.string.event_kind_23903, 24133 to Res.string.event_kind_24133,
    24242 to Res.string.event_kind_24242, 25050 to Res.string.event_kind_25050, 25051 to Res.string.event_kind_25051, 25052 to Res.string.event_kind_25052, 25053 to Res.string.event_kind_25053, 25054 to Res.string.event_kind_25054,
    25055 to Res.string.event_kind_25055, 27235 to Res.string.event_kind_27235, 27240 to Res.string.event_kind_27240, 27241 to Res.string.event_kind_27241, 22456 to Res.string.event_kind_22456, 28934 to Res.string.event_kind_28934, 28935 to Res.string.event_kind_28935, 28936 to Res.string.event_kind_28936,
    30000 to Res.string.event_kind_30000, 30001 to Res.string.event_kind_30001, 30002 to Res.string.event_kind_30002, 30003 to Res.string.event_kind_30003, 30004 to Res.string.event_kind_30004, 30005 to Res.string.event_kind_30005,
    30006 to Res.string.event_kind_30006, 30007 to Res.string.event_kind_30007, 30008 to Res.string.event_kind_30008, 30009 to Res.string.event_kind_30009, 30015 to Res.string.event_kind_30015, 30017 to Res.string.event_kind_30017,
    30018 to Res.string.event_kind_30018, 30019 to Res.string.event_kind_30019, 30020 to Res.string.event_kind_30020, 30023 to Res.string.event_kind_30023, 30024 to Res.string.event_kind_30024, 30030 to Res.string.event_kind_30030,
    30040 to Res.string.event_kind_30040, 30041 to Res.string.event_kind_30041, 30053 to Res.string.event_kind_30053, 30054 to Res.string.event_kind_30054, 30055 to Res.string.event_kind_30055, 30063 to Res.string.event_kind_30063,
    30064 to Res.string.event_kind_30064, 30065 to Res.string.event_kind_30065, 30066 to Res.string.event_kind_30066, 30067 to Res.string.event_kind_30067, 30068 to Res.string.event_kind_30068, 30078 to Res.string.event_kind_30078,
    30166 to Res.string.event_kind_30166, 30267 to Res.string.event_kind_30267, 30296 to Res.string.event_kind_30296, 30297 to Res.string.event_kind_30297, 30298 to Res.string.event_kind_30298, 30311 to Res.string.event_kind_30311,
    30312 to Res.string.event_kind_30312, 30313 to Res.string.event_kind_30313, 30315 to Res.string.event_kind_30315, 30382 to Res.string.event_kind_30382, 30383 to Res.string.event_kind_30383, 30384 to Res.string.event_kind_30384,
    30385 to Res.string.event_kind_30385, 30402 to Res.string.event_kind_30402, 30403 to Res.string.event_kind_30403, 30443 to Res.string.event_kind_30443, 30617 to Res.string.event_kind_30617, 30618 to Res.string.event_kind_30618,
    30624 to Res.string.event_kind_30624, 30817 to Res.string.event_kind_30817, 30818 to Res.string.event_kind_30818, 31124 to Res.string.event_kind_31124, 31125 to Res.string.event_kind_31125, 31234 to Res.string.event_kind_31234,
    31337 to Res.string.event_kind_31337, 31871 to Res.string.event_kind_31871, 31872 to Res.string.event_kind_31872, 31873 to Res.string.event_kind_31873, 31890 to Res.string.event_kind_31890, 31922 to Res.string.event_kind_31922,
    31923 to Res.string.event_kind_31923, 31924 to Res.string.event_kind_31924, 31925 to Res.string.event_kind_31925, 31989 to Res.string.event_kind_31989, 31990 to Res.string.event_kind_31990, 33302 to Res.string.event_kind_33302,
    33401 to Res.string.event_kind_33401, 33863 to Res.string.event_kind_33863, 34235 to Res.string.event_kind_34235, 34236 to Res.string.event_kind_34236, 34237 to Res.string.event_kind_34237, 32022 to Res.string.event_kind_32022,
    32267 to Res.string.event_kind_32267, 34139 to Res.string.event_kind_34139, 34550 to Res.string.event_kind_34550, 34551 to Res.string.event_kind_34551, 35128 to Res.string.event_kind_35128, 35129 to Res.string.event_kind_35129,
    36767 to Res.string.event_kind_36767, 36787 to Res.string.event_kind_36787, 37375 to Res.string.event_kind_37375, 37516 to Res.string.event_kind_37516, 38000 to Res.string.event_kind_38000, 38172 to Res.string.event_kind_38172,
    38173 to Res.string.event_kind_38173, 38192 to Res.string.event_kind_38192, 38383 to Res.string.event_kind_38383, 39000 to Res.string.event_kind_39000, 39001 to Res.string.event_kind_39001, 39002 to Res.string.event_kind_39002,
    39089 to Res.string.event_kind_39089, 39092 to Res.string.event_kind_39092, 39701 to Res.string.event_kind_39701, 10000300 to Res.string.event_kind_10000300,
)

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

/** What "approve basic actions" (sign policy 0) grants a new connection, in both apps. */
val basicPermissions = listOf(
    Permission("get_public_key", null),
    Permission("encrypt_clear_text", null),
    Permission("decrypt_clear_text", null),
    Permission("encrypt_event", null),
    Permission("decrypt_event", null),
    Permission("encrypt_tag_array", null),
    Permission("decrypt_tag_array", null),
    Permission("decrypt_zap_event", null),
    Permission("sign_event", 0),
    Permission("sign_event", 1),
    Permission("sign_event", 3),
    Permission("sign_event", 4),
    Permission("sign_event", 5),
    Permission("sign_event", 6),
    Permission("sign_event", 7),
    Permission("sign_event", 9734),
    Permission("sign_event", 9735),
    Permission("sign_event", 10000),
    Permission("sign_event", 10002),
    Permission("sign_event", 10003),
    Permission("sign_event", 10013),
    Permission("sign_event", 31234),
    Permission("sign_event", 30078),
    Permission("sign_event", 22242),
    Permission("sign_event", 27235),
    Permission("sign_event", 30023),
)

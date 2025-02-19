package com.greenart7c3.nostrsigner.models

import android.content.Context
import com.greenart7c3.nostrsigner.R

data class Permission(
    val type: String,
    val kind: Int?,
    var checked: Boolean = true,
) {
    // LAST_COMMIT_CHECKED = "14ec14dac92d891715239bfb83ed1c9bdf2ac382"
    fun toLocalizedString(context: Context): String {
        return when (type) {
            "sign_message" -> context.getString(R.string.sign_message)
            "get_public_key" -> context.getString(R.string.read_your_public_key)
            "nip04_encrypt" -> {
                context.getString(R.string.encrypt_data_using_nip_4)
            }
            "nip04_decrypt" -> {
                context.getString(R.string.decrypt_data_using_nip_4)
            }
            "nip44_decrypt" -> {
                context.getString(R.string.decrypt_data_using_nip_44)
            }
            "nip44_encrypt" -> {
                context.getString(R.string.encrypt_data_using_nip_44)
            }
            "decrypt_zap_event" -> {
                context.getString(R.string.decrypt_private_zaps)
            }
            "sign_event" -> {
                when (kind) {
                    0 -> context.getString(R.string.event_kind_0)
                    1 -> context.getString(R.string.event_kind_1)
                    3 -> context.getString(R.string.event_kind_3)
                    4 -> context.getString(R.string.event_kind_4)
                    5 -> context.getString(R.string.event_kind_5)
                    6 -> context.getString(R.string.event_kind_6)
                    7 -> context.getString(R.string.event_kind_7)
                    8 -> context.getString(R.string.event_kind_8)
                    9 -> context.getString(R.string.event_kind_9)
                    10 -> context.getString(R.string.event_kind_10)
                    11 -> context.getString(R.string.event_kind_11)
                    12 -> context.getString(R.string.event_kind_12)
                    13 -> context.getString(R.string.event_kind_13)
                    16 -> context.getString(R.string.event_kind_16)
                    17 -> context.getString(R.string.event_kind_17)
                    20 -> context.getString(R.string.event_kind_20)
                    40 -> context.getString(R.string.event_kind_40)
                    41 -> context.getString(R.string.event_kind_41)
                    42 -> context.getString(R.string.event_kind_42)
                    43 -> context.getString(R.string.event_kind_43)
                    44 -> context.getString(R.string.event_kind_44)
                    64 -> context.getString(R.string.event_kind_64)
                    818 -> context.getString(R.string.event_kind_818)
                    1018 -> context.getString(R.string.event_kind_1018)
                    1021 -> context.getString(R.string.event_kind_1021)
                    1022 -> context.getString(R.string.event_kind_1022)
                    1040 -> context.getString(R.string.event_kind_1040)
                    1059 -> context.getString(R.string.event_kind_1059)
                    1063 -> context.getString(R.string.event_kind_1063)
                    1068 -> context.getString(R.string.event_kind_1068)
                    1111 -> context.getString(R.string.event_kind_1111)
                    1311 -> context.getString(R.string.event_kind_1311)
                    1617 -> context.getString(R.string.event_kind_1617)
                    1621 -> context.getString(R.string.event_kind_1621)
                    1622 -> context.getString(R.string.event_kind_1622)
                    in 1630..1633 -> context.getString(R.string.event_kind_1630_1633)
                    1971 -> context.getString(R.string.event_kind_1971)
                    1984 -> context.getString(R.string.event_kind_1984)
                    1985 -> context.getString(R.string.event_kind_1985)
                    1986 -> context.getString(R.string.event_kind_1986)
                    1987 -> context.getString(R.string.event_kind_1987)
                    2003 -> context.getString(R.string.event_kind_2003)
                    2004 -> context.getString(R.string.event_kind_2004)
                    2022 -> context.getString(R.string.event_kind_2022)
                    4550 -> context.getString(R.string.event_kind_4550)
                    in 5000..5999 -> context.getString(R.string.event_kind_5000_5999)
                    in 6000..6999 -> context.getString(R.string.event_kind_6000_6999)
                    7000 -> context.getString(R.string.event_kind_7000)
                    7374 -> context.getString(R.string.event_kind_7374)
                    7375 -> context.getString(R.string.event_kind_7375)
                    7376 -> context.getString(R.string.event_kind_7376)
                    9000 -> context.getString(R.string.event_kind_9000)
                    9001 -> context.getString(R.string.event_kind_9001)
                    9002 -> context.getString(R.string.event_kind_9002)
                    9003 -> context.getString(R.string.event_kind_9003)
                    9004 -> context.getString(R.string.event_kind_9004)
                    9005 -> context.getString(R.string.event_kind_9005)
                    9006 -> context.getString(R.string.event_kind_9006)
                    9007 -> context.getString(R.string.event_kind_9007)
                    9021 -> context.getString(R.string.event_kind_9021)
                    in 9000..9030 -> context.getString(R.string.event_kind_9000_9030)
                    9041 -> context.getString(R.string.event_kind_9041)
                    9321 -> context.getString(R.string.event_kind_9321)
                    9467 -> context.getString(R.string.event_kind_9467)
                    9734 -> context.getString(R.string.event_kind_9734)
                    9735 -> context.getString(R.string.event_kind_9735)
                    9802 -> context.getString(R.string.event_kind_9802)
                    10000 -> context.getString(R.string.event_kind_10000)
                    10001 -> context.getString(R.string.event_kind_10001)
                    10002 -> context.getString(R.string.event_kind_10002)
                    10003 -> context.getString(R.string.event_kind_10003)
                    10004 -> context.getString(R.string.event_kind_10004)
                    10005 -> context.getString(R.string.event_kind_10005)
                    10006 -> context.getString(R.string.event_kind_10006)
                    10007 -> context.getString(R.string.event_kind_10007)
                    10009 -> context.getString(R.string.event_kind_10009)
                    10013 -> context.getString(R.string.event_kind_10013)
                    10015 -> context.getString(R.string.event_kind_10015)
                    10030 -> context.getString(R.string.event_kind_10030)
                    10050 -> context.getString(R.string.event_kind_10050)
                    10063 -> context.getString(R.string.event_kind_10063)
                    10096 -> context.getString(R.string.event_kind_10096)
                    13194 -> context.getString(R.string.event_kind_13194)
                    21000 -> context.getString(R.string.event_kind_21000)
                    22242 -> context.getString(R.string.event_kind_22242)
                    23194 -> context.getString(R.string.event_kind_23194)
                    23195 -> context.getString(R.string.event_kind_23195)
                    24133 -> context.getString(R.string.event_kind_24133)
                    24242 -> context.getString(R.string.event_kind_24242)
                    27235 -> context.getString(R.string.event_kind_27235)
                    22456 -> context.getString(R.string.event_kind_22456)
                    30000 -> context.getString(R.string.event_kind_30000)
                    30001 -> context.getString(R.string.event_kind_30001)
                    30002 -> context.getString(R.string.event_kind_30002)
                    30003 -> context.getString(R.string.event_kind_30003)
                    30004 -> context.getString(R.string.event_kind_30004)
                    30008 -> context.getString(R.string.event_kind_30008)
                    30009 -> context.getString(R.string.event_kind_30009)
                    30015 -> context.getString(R.string.event_kind_30015)
                    30017 -> context.getString(R.string.event_kind_30017)
                    30018 -> context.getString(R.string.event_kind_30018)
                    30019 -> context.getString(R.string.event_kind_30019)
                    30020 -> context.getString(R.string.event_kind_30020)
                    30023 -> context.getString(R.string.event_kind_30023)
                    30024 -> context.getString(R.string.event_kind_30024)
                    30030 -> context.getString(R.string.event_kind_30030)
                    30040 -> context.getString(R.string.event_kind_30040)
                    30041 -> context.getString(R.string.event_kind_30041)
                    30063 -> context.getString(R.string.event_kind_30063)
                    30078 -> context.getString(R.string.event_kind_30078)
                    30311 -> context.getString(R.string.event_kind_30311)
                    30315 -> context.getString(R.string.event_kind_30315)
                    30402 -> context.getString(R.string.event_kind_30402)
                    30403 -> context.getString(R.string.event_kind_30403)
                    31234 -> context.getString(R.string.event_kind_31234)
                    31922 -> context.getString(R.string.event_kind_31922)
                    31923 -> context.getString(R.string.event_kind_31923)
                    31924 -> context.getString(R.string.event_kind_31924)
                    31925 -> context.getString(R.string.event_kind_31925)
                    31989 -> context.getString(R.string.event_kind_31989)
                    31990 -> context.getString(R.string.event_kind_31990)
                    34235 -> context.getString(R.string.event_kind_34235)
                    34236 -> context.getString(R.string.event_kind_34236)
                    34550 -> context.getString(R.string.event_kind_34550)
                    37375 -> context.getString(R.string.event_kind_37375)
                    38383 -> context.getString(R.string.event_kind_38383)
                    39000 -> context.getString(R.string.event_kind_39000)
                    39001 -> context.getString(R.string.event_kind_39001)
                    39002 -> context.getString(R.string.event_kind_39002)
                    in 39003..39009 -> context.getString(R.string.event_kind_39000_39009)
                    else -> {
                        val nipDescription = nipToLocalizedString(context, this, true)
                        nipDescription.ifEmpty {
                            context.getString(R.string.event_kind, kind.toString())
                        }
                    }
                }
            }
            "connect" -> context.getString(R.string.would_like_your_permission_to_read_your_public_key_and_sign_events_on_your_behalf)
            "nip" -> {
                nipToLocalizedString(context, this)
            }
            else -> type
        }
    }
}

fun nipToLocalizedString(context: Context, permission: Permission, shouldReturnEmpty: Boolean = false): String {
    return when (permission.kind) {
        1 -> context.getString(R.string.nip_01)
        2 -> context.getString(R.string.nip_02)
        3 -> context.getString(R.string.nip_03)
        4 -> context.getString(R.string.nip_04)
        5 -> context.getString(R.string.nip_05)
        6 -> context.getString(R.string.nip_06)
        7 -> context.getString(R.string.nip_07)
        8 -> context.getString(R.string.nip_08)
        9 -> context.getString(R.string.nip_09)
        10 -> context.getString(R.string.nip_10)
        11 -> context.getString(R.string.nip_11)
        13 -> context.getString(R.string.nip_13)
        14 -> context.getString(R.string.nip_14)
        15 -> context.getString(R.string.nip_15)
        17 -> context.getString(R.string.nip_17)
        18 -> context.getString(R.string.nip_18)
        19 -> context.getString(R.string.nip_19)
        21 -> context.getString(R.string.nip_21)
        23 -> context.getString(R.string.nip_23)
        24 -> context.getString(R.string.nip_24)
        25 -> context.getString(R.string.nip_25)
        26 -> context.getString(R.string.nip_26)
        27 -> context.getString(R.string.nip_27)
        28 -> context.getString(R.string.nip_28)
        29 -> context.getString(R.string.nip_29)
        30 -> context.getString(R.string.nip_30)
        31 -> context.getString(R.string.nip_31)
        32 -> context.getString(R.string.nip_32)
        34 -> context.getString(R.string.nip_34)
        35 -> context.getString(R.string.nip_35)
        36 -> context.getString(R.string.nip_36)
        38 -> context.getString(R.string.nip_38)
        39 -> context.getString(R.string.nip_39)
        40 -> context.getString(R.string.nip_40)
        42 -> context.getString(R.string.nip_42)
        44 -> context.getString(R.string.nip_44)
        45 -> context.getString(R.string.nip_45)
        46 -> context.getString(R.string.nip_46)
        47 -> context.getString(R.string.nip_47)
        48 -> context.getString(R.string.nip_48)
        49 -> context.getString(R.string.nip_49)
        50 -> context.getString(R.string.nip_50)
        51 -> context.getString(R.string.nip_51)
        52 -> context.getString(R.string.nip_52)
        53 -> context.getString(R.string.nip_53)
        54 -> context.getString(R.string.nip_54)
        55 -> context.getString(R.string.nip_55)
        56 -> context.getString(R.string.nip_56)
        57 -> context.getString(R.string.nip_57)
        58 -> context.getString(R.string.nip_58)
        59 -> context.getString(R.string.nip_59)
        65 -> context.getString(R.string.nip_65)
        70 -> context.getString(R.string.nip_70)
        71 -> context.getString(R.string.nip_71)
        72 -> context.getString(R.string.nip_72)
        75 -> context.getString(R.string.nip_75)
        78 -> context.getString(R.string.nip_78)
        84 -> context.getString(R.string.nip_84)
        89 -> context.getString(R.string.nip_89)
        90 -> context.getString(R.string.nip_90)
        92 -> context.getString(R.string.nip_92)
        94 -> context.getString(R.string.nip_94)
        96 -> context.getString(R.string.nip_96)
        98 -> context.getString(R.string.nip_98)
        99 -> context.getString(R.string.nip_99)
        else -> {
            if (shouldReturnEmpty) {
                ""
            } else {
                context.getString(R.string.nip_kind, permission.kind.toString())
            }
        }
    }
}

data class KindNip(val kind: IntRange, val nip: String)

fun Int.kindToNipUrl(): String? =
    kindsByNip.firstOrNull { it.kind.contains(this) }?.let {
        return if (it.nip.startsWith("http")) it.nip else "https://github.com/nostr-protocol/nips/blob/master/${it.nip}.md"
    }

fun Int.containsNip(): Boolean = kindsByNip.any {
    val nip = if (this < 10) {
        this.toString().padStart(2, '0')
    } else {
        this.toString()
    }
    it.nip.contains(nip)
}

fun Int.kindToNip(): String? = kindsByNip.firstOrNull { it.kind.contains(this) }?.nip

fun Int.nipToUrl(): String? = kindsByNip.firstOrNull { it.nip.toIntOrNull() == this }
    ?.nip?.let { nip -> if (nip.startsWith("http")) nip else "https://github.com/nostr-protocol/nips/blob/master/$nip.md" }

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
    KindNip(31234..31234, "https://github.com/nostr-protocol/nips/pull/1124"),
    KindNip(9467..9467, "https://wikistr.com/tidal-nostr"),
    KindNip(24242..24242, "https://wikistr.com/blossom"),
    KindNip(10063..10063, "https://wikistr.com/blossom"),
    KindNip(30040..30041, "https://wikistr.com/nkbip-01"),
    KindNip(1987..1987, "https://wikistr.com/nkbip-02"),
)

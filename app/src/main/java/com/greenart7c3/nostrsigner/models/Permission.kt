package com.greenart7c3.nostrsigner.models

import android.content.Context
import com.greenart7c3.nostrsigner.R

data class Permission(
    val type: String,
    val kind: Int?,
    var checked: Boolean = true,
) {
    fun toLocalizedString(context: Context): String {
        return when (type) {
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
                    40 -> context.getString(R.string.event_kind_40)
                    41 -> context.getString(R.string.event_kind_41)
                    42 -> context.getString(R.string.event_kind_42)
                    43 -> context.getString(R.string.event_kind_43)
                    44 -> context.getString(R.string.event_kind_44)
                    1021 -> context.getString(R.string.event_kind_1021)
                    1022 -> context.getString(R.string.event_kind_1022)
                    1040 -> context.getString(R.string.event_kind_1040)
                    1059 -> context.getString(R.string.event_kind_1059)
                    1063 -> context.getString(R.string.event_kind_1063)
                    1311 -> context.getString(R.string.event_kind_1311)
                    1971 -> context.getString(R.string.event_kind_1971)
                    1984 -> context.getString(R.string.event_kind_1984)
                    1985 -> context.getString(R.string.event_kind_1985)
                    4550 -> context.getString(R.string.event_kind_4550)
                    in 5000..5999 -> context.getString(R.string.event_kind_5000_5999)
                    in 6000..6999 -> context.getString(R.string.event_kind_6000_6999)
                    7000 -> context.getString(R.string.event_kind_7000)
                    in 9000..9030 -> context.getString(R.string.event_kind_9000_9030)
                    9041 -> context.getString(R.string.event_kind_9041)
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
                    10096 -> context.getString(R.string.event_kind_10096)
                    13194 -> context.getString(R.string.event_kind_13194)
                    21000 -> context.getString(R.string.event_kind_21000)
                    22242 -> context.getString(R.string.event_kind_22242)
                    23194 -> context.getString(R.string.event_kind_23194)
                    23195 -> context.getString(R.string.event_kind_23195)
                    24133 -> context.getString(R.string.event_kind_24133)
                    27235 -> context.getString(R.string.event_kind_27235)
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
                    in 39000..39009 -> context.getString(R.string.event_kind_39000_39009)
                    34550 -> context.getString(R.string.event_kind_34550)
                    else -> context.getString(R.string.event_kind, kind.toString())
                }
            }
            "connect" -> context.getString(R.string.would_like_your_permission_to_read_your_public_key_and_sign_events_on_your_behalf)
            else -> type
        }
    }
}

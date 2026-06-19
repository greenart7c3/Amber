package com.greenart7c3.nostrsigner.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history",
    indices = [
        Index(
            value = ["pkKey"],
            name = "history_by_pk_key",
        ),
        Index(
            value = ["id"],
            name = "history_by_id",
        ),
        Index(
            value = ["time"],
            name = "history_by_time",
        ),
        Index(
            value = ["pkKey", "time"],
            name = "history_by_key_and_time",
        ),
    ],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int,
    val pkKey: String,
    val type: String,
    val kind: Int?,
    val time: Long,
    val accepted: Boolean,
    val translatedPermission: String = "",
    // For encrypt/decrypt requests `content` holds the ciphertext (the encrypted
    // form that was sent or received), never the plaintext. The plaintext is
    // recovered on demand in the activity/history UI via [encryptionPubKey] (the
    // counterparty key) and, for NIP-44 v3, [kind] + [encryptionScope].
    val content: String = "",
    val encryptionPubKey: String = "",
    val encryptionScope: String = "",
)

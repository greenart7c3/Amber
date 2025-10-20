package com.greenart7c3.nostrsigner.models

import androidx.compose.runtime.Immutable
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * Data model for exporting a single account's credentials and metadata
 */
@Immutable
data class AccountExportData(
    @param:JsonProperty("npub")
    val npub: String,
    @param:JsonProperty("name")
    val name: String,
    @param:JsonProperty("nsec")
    val nsec: String?,
    @param:JsonProperty("seedWords")
    val seedWords: String,
    @param:JsonProperty("signPolicy")
    val signPolicy: Int,
    @param:JsonProperty("picture")
    val picture: String?,
    @param:JsonProperty("didBackup")
    val didBackup: Boolean,
)

/**
 * Container for bulk account export with versioning for future compatibility
 */
@Immutable
data class BulkAccountExport(
    @param:JsonProperty("version")
    val version: String = "1.0",
    @param:JsonProperty("exportDate")
    val exportDate: Long = System.currentTimeMillis() / 1000,
    @param:JsonProperty("accountCount")
    val accountCount: Int,
    @param:JsonProperty("accounts")
    val accounts: List<AccountExportData>,
) {
    companion object {
        private val mapper = jacksonObjectMapper()

        fun toJson(export: BulkAccountExport): String {
            return mapper.writeValueAsString(export)
        }

        fun fromJson(json: String): BulkAccountExport {
            return mapper.readValue(json)
        }
    }
}

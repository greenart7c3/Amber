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
    @JsonProperty("npub")
    val npub: String,
    @JsonProperty("name")
    val name: String,
    @JsonProperty("nsec")
    val nsec: String?,
    @JsonProperty("signPolicy")
    val signPolicy: Int,
    @JsonProperty("picture")
    val picture: String?,
    @JsonProperty("didBackup")
    val didBackup: Boolean,
)

/**
 * Container for bulk account export with versioning for future compatibility
 */
@Immutable
data class BulkAccountExport(
    @JsonProperty("version")
    val version: String = "1.0",
    @JsonProperty("exportDate")
    val exportDate: Long = System.currentTimeMillis() / 1000,
    @JsonProperty("accountCount")
    val accountCount: Int,
    @JsonProperty("accounts")
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

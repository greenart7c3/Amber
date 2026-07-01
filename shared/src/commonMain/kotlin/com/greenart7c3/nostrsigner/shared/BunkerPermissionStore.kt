package com.greenart7c3.nostrsigner.shared

/** Persisted auto-accept/auto-reject rules for a connected client app. Implemented per-platform. */
interface BunkerPermissionStore {
    /** Null means "no stored rule, ask the user"; non-null is an auto accept/reject decision. */
    suspend fun isApproved(appPubKey: String, method: BunkerMethod, kind: Int?): Boolean?

    suspend fun remember(appPubKey: String, method: BunkerMethod, kind: Int?, approved: Boolean)
}

data class BunkerApprovalRequest(
    val appPubKey: String,
    val appName: String?,
    val method: BunkerMethod,
    val kind: Int?,
    val payloadPreview: String,
)

data class BunkerApprovalDecision(
    val approved: Boolean,
    val remember: Boolean,
)

/** Prompts the user when no stored permission rule covers a request. Implemented by the UI layer. */
fun interface BunkerApprovalPort {
    suspend fun requestApproval(request: BunkerApprovalRequest): BunkerApprovalDecision
}

data class BunkerHistoryEntry(
    val appPubKey: String,
    val method: BunkerMethod,
    val kind: Int?,
    val approved: Boolean,
    val time: Long,
    val appName: String? = null,
)

/** Records handled requests for the connected-apps/history UI. Implemented per-platform. */
fun interface BunkerHistoryLogger {
    suspend fun log(entry: BunkerHistoryEntry)
}

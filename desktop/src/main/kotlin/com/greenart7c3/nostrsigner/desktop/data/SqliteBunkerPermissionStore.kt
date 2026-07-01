package com.greenart7c3.nostrsigner.desktop.data

import com.greenart7c3.nostrsigner.shared.BunkerHistoryEntry
import com.greenart7c3.nostrsigner.shared.BunkerHistoryLogger
import com.greenart7c3.nostrsigner.shared.BunkerMethod
import com.greenart7c3.nostrsigner.shared.BunkerPermissionStore
import java.sql.Connection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqliteBunkerPermissionStore(private val connection: Connection) : BunkerPermissionStore {
    override suspend fun isApproved(appPubKey: String, method: BunkerMethod, kind: Int?): Boolean? = withContext(Dispatchers.IO) {
        connection.prepareStatement(
            "SELECT approved FROM permissions WHERE app_pub_key = ? AND method = ? AND kind = ?",
        ).use { statement ->
            statement.setString(1, appPubKey)
            statement.setString(2, method.name)
            statement.setInt(3, BunkerDatabase.kindToColumn(kind))
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.getInt("approved") != 0 else null
            }
        }
    }

    override suspend fun remember(appPubKey: String, method: BunkerMethod, kind: Int?, approved: Boolean) {
        withContext(Dispatchers.IO) {
            connection.prepareStatement(
                """
                INSERT INTO permissions (app_pub_key, method, kind, approved) VALUES (?, ?, ?, ?)
                ON CONFLICT(app_pub_key, method, kind) DO UPDATE SET approved = excluded.approved
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, appPubKey)
                statement.setString(2, method.name)
                statement.setInt(3, BunkerDatabase.kindToColumn(kind))
                statement.setInt(4, if (approved) 1 else 0)
                statement.executeUpdate()
            }
        }
    }

    /** Revokes every stored rule for a connected app (used by the "connected apps" UI). */
    suspend fun revokeAll(appPubKey: String) = withContext(Dispatchers.IO) {
        connection.prepareStatement("DELETE FROM permissions WHERE app_pub_key = ?").use { statement ->
            statement.setString(1, appPubKey)
            statement.executeUpdate()
        }
    }

    /** Lists every stored auto-accept/reject rule for one app, for the app-detail permission editor. */
    suspend fun permissionsFor(appPubKey: String): List<StoredPermission> = withContext(Dispatchers.IO) {
        connection.prepareStatement(
            "SELECT method, kind, approved FROM permissions WHERE app_pub_key = ? ORDER BY method, kind",
        ).use { statement ->
            statement.setString(1, appPubKey)
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            StoredPermission(
                                appPubKey = appPubKey,
                                method = BunkerMethod.valueOf(rows.getString("method")),
                                kind = BunkerDatabase.columnToKind(rows.getInt("kind")),
                                approved = rows.getInt("approved") != 0,
                            ),
                        )
                    }
                }
            }
        }
    }

    /** Deletes a single stored rule, resetting that method/kind back to "ask next time". */
    suspend fun deletePermission(appPubKey: String, method: BunkerMethod, kind: Int?) = withContext(Dispatchers.IO) {
        connection.prepareStatement(
            "DELETE FROM permissions WHERE app_pub_key = ? AND method = ? AND kind = ?",
        ).use { statement ->
            statement.setString(1, appPubKey)
            statement.setString(2, method.name)
            statement.setInt(3, BunkerDatabase.kindToColumn(kind))
            statement.executeUpdate()
        }
    }
}

/** A single stored auto-accept/auto-reject rule, as shown in the app-detail permission editor. */
data class StoredPermission(val appPubKey: String, val method: BunkerMethod, val kind: Int?, val approved: Boolean)

data class ConnectedApp(val pubKey: String, val name: String, val connectedAt: Long)

/** One row of stored history, including its autoincrement id (used as a stable LazyColumn key). */
data class HistoryRow(val id: Long, val entry: BunkerHistoryEntry)

class SqliteBunkerHistoryLogger(private val connection: Connection) : BunkerHistoryLogger {
    override suspend fun log(entry: BunkerHistoryEntry) {
        withContext(Dispatchers.IO) {
            connection.prepareStatement(
                "INSERT INTO history (app_pub_key, method, kind, approved, time) VALUES (?, ?, ?, ?, ?)",
            ).use { statement ->
                statement.setString(1, entry.appPubKey)
                statement.setString(2, entry.method.name)
                statement.setInt(3, BunkerDatabase.kindToColumn(entry.kind))
                statement.setInt(4, if (entry.approved) 1 else 0)
                statement.setLong(5, entry.time)
                statement.executeUpdate()
            }
            connection.prepareStatement(
                """
                INSERT INTO applications (app_pub_key, name, connected_at) VALUES (?, ?, ?)
                ON CONFLICT(app_pub_key) DO UPDATE SET
                    connected_at = excluded.connected_at,
                    name = CASE WHEN excluded.name != '' THEN excluded.name ELSE applications.name END
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, entry.appPubKey)
                statement.setString(2, entry.appName.orEmpty())
                statement.setLong(3, entry.time)
                statement.executeUpdate()
            }
        }
    }

    suspend fun connectedApps(): List<ConnectedApp> = withContext(Dispatchers.IO) {
        connection.prepareStatement("SELECT app_pub_key, name, connected_at FROM applications ORDER BY connected_at DESC").use { statement ->
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(ConnectedApp(rows.getString("app_pub_key"), rows.getString("name"), rows.getLong("connected_at")))
                    }
                }
            }
        }
    }

    /** Removes a connected app's `applications` record. Permissions/history are untouched — callers that also want those cleared should call [SqliteBunkerPermissionStore.revokeAll] separately. */
    suspend fun removeApp(appPubKey: String) = withContext(Dispatchers.IO) {
        connection.prepareStatement("DELETE FROM applications WHERE app_pub_key = ?").use { statement ->
            statement.setString(1, appPubKey)
            statement.executeUpdate()
        }
    }

    /** The last known display name for an app, if any — used as [com.greenart7c3.nostrsigner.shared.BunkerSigningEngine]'s `appNameLookup` fallback. */
    suspend fun nameFor(appPubKey: String): String? = withContext(Dispatchers.IO) {
        connection.prepareStatement("SELECT name FROM applications WHERE app_pub_key = ?").use { statement ->
            statement.setString(1, appPubKey)
            statement.executeQuery().use { rows ->
                if (rows.next()) rows.getString("name").takeIf { it.isNotBlank() } else null
            }
        }
    }

    /** The most recent history entries across all apps, newest first. */
    suspend fun recentHistory(limit: Int = 200): List<HistoryRow> = withContext(Dispatchers.IO) {
        queryHistory("SELECT id, app_pub_key, method, kind, approved, time FROM history ORDER BY time DESC LIMIT ?") { statement ->
            statement.setInt(1, limit)
        }
    }

    /** The most recent history entries for a single app, newest first. */
    suspend fun recentHistoryFor(appPubKey: String, limit: Int = 50): List<HistoryRow> = withContext(Dispatchers.IO) {
        queryHistory(
            "SELECT id, app_pub_key, method, kind, approved, time FROM history WHERE app_pub_key = ? ORDER BY time DESC LIMIT ?",
        ) { statement ->
            statement.setString(1, appPubKey)
            statement.setInt(2, limit)
        }
    }

    private fun queryHistory(sql: String, bind: (java.sql.PreparedStatement) -> Unit): List<HistoryRow> = connection.prepareStatement(sql).use { statement ->
        bind(statement)
        statement.executeQuery().use { rows ->
            buildList {
                while (rows.next()) {
                    add(
                        HistoryRow(
                            id = rows.getLong("id"),
                            entry = BunkerHistoryEntry(
                                appPubKey = rows.getString("app_pub_key"),
                                method = BunkerMethod.valueOf(rows.getString("method")),
                                kind = BunkerDatabase.columnToKind(rows.getInt("kind")),
                                approved = rows.getInt("approved") != 0,
                                time = rows.getLong("time"),
                            ),
                        ),
                    )
                }
            }
        }
    }
}

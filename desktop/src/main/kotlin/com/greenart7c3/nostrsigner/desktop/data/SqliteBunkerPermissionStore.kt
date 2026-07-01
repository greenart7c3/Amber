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
}

data class ConnectedApp(val pubKey: String, val name: String, val connectedAt: Long)

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
                INSERT INTO applications (app_pub_key, connected_at) VALUES (?, ?)
                ON CONFLICT(app_pub_key) DO UPDATE SET connected_at = excluded.connected_at
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, entry.appPubKey)
                statement.setLong(2, entry.time)
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
}

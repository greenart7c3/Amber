package com.greenart7c3.nostrsigner.desktop.data

import java.sql.Connection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small key/value store for UI preferences (currently just theme mode). */
class SettingsStore(private val connection: Connection) {
    suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        connection.prepareStatement("SELECT value FROM settings WHERE key = ?").use { statement ->
            statement.setString(1, key)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString("value") else null }
        }
    }

    suspend fun set(key: String, value: String) = withContext(Dispatchers.IO) {
        connection.prepareStatement(
            """
            INSERT INTO settings (key, value) VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, key)
            statement.setString(2, value)
            statement.executeUpdate()
        }
    }
}

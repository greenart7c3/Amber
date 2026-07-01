package com.greenart7c3.nostrsigner.desktop.data

import java.sql.Connection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Persists the user's custom relay list; [BunkerRelayConnection][com.greenart7c3.nostrsigner.desktop.relay.BunkerRelayConnection] falls back to its defaults when this is empty. */
class RelayStore(private val connection: Connection) {
    suspend fun list(): List<String> = withContext(Dispatchers.IO) {
        connection.prepareStatement("SELECT url FROM relays ORDER BY url").use { statement ->
            statement.executeQuery().use { rows ->
                buildList { while (rows.next()) add(rows.getString("url")) }
            }
        }
    }

    suspend fun add(url: String) = withContext(Dispatchers.IO) {
        connection.prepareStatement("INSERT OR IGNORE INTO relays (url) VALUES (?)").use { statement ->
            statement.setString(1, url)
            statement.executeUpdate()
        }
    }

    suspend fun remove(url: String) = withContext(Dispatchers.IO) {
        connection.prepareStatement("DELETE FROM relays WHERE url = ?").use { statement ->
            statement.setString(1, url)
            statement.executeUpdate()
        }
    }
}

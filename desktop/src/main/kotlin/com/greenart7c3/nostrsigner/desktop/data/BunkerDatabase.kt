package com.greenart7c3.nostrsigner.desktop.data

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/** The on-disk home for all Amber Bunker desktop state: `~/.amber-bunker/`. */
object AppDataDir {
    val directory: File by lazy {
        File(System.getProperty("user.home"), ".amber-bunker").apply {
            mkdirs()
            restrictToOwnerOnly(this, isDirectory = true)
        }
    }

    val accountsDir: File by lazy {
        File(directory, "accounts").apply {
            mkdirs()
            restrictToOwnerOnly(this, isDirectory = true)
        }
    }

    val activeAccountFile: File get() = file("active_account")

    fun file(name: String): File = File(directory, name)

    /** Each account's own directory, holding its `account.key` and `bunker.db`. */
    fun accountDir(pubKeyHex: String): File = File(accountsDir, pubKeyHex).apply {
        mkdirs()
        restrictToOwnerOnly(this, isDirectory = true)
    }
}

/** Opens (and, on first run, creates the schema for) one account's SQLite database. */
object BunkerDatabase {
    private const val NO_KIND = -1

    fun kindToColumn(kind: Int?): Int = kind ?: NO_KIND

    fun columnToKind(value: Int): Int? = if (value == NO_KIND) null else value

    fun open(pubKeyHex: String): Connection {
        val dbFile = File(AppDataDir.accountDir(pubKeyHex), "bunker.db")
        val connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        // Restrict on every open, not just first creation, so upgrading from a version
        // predating this fix also tightens an already-existing database file.
        restrictToOwnerOnly(dbFile)
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS applications (
                    app_pub_key TEXT PRIMARY KEY,
                    name TEXT NOT NULL DEFAULT '',
                    connected_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS permissions (
                    app_pub_key TEXT NOT NULL,
                    method TEXT NOT NULL,
                    kind INTEGER NOT NULL,
                    approved INTEGER NOT NULL,
                    PRIMARY KEY (app_pub_key, method, kind)
                )
                """.trimIndent(),
            )
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    app_pub_key TEXT NOT NULL,
                    method TEXT NOT NULL,
                    kind INTEGER NOT NULL,
                    approved INTEGER NOT NULL,
                    time INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS relays (url TEXT PRIMARY KEY)")
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        }
        return connection
    }
}

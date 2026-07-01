package com.greenart7c3.nostrsigner.desktop.data

import com.greenart7c3.nostrsigner.shared.BunkerHistoryEntry
import com.greenart7c3.nostrsigner.shared.BunkerMethod
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SqliteBunkerDataLayerTest {
    private lateinit var connection: Connection

    @BeforeTest
    fun setUp() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        connection.createStatement().use { statement ->
            statement.executeUpdate("CREATE TABLE applications (app_pub_key TEXT PRIMARY KEY, name TEXT NOT NULL DEFAULT '', connected_at INTEGER NOT NULL)")
            statement.executeUpdate("CREATE TABLE permissions (app_pub_key TEXT NOT NULL, method TEXT NOT NULL, kind INTEGER NOT NULL, approved INTEGER NOT NULL, PRIMARY KEY (app_pub_key, method, kind))")
            statement.executeUpdate("CREATE TABLE history (id INTEGER PRIMARY KEY AUTOINCREMENT, app_pub_key TEXT NOT NULL, method TEXT NOT NULL, kind INTEGER NOT NULL, approved INTEGER NOT NULL, time INTEGER NOT NULL)")
            statement.executeUpdate("CREATE TABLE relays (url TEXT PRIMARY KEY)")
            statement.executeUpdate("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        }
    }

    @AfterTest
    fun tearDown() {
        connection.close()
    }

    @Test
    fun permissionsForListsAndDeletePermissionRemovesOne() = runTest {
        val store = SqliteBunkerPermissionStore(connection)
        store.remember("app1", BunkerMethod.SIGN_EVENT, 1, true)
        store.remember("app1", BunkerMethod.PING, null, false)

        val permissions = store.permissionsFor("app1")
        assertEquals(2, permissions.size)
        assertTrue(permissions.any { it.method == BunkerMethod.SIGN_EVENT && it.kind == 1 && it.approved })
        assertTrue(permissions.any { it.method == BunkerMethod.PING && it.kind == null && !it.approved })

        store.deletePermission("app1", BunkerMethod.PING, null)
        assertEquals(1, store.permissionsFor("app1").size)
        assertNull(store.isApproved("app1", BunkerMethod.PING, null))
    }

    @Test
    fun historyLoggerUpsertsAppNameWithoutClobberingWithBlank() = runTest {
        val logger = SqliteBunkerHistoryLogger(connection)
        logger.log(BunkerHistoryEntry("app1", BunkerMethod.CONNECT, null, true, 1000L, appName = "My App"))
        assertEquals("My App", logger.nameFor("app1"))

        // A later request without metadata must not blank out the previously-learned name.
        logger.log(BunkerHistoryEntry("app1", BunkerMethod.PING, null, true, 2000L, appName = null))
        assertEquals("My App", logger.nameFor("app1"))

        val connected = logger.connectedApps()
        assertEquals(1, connected.size)
        assertEquals("My App", connected.single().name)
        assertEquals(2000L, connected.single().connectedAt)
    }

    @Test
    fun recentHistoryAndRecentHistoryForReturnNewestFirst() = runTest {
        val logger = SqliteBunkerHistoryLogger(connection)
        logger.log(BunkerHistoryEntry("app1", BunkerMethod.PING, null, true, 1000L))
        logger.log(BunkerHistoryEntry("app2", BunkerMethod.GET_PUBLIC_KEY, null, true, 2000L))
        logger.log(BunkerHistoryEntry("app1", BunkerMethod.SIGN_EVENT, 1, true, 3000L))

        val all = logger.recentHistory()
        assertEquals(listOf(3000L, 2000L, 1000L), all.map { it.entry.time })

        val app1Only = logger.recentHistoryFor("app1")
        assertEquals(listOf(3000L, 1000L), app1Only.map { it.entry.time })
        assertTrue(app1Only.all { it.entry.appPubKey == "app1" })
    }

    @Test
    fun relayStoreAddsListsAndRemoves() = runTest {
        val relayStore = RelayStore(connection)
        relayStore.add("wss://relay.example.com")
        relayStore.add("wss://relay2.example.com")
        relayStore.add("wss://relay.example.com") // duplicate, ignored

        assertEquals(listOf("wss://relay.example.com", "wss://relay2.example.com"), relayStore.list())

        relayStore.remove("wss://relay.example.com")
        assertEquals(listOf("wss://relay2.example.com"), relayStore.list())
    }

    @Test
    fun settingsStoreRoundTrips() = runTest {
        val settingsStore = SettingsStore(connection)
        assertNull(settingsStore.get("theme_mode"))

        settingsStore.set("theme_mode", "DARK")
        assertEquals("DARK", settingsStore.get("theme_mode"))

        settingsStore.set("theme_mode", "LIGHT")
        assertEquals("LIGHT", settingsStore.get("theme_mode"))
    }
}

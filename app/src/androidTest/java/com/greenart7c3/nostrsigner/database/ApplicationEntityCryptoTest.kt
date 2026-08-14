package com.greenart7c3.nostrsigner.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.greenart7c3.nostrsigner.SecureCryptoHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented coverage for the GHSA-5fjp-ghh8-wch8 fix — envelope encryption
 * of [ApplicationEntity.secret] and [ApplicationEntity.localKey] at the DAO
 * boundary, plus the [MIGRATION_18_19] row re-encryption.
 *
 * Lives under `androidTest/` because [SecureCryptoHelper] depends on the
 * AndroidKeyStore provider, which is unavailable under JVM unit tests.
 */
@RunWith(AndroidJUnit4::class)
class ApplicationEntityCryptoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ApplicationDao
    private val pubKey = "ab".repeat(32)
    private val sampleSecret = "11111111-2222-3333-4444-555555555555"
    private val sampleLocalKey = "cd".repeat(32)

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newEntity(
        key: String = "app1",
        secret: String = sampleSecret,
        localKey: String = sampleLocalKey,
    ) = ApplicationEntity(
        key = key,
        name = "Test Bunker",
        relays = emptyList(),
        url = "",
        icon = "",
        description = "",
        pubKey = pubKey,
        isConnected = false,
        secret = secret,
        useSecret = true,
        signPolicy = 1,
        closeApplication = true,
        deleteAfter = 0L,
        lastUsed = 0L,
        localKey = localKey,
    )

    @Test
    fun insertAndRead_roundTrips_plaintextSecretAndLocalKey() = runBlocking {
        dao.insertApplication(newEntity())

        val read = dao.getByKey("app1")?.application
        assertNotNull(read)
        assertEquals(sampleSecret, read?.secret)
        assertEquals(sampleLocalKey, read?.localKey)
    }

    @Test
    fun insertPersistsCiphertext_rawColumnDiffersFromPlaintext() = runBlocking {
        dao.insertApplication(newEntity())

        val rawCursor = db.openHelper.readableDatabase.query(
            "SELECT `secret`, `localKey` FROM application WHERE `key` = ?",
            arrayOf("app1"),
        )
        rawCursor.use { c ->
            assertTrue("row must exist", c.moveToFirst())
            val rawSecret = c.getString(0)
            val rawLocalKey = c.getString(1)
            assertNotEquals("stored secret must not be plaintext", sampleSecret, rawSecret)
            assertNotEquals("stored localKey must not be plaintext", sampleLocalKey, rawLocalKey)
            // Sentinel format: Base64 NO_WRAP of IV(12) ‖ ciphertext+tag (>= 16 bytes)
            assertTrue("raw secret looks like Base64", rawSecret.matches(Regex("^[A-Za-z0-9+/=]+$")))
            assertTrue("raw localKey looks like Base64", rawLocalKey.matches(Regex("^[A-Za-z0-9+/=]+$")))
            // Round-trips through SecureCryptoHelper to the original plaintext.
            assertEquals(sampleSecret, SecureCryptoHelper.decryptBlocking(rawSecret))
            assertEquals(sampleLocalKey, SecureCryptoHelper.decryptBlocking(rawLocalKey))
        }
    }

    @Test
    fun emptySentinel_storedAsEmpty_decryptedAsEmpty() = runBlocking {
        dao.insertApplication(newEntity(secret = "", localKey = ""))

        val read = dao.getByKey("app1")?.application
        assertNotNull(read)
        assertEquals("", read?.secret)
        assertEquals("", read?.localKey)

        // Raw column should also be empty (not encrypted to a Base64 token).
        val rawCursor = db.openHelper.readableDatabase.query(
            "SELECT `secret`, `localKey` FROM application WHERE `key` = ?",
            arrayOf("app1"),
        )
        rawCursor.use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("", c.getString(0))
            assertEquals("", c.getString(1))
        }
    }

    @Test
    fun localPubKey_accessorReturnsDerivedPubkey() = runBlocking {
        dao.insertApplication(newEntity())

        val read = dao.getByKey("app1")?.application
        assertNotNull(read)
        assertEquals(
            localPubKeyFromPrivKey(sampleLocalKey),
            read?.localPubKey,
        )
    }

    @Test
    fun getAllWithLocalKey_onlyReturnsRowsWithNonEmptyLocalKey() = runBlocking {
        dao.insertApplication(newEntity(key = "withKey", localKey = sampleLocalKey))
        dao.insertApplication(newEntity(key = "withoutKey", localKey = ""))

        val rows = dao.getAllWithLocalKey(pubKey)
        assertEquals(1, rows.size)
        assertEquals("withKey", rows.first().key)
        assertEquals(sampleLocalKey, rows.first().localKey)
    }

    @Test
    fun getBySecret_findsRowByPlaintextSecret() = runBlocking {
        dao.insertApplication(newEntity(key = "bunker1", secret = sampleSecret))

        val found = dao.getBySecret(sampleSecret)?.application
        assertNotNull(found)
        assertEquals("bunker1", found?.key)
        assertEquals(sampleSecret, found?.secret)
    }

    @Test
    fun getBySecret_returnsNullForUnknownSecret() = runBlocking {
        dao.insertApplication(newEntity(secret = sampleSecret))

        assertNull(dao.getBySecret("not-a-known-secret"))
    }

    @Test
    fun getBySecret_findsRowWithEmptySecretWhenQueriedWithEmpty() = runBlocking {
        dao.insertApplication(newEntity(key = "empty", secret = ""))

        val found = dao.getBySecret("")?.application
        assertNotNull(found)
        assertEquals("empty", found?.key)
    }

    /**
     * Key-rotation write path (SecureCryptoHelper.rotateKey step 4): the DAO
     * must accept already-encrypted column values written under the target
     * Keystore key so the wrapper reads decrypt them back to plaintext.
     */
    @Test
    fun updateEncryptedColumnsRaw_rewritesRowCiphertext_readBackThroughWrapper() = runBlocking {
        dao.insertApplication(newEntity(key = "rot"))

        val newSecret = "99999999-8888-7777-6666-555555555555"
        val newLocalKey = "ef".repeat(32)
        dao.updateEncryptedColumnsRaw(
            key = "rot",
            secret = SecureCryptoHelper.encryptBlocking(newSecret),
            localKey = SecureCryptoHelper.encryptBlocking(newLocalKey),
        )

        val read = dao.getByKey("rot")?.application
        assertNotNull(read)
        assertEquals(newSecret, read?.secret)
        assertEquals(newLocalKey, read?.localKey)
        // Raw columns were replaced, not duplicated/concatenated.
        val rawCursor = db.openHelper.readableDatabase.query(
            "SELECT `secret` FROM application WHERE `key` = ?",
            arrayOf("rot"),
        )
        rawCursor.use { c ->
            assertTrue(c.moveToFirst())
            assertNotEquals(newSecret, c.getString(0))
            assertEquals(newSecret, SecureCryptoHelper.decryptBlocking(c.getString(0)))
        }
    }

    @Test
    fun updateEncryptedColumnsRaw_emptySentinel_roundTripsEmpty() = runBlocking {
        dao.insertApplication(newEntity(key = "rotEmpty"))

        dao.updateEncryptedColumnsRaw(key = "rotEmpty", secret = "", localKey = "")

        val read = dao.getByKey("rotEmpty")?.application
        assertNotNull(read)
        assertEquals("", read?.secret)
        assertEquals("", read?.localKey)
    }

    @Test
    fun insertApplicationWithPermissions_encryptsAndReReadsPlaintext() = runBlocking {
        val entity = newEntity(key = "permApp")
        val perms = mutableListOf(
            ApplicationPermissionsEntity(
                id = null,
                pkKey = "permApp",
                type = "SIGN_EVENT",
                kind = 1,
                acceptable = true,
                rememberType = 1,
                acceptUntil = 0L,
                rejectUntil = 0L,
            ),
        )
        dao.insertApplicationWithPermissions(
            ApplicationWithPermissions(
                application = entity,
                permissions = perms,
            ),
        )

        val read = dao.getByKey("permApp")
        assertNotNull(read)
        assertEquals(sampleSecret, read?.application?.secret)
        assertEquals(sampleLocalKey, read?.application?.localKey)
        assertEquals(1, read?.permissions?.size)
    }

    @Test
    fun getAll_decryptsAllRows() = runBlocking {
        dao.insertApplication(newEntity(key = "a", secret = "secret-a", localKey = "11".repeat(32)))
        dao.insertApplication(newEntity(key = "b", secret = "secret-b", localKey = "22".repeat(32)))

        val rows = dao.getAll(pubKey)
        assertEquals(2, rows.size)
        val byKey = rows.associateBy { it.key }
        assertEquals("secret-a", byKey["a"]?.secret)
        assertEquals("secret-b", byKey["b"]?.secret)
        assertEquals("11".repeat(32), byKey["a"]?.localKey)
        assertEquals("22".repeat(32), byKey["b"]?.localKey)
    }

    private val dbName = "migration_test.db"

    @get:Rule
    val migrationHelper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        listOf(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    /**
     * Verifies MIGRATION_18_19 envelope-encrypts existing plaintext columns.
     *
     * Creates a v18 DB (schema loaded from app/schemas/18.json), inserts
     * plaintext `secret`/`localKey` rows directly via SQL, runs the migration
     * to v19, then opens the DB via [AppDatabase] and asserts the DAO returns
     * the original plaintext values while the raw columns now hold ciphertext.
     */
    @Test
    fun migration_18_19_encryptsPlaintextSecrets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        val v18 = migrationHelper.createDatabase(dbName, 18)
        // v18 schema has the `application` table with `secret` and `localKey` TEXT columns.
        v18.execSQL(
            "INSERT INTO application " +
                "(`key`, name, relays, url, icon, description, pubKey, isConnected, " +
                "secret, useSecret, signPolicy, closeApplication, deleteAfter, lastUsed, localKey) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(
                "migApp",
                "Migration App",
                "",
                "",
                "",
                "",
                pubKey,
                0,
                sampleSecret,
                1,
                1,
                1,
                0L,
                0L,
                sampleLocalKey,
            ),
        )
        v18.execSQL(
            "INSERT INTO application " +
                "(`key`, name, relays, url, icon, description, pubKey, isConnected, " +
                "secret, useSecret, signPolicy, closeApplication, deleteAfter, lastUsed, localKey) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(
                "migAppEmpty",
                "Empty App",
                "",
                "",
                "",
                "",
                pubKey,
                0,
                "",
                0,
                1,
                1,
                0L,
                0L,
                "",
            ),
        )
        v18.close()

        val migrated = migrationHelper.runMigrationsAndValidate(dbName, 19, false, MIGRATION_18_19)
        // After migration the raw columns must be ciphertext for non-empty values
        // and "" for the empty-sentinel row.
        val rawCursor = migrated.query(
            "SELECT `secret`, `localKey` FROM application WHERE `key` = ?",
            arrayOf("migApp"),
        )
        rawCursor.use { c ->
            assertTrue(c.moveToFirst())
            val rawSecret = c.getString(0)
            val rawLocalKey = c.getString(1)
            assertNotEquals(sampleSecret, rawSecret)
            assertNotEquals(sampleLocalKey, rawLocalKey)
            assertEquals(sampleSecret, SecureCryptoHelper.decryptBlocking(rawSecret))
            assertEquals(sampleLocalKey, SecureCryptoHelper.decryptBlocking(rawLocalKey))
        }
        val emptyCursor = migrated.query(
            "SELECT `secret`, `localKey` FROM application WHERE `key` = ?",
            arrayOf("migAppEmpty"),
        )
        emptyCursor.use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("", c.getString(0))
            assertEquals("", c.getString(1))
        }
        migrated.close()
        context.deleteDatabase(dbName)
    }
}

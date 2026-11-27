package com.greenart7c3.nostrsigner.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.greenart7c3.nostrsigner.SecureCryptoHelper
import com.greenart7c3.nostrsigner.common.localPubKeyFromPrivKey
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
 * boundary, plus the [MIGRATION_19_20] row re-encryption.
 *
 * Lives under `androidTest/` because [SecureCryptoHelper] depends on the
 * AndroidKeyStore provider, which is unavailable under JVM unit tests.
 */
@RunWith(AndroidJUnit4::class)
class ApplicationEntityCryptoTest {
    private lateinit var db: AppDatabase
    private lateinit var daos: ApplicationDaos
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
        daos = ApplicationDaos.of(db)
        dao = daos.applicationDao
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
        daos.applicationWriteDao.insertApplication(newEntity())

        val read = dao.getByKey("app1")?.application
        assertNotNull(read)
        assertEquals(sampleSecret, read?.secret)
        assertEquals(sampleLocalKey, read?.localKey)
    }

    @Test
    fun insertPersistsCiphertext_rawColumnDiffersFromPlaintext() = runBlocking {
        daos.applicationWriteDao.insertApplication(newEntity())

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
            assertTrue("raw secret looks like Base64", rawSecret.matches(Regex("^[A-Za-z0-9+/=]+$")))
            assertTrue("raw localKey looks like Base64", rawLocalKey.matches(Regex("^[A-Za-z0-9+/=]+$")))
            assertEquals("raw secret decrypts to the plaintext", sampleSecret, SecureCryptoHelper.decryptBlocking(rawSecret))
            assertEquals("raw localKey decrypts to the plaintext", sampleLocalKey, SecureCryptoHelper.decryptBlocking(rawLocalKey))
        }
    }

    @Test
    fun emptySentinel_storedAsEmpty_decryptedAsEmpty() = runBlocking {
        daos.applicationWriteDao.insertApplication(newEntity(secret = "", localKey = ""))

        val read = dao.getByKey("app1")?.application
        assertNotNull(read)
        assertEquals("", read?.secret)
        assertEquals("", read?.localKey)

        val rawCursor = db.openHelper.readableDatabase.query(
            "SELECT `secret`, `localKey` FROM application WHERE `key` = ?",
            arrayOf("app1"),
        )
        rawCursor.use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("raw secret column stays empty, not an encrypted token", "", c.getString(0))
            assertEquals("raw localKey column stays empty, not an encrypted token", "", c.getString(1))
        }
    }

    @Test
    fun localPubKey_accessorReturnsDerivedPubkey() = runBlocking {
        daos.applicationWriteDao.insertApplication(newEntity())

        val read = dao.getByKey("app1")?.application
        assertNotNull(read)
        assertEquals(
            localPubKeyFromPrivKey(sampleLocalKey),
            read?.localPubKey,
        )
    }

    @Test
    fun getAllWithLocalKey_onlyReturnsRowsWithNonEmptyLocalKey() = runBlocking {
        daos.applicationWriteDao.insertApplication(newEntity(key = "withKey", localKey = sampleLocalKey))
        daos.applicationWriteDao.insertApplication(newEntity(key = "withoutKey", localKey = ""))

        val rows = daos.applicationListDao.getAllWithLocalKey(pubKey)
        assertEquals(1, rows.size)
        assertEquals("withKey", rows.first().key)
        assertEquals(sampleLocalKey, rows.first().localKey)
    }

    @Test
    fun getBySecret_findsRowByPlaintextSecret() = runBlocking {
        daos.applicationWriteDao.insertApplication(newEntity(key = "bunker1", secret = sampleSecret))

        val found = dao.getBySecret(sampleSecret)?.application
        assertNotNull(found)
        assertEquals("bunker1", found?.key)
        assertEquals(sampleSecret, found?.secret)
    }

    @Test
    fun getBySecret_returnsNullForUnknownSecret() = runBlocking {
        daos.applicationWriteDao.insertApplication(newEntity(secret = sampleSecret))

        assertNull(dao.getBySecret("not-a-known-secret"))
    }

    @Test
    fun getBySecret_findsRowWithEmptySecretWhenQueriedWithEmpty() = runBlocking {
        daos.applicationWriteDao.insertApplication(newEntity(key = "empty", secret = ""))

        val found = dao.getBySecret("")?.application
        assertNotNull(found)
        assertEquals("empty", found?.key)
    }

    /**
     * Key-rotation write path (SecureCryptoHelper.rotateKey sweep): the DAO
     * must accept already-encrypted column values so the wrapper reads
     * decrypt them back to plaintext, and only when the column still holds
     * the expected ciphertext.
     */
    @Test
    fun replaceRaw_rewritesRowCiphertext_readBackThroughWrapper() = runBlocking {
        daos.applicationWriteDao.insertApplication(newEntity(key = "rot"))
        val raw = dao.getAllApplicationsRaw().single { it.key == "rot" }

        val newSecret = "99999999-8888-7777-6666-555555555555"
        val newLocalKey = "ef".repeat(32)
        assertEquals(1, daos.applicationWriteDao.replaceSecretRaw("rot", raw.secret, SecureCryptoHelper.encryptBlocking(newSecret)))
        assertEquals(1, daos.applicationWriteDao.replaceLocalKeyRaw("rot", raw.localKey, SecureCryptoHelper.encryptBlocking(newLocalKey)))

        val read = dao.getByKey("rot")?.application
        assertNotNull(read)
        assertEquals(newSecret, read?.secret)
        assertEquals(newLocalKey, read?.localKey)
        val rawCursor = db.openHelper.readableDatabase.query(
            "SELECT `secret` FROM application WHERE `key` = ?",
            arrayOf("rot"),
        )
        rawCursor.use { c ->
            assertTrue(c.moveToFirst())
            assertNotEquals(newSecret, c.getString(0))
            assertEquals("raw secret was replaced, not concatenated", newSecret, SecureCryptoHelper.decryptBlocking(c.getString(0)))
        }
    }

    @Test
    fun replaceRaw_staleExpectedValue_leavesRowUntouched() = runBlocking {
        daos.applicationWriteDao.insertApplication(newEntity(key = "rotStale"))

        assertEquals(0, daos.applicationWriteDao.replaceSecretRaw("rotStale", "not-the-stored-value", SecureCryptoHelper.encryptBlocking("other")))
        assertEquals(0, daos.applicationWriteDao.replaceLocalKeyRaw("rotStale", "not-the-stored-value", SecureCryptoHelper.encryptBlocking("other")))

        val read = dao.getByKey("rotStale")?.application
        assertEquals(sampleSecret, read?.secret)
        assertEquals(sampleLocalKey, read?.localKey)
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
        daos.permissionWriteDao.insertApplicationWithPermissions(
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
        daos.applicationWriteDao.insertApplication(newEntity(key = "a", secret = "secret-a", localKey = "11".repeat(32)))
        daos.applicationWriteDao.insertApplication(newEntity(key = "b", secret = "secret-b", localKey = "22".repeat(32)))

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
     * Verifies MIGRATION_19_20 envelope-encrypts existing plaintext columns.
     *
     * Creates a v19 DB (schema loaded from app/schemas/19.json), inserts
     * plaintext `secret`/`localKey` rows directly via SQL, runs the migration
     * to v20, and asserts the raw columns now hold ciphertext that decrypts
     * to the original values.
     */
    @Test
    fun migration_19_20_encryptsPlaintextSecrets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)

        val v19 = migrationHelper.createDatabase(dbName, 19)
        v19.insertPlaintextApp("migApp", "Migration App", secret = sampleSecret, useSecret = 1, localKey = sampleLocalKey)
        v19.insertPlaintextApp("migAppEmpty", "Empty App", secret = "", useSecret = 0, localKey = "")
        v19.close()

        val migrated = migrationHelper.runMigrationsAndValidate(dbName, 20, false, MIGRATION_19_20)
        val (rawSecret, rawLocalKey) = migrated.rawSecrets("migApp")
        assertNotEquals("migrated secret is ciphertext", sampleSecret, rawSecret)
        assertNotEquals("migrated localKey is ciphertext", sampleLocalKey, rawLocalKey)
        assertEquals(sampleSecret, SecureCryptoHelper.decryptBlocking(rawSecret))
        assertEquals(sampleLocalKey, SecureCryptoHelper.decryptBlocking(rawLocalKey))
        assertEquals("the empty-sentinel row stays empty", "" to "", migrated.rawSecrets("migAppEmpty"))
        migrated.close()
        context.deleteDatabase(dbName)
    }

    private fun SupportSQLiteDatabase.insertPlaintextApp(
        key: String,
        name: String,
        secret: String,
        useSecret: Int,
        localKey: String,
    ) {
        execSQL(
            "INSERT INTO application " +
                "(`key`, name, relays, url, icon, description, pubKey, isConnected, " +
                "secret, useSecret, signPolicy, closeApplication, deleteAfter, lastUsed, localKey) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(key, name, "", "", "", "", pubKey, 0, secret, useSecret, 1, 1, 0L, 0L, localKey),
        )
    }

    private fun SupportSQLiteDatabase.rawSecrets(key: String): Pair<String, String> = query(
        "SELECT `secret`, `localKey` FROM application WHERE `key` = ?",
        arrayOf(key),
    ).use { c ->
        assertTrue(c.moveToFirst())
        c.getString(0) to c.getString(1)
    }
}

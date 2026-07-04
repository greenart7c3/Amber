package com.greenart7c3.nostrsigner.database

import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DatabaseEncryptionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun fileWith(bytes: ByteArray): File {
        val file = folder.newFile()
        file.writeBytes(bytes)
        return file
    }

    @Test
    fun `plaintext sqlite header is detected`() {
        val header = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        val file = fileWith(header + Random.nextBytes(64))
        assertEquals(DatabaseEncryption.FileState.PLAINTEXT, DatabaseEncryption.detectFileState(file))
    }

    @Test
    fun `random header is detected as encrypted`() {
        // SQLCipher encrypts the whole file including the 16-byte header, so an
        // encrypted database is indistinguishable from random bytes.
        val bytes = ByteArray(80)
        bytes.fill(0x41)
        val file = fileWith(bytes)
        assertEquals(DatabaseEncryption.FileState.ENCRYPTED, DatabaseEncryption.detectFileState(file))
    }

    @Test
    fun `missing file is detected`() {
        val file = File(folder.root, "does_not_exist")
        assertEquals(DatabaseEncryption.FileState.MISSING, DatabaseEncryption.detectFileState(file))
    }

    @Test
    fun `empty file is treated as missing`() {
        val file = fileWith(ByteArray(0))
        assertEquals(DatabaseEncryption.FileState.MISSING, DatabaseEncryption.detectFileState(file))
    }

    @Test
    fun `file shorter than the header is treated as missing`() {
        val file = fileWith("SQLite".toByteArray(Charsets.US_ASCII))
        assertEquals(DatabaseEncryption.FileState.MISSING, DatabaseEncryption.detectFileState(file))
    }

    @Test
    fun `header prefix alone is not plaintext`() {
        val corrupted = "SQLite format 3!".toByteArray(Charsets.US_ASCII)
        val file = fileWith(corrupted + Random.nextBytes(16))
        assertEquals(DatabaseEncryption.FileState.ENCRYPTED, DatabaseEncryption.detectFileState(file))
    }
}

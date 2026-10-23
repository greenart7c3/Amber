package com.greenart7c3.nostrsigner.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateApkVerifierTest {
    private val pkg = "com.greenart7c3.nostrsigner"
    private val devCert = "aa".repeat(32)
    private val rotatedCert = "bb".repeat(32)
    private val attackerCert = "cc".repeat(32)

    @Test
    fun `same package signed with the installed certificate is trusted`() {
        assertTrue(UpdateApkVerifier.isTrustedUpdate(pkg, setOf(devCert), pkg, setOf(devCert)))
    }

    @Test
    fun `key rotation is trusted when the lineage shares a certificate`() {
        assertTrue(UpdateApkVerifier.isTrustedUpdate(pkg, setOf(devCert), pkg, setOf(devCert, rotatedCert)))
    }

    @Test
    fun `look-alike package or foreign signer is refused`() {
        assertFalse(UpdateApkVerifier.isTrustedUpdate(pkg, setOf(devCert), "com.greenart7c3.nostrsigner.update", setOf(devCert)))
        assertFalse(UpdateApkVerifier.isTrustedUpdate(pkg, setOf(devCert), pkg, setOf(attackerCert)))
        assertFalse(UpdateApkVerifier.isTrustedUpdate(pkg, setOf(devCert), null, emptySet()))
        assertFalse(UpdateApkVerifier.isTrustedUpdate(pkg, emptySet(), pkg, emptySet()))
    }
}

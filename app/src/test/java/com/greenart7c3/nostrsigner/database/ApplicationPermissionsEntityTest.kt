package com.greenart7c3.nostrsigner.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApplicationPermissionsEntityTest {
    private val now = 1_000L

    private fun permission(
        acceptable: Boolean,
        acceptUntil: Long = 0,
        rejectUntil: Long = 0,
        rememberType: Int = 5,
    ) = ApplicationPermissionsEntity(1, "caller", "SIGN_EVENT", 1, acceptable, rememberType, acceptUntil, rejectUntil)

    @Test
    fun `an active grant accepts and an active denial rejects`() {
        assertEquals(true, permission(acceptable = true, acceptUntil = now + 1).activeDecision(now))
        assertEquals(false, permission(acceptable = false, rejectUntil = now + 1).activeDecision(now))
    }

    @Test
    fun `a decision expires at its timestamp`() {
        assertNull(permission(acceptable = true, acceptUntil = now).activeDecision(now))
        assertNull(permission(acceptable = false, rejectUntil = now).activeDecision(now))
    }

    @Test
    fun `a stale timestamp on the unselected side never decides`() {
        // A denial row with a leftover future acceptUntil used to auto-accept
        // on the NIP-46 path.
        assertNull(permission(acceptable = false, acceptUntil = now + 3600).activeDecision(now))
        assertNull(permission(acceptable = true, rejectUntil = now + 3600).activeDecision(now))
    }

    @Test
    fun `an ask row with no timestamps asks`() {
        assertNull(permission(acceptable = true, rememberType = 7).activeDecision(now))
    }

    @Test
    fun `an always grant accepts`() {
        assertEquals(true, permission(acceptable = true, acceptUntil = Long.MAX_VALUE / 1000, rememberType = 4).activeDecision(now))
    }
}

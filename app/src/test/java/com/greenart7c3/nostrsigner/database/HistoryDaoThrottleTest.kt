package com.greenart7c3.nostrsigner.database

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryDaoThrottleTest {
    @Test
    fun `shouldWriteLastUsed throttles repeated updates within 60 seconds`() {
        val key = "npub1test|app1"
        assertTrue(HistoryDao.shouldWriteLastUsed(key, 1_000L))
        assertFalse(HistoryDao.shouldWriteLastUsed(key, 1_005L))
        assertFalse(HistoryDao.shouldWriteLastUsed(key, 1_059L))
        assertTrue(HistoryDao.shouldWriteLastUsed(key, 1_060L))
    }

    @Test
    fun `shouldWriteLastUsed tracks apps independently`() {
        assertTrue(HistoryDao.shouldWriteLastUsed("npub1test|appA", 2_000L))
        assertTrue(HistoryDao.shouldWriteLastUsed("npub1test|appB", 2_001L))
        assertFalse(HistoryDao.shouldWriteLastUsed("npub1test|appA", 2_002L))
        assertFalse(HistoryDao.shouldWriteLastUsed("npub1test|appB", 2_003L))
    }
}

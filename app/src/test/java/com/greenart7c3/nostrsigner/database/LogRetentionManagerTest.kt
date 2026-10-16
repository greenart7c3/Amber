package com.greenart7c3.nostrsigner.database

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LogRetentionManagerTest {
    @Test
    fun `trimToBudget does nothing when under budget`() = runBlocking {
        var deleteCalls = 0
        val deleted = LogRetentionManager.trimToBudget(100, { 50 }) {
            deleteCalls++
            10
        }
        assertEquals(0, deleted)
        assertEquals(0, deleteCalls)
    }

    @Test
    fun `trimToBudget deletes batches until under budget`() = runBlocking {
        var size = 350L
        val deleted = LogRetentionManager.trimToBudget(100, { size }) {
            size -= 100
            10
        }
        // 350 -> 250 -> 150 -> 50
        assertEquals(30, deleted)
        assertEquals(50, size)
    }

    @Test
    fun `trimToBudget stops when nothing is left to delete`() = runBlocking {
        var deleteCalls = 0
        val deleted = LogRetentionManager.trimToBudget(100, { 500 }) {
            deleteCalls++
            0
        }
        assertEquals(0, deleted)
        assertEquals(1, deleteCalls)
    }
}

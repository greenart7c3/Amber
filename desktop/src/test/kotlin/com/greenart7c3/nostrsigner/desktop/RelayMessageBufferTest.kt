package com.greenart7c3.nostrsigner.desktop

import com.greenart7c3.nostrsigner.desktop.core.RelayMessageBuffer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The relay message hand-off is bounded: on overflow the oldest buffered
 * message is dropped instead of the queue growing (which OOMed the app under
 * a flood), the drop is counted, and the reader-side `offer` never blocks.
 */
class RelayMessageBufferTest {
    @Test
    fun `overflow drops the oldest and keeps the newest`() = runBlocking {
        val buffer = RelayMessageBuffer(capacity = 2)
        (1..5).forEach { buffer.offer(it.toString()) }

        assertEquals("three messages must have been dropped", 3L, buffer.dropped)
        assertEquals("4", buffer.receive())
        assertEquals("5", buffer.receive())
        buffer.close()
        assertNull("a closed, drained buffer reports null", buffer.receive())
    }

    @Test
    fun `offers after close are ignored without counting a drop`() = runBlocking {
        val buffer = RelayMessageBuffer(capacity = 2)
        buffer.close()

        buffer.offer("late")

        assertEquals(0L, buffer.dropped)
        assertNull(buffer.receive())
    }

    @Test
    fun `capacity is respected exactly at the limit`() = runBlocking {
        val buffer = RelayMessageBuffer(capacity = 3)
        (1..3).forEach { buffer.offer(it.toString()) }

        assertEquals(0L, buffer.dropped)
        assertEquals("1", buffer.receive())
        assertEquals("2", buffer.receive())
        assertEquals("3", buffer.receive())
        buffer.close()
        assertNull(buffer.receive())
    }
}

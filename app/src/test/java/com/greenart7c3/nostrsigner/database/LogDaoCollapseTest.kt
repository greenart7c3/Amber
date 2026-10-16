package com.greenart7c3.nostrsigner.database

import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LogDaoCollapseTest {
    private class FakeLogDao : LogDao {
        val rows = mutableListOf<LogEntity>()
        private var nextId = 1

        override fun insertAll(items: List<LogEntity>) {
            items.forEach { rows.add(it.copy(id = nextId++)) }
        }

        override suspend fun insertLog(logEntity: LogEntity) {
            rows.add(logEntity.copy(id = nextId++))
        }

        override suspend fun findRecentDuplicate(url: String, type: String, message: String, since: Long): Int? = rows
            .filter { it.url == url && it.type == type && it.message == message && it.time >= since }
            .maxByOrNull { it.time }
            ?.id

        override suspend fun incrementRepeat(id: Int, time: Long) {
            val index = rows.indexOfFirst { it.id == id }
            val row = rows[index]
            rows[index] = row.copy(repeatCount = row.repeatCount + 1, time = maxOf(row.time, time))
        }

        override fun getLogs(): Flow<List<LogEntity>> = throw NotImplementedError()

        override fun getLogsPaging(): PagingSource<Int, LogEntity> = throw NotImplementedError()

        override fun getLogsByUrl(url: String): Flow<List<LogEntity>> = throw NotImplementedError()

        override fun getLogsByUrlPaging(url: String): PagingSource<Int, LogEntity> = throw NotImplementedError()

        override suspend fun getCount(): Long = rows.size.toLong()

        override suspend fun deleteOldestLogs(count: Int): Int = throw NotImplementedError()

        override suspend fun clearLogs() = rows.clear()

        override suspend fun deleteOldLog(time: Long): Int = throw NotImplementedError()

        override suspend fun deleteLog(logEntity: LogEntity) {
            rows.removeAll { it.id == logEntity.id }
        }
    }

    private fun log(time: Long, message: String = "Connected", url: String = "wss://relay.example/") = LogEntity(0, url, "onConnected", message, time)

    @Test
    fun `identical entry within the window bumps the existing row`() = runBlocking {
        val dao = FakeLogDao()
        dao.insertOrCollapse(log(1_000))
        dao.insertOrCollapse(log(2_000))
        dao.insertOrCollapse(log(3_000))

        assertEquals(1, dao.rows.size)
        assertEquals(3, dao.rows.single().repeatCount)
        assertEquals(3_000, dao.rows.single().time)
    }

    @Test
    fun `different message or url creates a new row`() = runBlocking {
        val dao = FakeLogDao()
        dao.insertOrCollapse(log(1_000))
        dao.insertOrCollapse(log(2_000, message = "Disconnected"))
        dao.insertOrCollapse(log(3_000, url = "wss://other.example/"))

        assertEquals(3, dao.rows.size)
        dao.rows.forEach { assertEquals(1, it.repeatCount) }
    }

    @Test
    fun `entry outside the window creates a new row`() = runBlocking {
        val dao = FakeLogDao()
        dao.insertOrCollapse(log(1_000))
        dao.insertOrCollapse(log(1_000 + LogDao.COLLAPSE_WINDOW_MILLIS + 1))

        assertEquals(2, dao.rows.size)
    }
}

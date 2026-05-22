package com.greenart7c3.nostrsigner.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.system.measureNanoTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-Room benchmark for [CachingApplicationDao]. Builds an in-memory [AppDatabase],
 * seeds it with the permission rows a typical app accumulates, then measures the
 * SIGN_EVENT permission-check query mix (sign policy + kind-specific permission +
 * NIP fallback) twice:
 *
 *  - raw  : direct DAO, every iteration hits SQLite.
 *  - warm : caching DAO with the cache pre-primed, every iteration is a hit.
 *
 * Asserts warm is meaningfully faster than raw (5x floor — actual speedup is far
 * larger on a real device since each raw call is a full SQLite roundtrip). Also
 * sanity-checks that warm and raw return the same row for the same query.
 */
@RunWith(AndroidJUnit4::class)
class PermissionCacheBenchmarkTest {
    private lateinit var db: AppDatabase
    private lateinit var raw: ApplicationDao
    private lateinit var cached: CachingApplicationDao

    private val pkKey = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val seededKind = 1
    private val unseededKind = 31337

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        raw = db.dao()
        cached = CachingApplicationDao(raw)

        runBlocking {
            raw.insertApplication(
                ApplicationEntity(
                    key = pkKey,
                    name = "Bench App",
                    relays = emptyList(),
                    url = "",
                    icon = "",
                    description = "",
                    pubKey = pkKey,
                    isConnected = true,
                    secret = "",
                    useSecret = false,
                    signPolicy = 1,
                    closeApplication = false,
                    deleteAfter = 0L,
                    lastUsed = 0L,
                ),
            )
            val perms = mutableListOf<ApplicationPermissionsEntity>()
            for (k in listOf(0, 1, 3, 4, 6, 7, 9735, 10002, 30023)) {
                perms.add(
                    ApplicationPermissionsEntity(
                        id = null,
                        pkKey = pkKey,
                        type = "SIGN_EVENT",
                        kind = k,
                        acceptable = true,
                        rememberType = 1,
                        acceptUntil = 0L,
                        rejectUntil = 0L,
                    ),
                )
            }
            for (nip in listOf(4, 44)) {
                perms.add(
                    ApplicationPermissionsEntity(
                        id = null,
                        pkKey = pkKey,
                        type = "NIP",
                        kind = nip,
                        acceptable = true,
                        rememberType = 1,
                        acceptUntil = 0L,
                        rejectUntil = 0L,
                    ),
                )
            }
            raw.insertPermissions(perms)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun queryMix(dao: ApplicationDao) {
        // Mirrors SignerProvider.kt SIGN_EVENT path: sign policy + kind-specific
        // permission + NIP fallback for an unseeded kind.
        dao.getSignPolicy(pkKey)
        dao.getPermission(pkKey, "SIGN_EVENT", seededKind)
        dao.getPermission(pkKey, "SIGN_EVENT", unseededKind)
        dao.getPermission(pkKey, "NIP", 4)
    }

    @Test
    fun benchmark_signEvent_query_mix() {
        // Correctness: cached must return the same data as raw.
        assertEquals(raw.getSignPolicy(pkKey), cached.getSignPolicy(pkKey))
        assertEquals(
            raw.getPermission(pkKey, "SIGN_EVENT", seededKind)?.id,
            cached.getPermission(pkKey, "SIGN_EVENT", seededKind)?.id,
        )
        assertEquals(
            raw.getPermission(pkKey, "SIGN_EVENT", unseededKind),
            cached.getPermission(pkKey, "SIGN_EVENT", unseededKind),
        )

        val warmupIters = 1_000
        val measureIters = 10_000

        // Warmup so JIT and SQLite statement caches are primed for both paths.
        repeat(warmupIters) {
            queryMix(raw)
            queryMix(cached)
        }

        val rawNanos = measureNanoTime {
            repeat(measureIters) { queryMix(raw) }
        }

        val warmNanos = measureNanoTime {
            repeat(measureIters) { queryMix(cached) }
        }

        val rawMs = rawNanos / 1_000_000.0
        val warmMs = warmNanos / 1_000_000.0
        val speedup = rawNanos.toDouble() / warmNanos.toDouble()

        println(
            "[PermissionCacheBenchmark] iters=$measureIters  raw=${"%.2f".format(rawMs)}ms  " +
                "warm=${"%.2f".format(warmMs)}ms  speedup=${"%.1f".format(speedup)}x",
        )

        assertTrue(
            "Warm cache should be at least 5x faster than raw DAO; " +
                "got raw=${rawMs}ms warm=${warmMs}ms (${"%.1f".format(speedup)}x)",
            speedup >= 5.0,
        )
    }

    @Test
    fun benchmark_invalidation_correctness() {
        // After a write that touches this app, the next read must NOT return a
        // stale cached value. This is the regression we'd care about more than
        // raw speed — caching is worthless if it returns wrong answers.
        cached.getPermission(pkKey, "SIGN_EVENT", seededKind) // prime

        runBlocking {
            cached.deletePermissions(pkKey, "SIGN_EVENT", seededKind)
        }

        assertEquals(
            null,
            cached.getPermission(pkKey, "SIGN_EVENT", seededKind),
        )
    }
}

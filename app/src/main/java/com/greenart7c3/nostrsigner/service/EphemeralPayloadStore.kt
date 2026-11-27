package com.greenart7c3.nostrsigner.service

import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * In-memory, caller-UID-isolated staging store for NIP-55 payloads that exceed
 * Android's Binder / CursorWindow limits. Handles are opaque 128-bit tokens
 * served over `content://<applicationId>.PAYLOADS/<token>` pipes.
 */
class EphemeralPayloadStore(
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxSinglePayloadBytes: Int = MAX_SINGLE_PAYLOAD_BYTES,
    private val maxPerCallerBytes: Long = MAX_PER_CALLER_BYTES.toLong(),
    private val maxTotalBytes: Long = MAX_TOTAL_BYTES.toLong(),
    private val maxEntriesPerCaller: Int = MAX_ENTRIES_PER_CALLER,
    private val ttlMs: Long = ENTRY_TTL_MS,
    private val uploadAwaitTimeoutMs: Long = UPLOAD_AWAIT_TIMEOUT_MS,
    val inlineThresholdBytes: Int = INLINE_THRESHOLD_BYTES,
) {
    private sealed interface Entry {
        val ownerUid: Int
        val ownerPackage: String
        val createdAtMs: Long
        val accountedBytes: Long
    }

    private class UploadEntry(
        override val ownerUid: Int,
        override val ownerPackage: String,
        override val createdAtMs: Long,
        val data: CompletableDeferred<ByteArray> = CompletableDeferred(),
        val openStarted: AtomicBoolean = AtomicBoolean(false),
        val consumed: AtomicBoolean = AtomicBoolean(false),
        override var accountedBytes: Long = 0L,
        var completedBytes: ByteArray? = null,
    ) : Entry

    private class ResponseEntry(
        override val ownerUid: Int,
        override val ownerPackage: String,
        override val createdAtMs: Long,
        val bytes: ByteArray,
        val consumed: AtomicBoolean = AtomicBoolean(false),
    ) : Entry {
        override val accountedBytes: Long get() = bytes.size.toLong()
    }

    private val lock = Any()
    private val random = SecureRandom()
    private val entries = HashMap<String, Entry>()
    private val callerBytes = HashMap<Int, Long>()
    private val callerEntryCounts = HashMap<Int, Int>()
    private var totalBytes = 0L

    fun allocateUpload(ownerUid: Int, ownerPackage: String): String = synchronized(lock) {
        evictExpiredLocked()
        ensureCallerEntryLimitLocked(ownerUid)
        val token = nextTokenLocked()
        entries[token] = UploadEntry(
            ownerUid = ownerUid,
            ownerPackage = ownerPackage,
            createdAtMs = clock(),
        )
        callerEntryCounts[ownerUid] = (callerEntryCounts[ownerUid] ?: 0) + 1
        token
    }

    fun readStreamIntoUpload(
        token: String,
        ownerUid: Int,
        ownerPackage: String,
        input: InputStream,
    ) {
        try {
            val buffer = ByteArray(PIPE_CHUNK_BYTES)
            val output = ByteArrayOutputStream()
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (output.size().toLong() + read.toLong() > maxSinglePayloadBytes.toLong()) {
                    throw IOException("Payload exceeds limit of $maxSinglePayloadBytes bytes")
                }
                output.write(buffer, 0, read)
            }
            completeUpload(token, ownerUid, ownerPackage, output.toByteArray())
        } catch (t: Throwable) {
            failUpload(token, ownerUid, ownerPackage, t)
            throw t
        }
    }

    fun completeUpload(
        token: String,
        ownerUid: Int,
        ownerPackage: String,
        bytes: ByteArray,
    ) {
        synchronized(lock) {
            evictExpiredLocked()
            val entry = entries[token] as? UploadEntry
                ?: throw FileNotFoundException("Unknown or expired upload token")
            requireOwner(entry.ownerUid, entry.ownerPackage, ownerUid, ownerPackage)
            val size = bytes.size.toLong()
            if (size > maxSinglePayloadBytes.toLong()) {
                discardAndFailUploadLocked(token, entry, IOException("Payload exceeds limit of $maxSinglePayloadBytes bytes"))
            }
            if (!canFitQuotasLocked(ownerUid, size)) {
                discardAndFailUploadLocked(token, entry, IOException("Payload memory quota exceeded"))
            }
            entry.accountedBytes = size
            entry.completedBytes = bytes
            totalBytes += size
            callerBytes[ownerUid] = (callerBytes[ownerUid] ?: 0L) + size
            entry.data.complete(bytes)
        }
    }

    suspend fun consumeUpload(
        token: String,
        callerUid: Int,
        callerPackage: String,
    ): ByteArray {
        val entry = synchronized(lock) {
            evictExpiredLocked()
            val existing = entries[token] as? UploadEntry
                ?: throw FileNotFoundException("Unknown or expired upload token")
            requireOwner(existing.ownerUid, existing.ownerPackage, callerUid, callerPackage)
            if (!existing.consumed.compareAndSet(false, true)) {
                throw FileNotFoundException("Upload already consumed")
            }
            existing
        }
        var delivered = false
        return try {
            val bytes = withTimeout(uploadAwaitTimeoutMs) {
                entry.data.await()
            }
            delivered = true
            bytes
        } finally {
            synchronized(lock) {
                removeEntryLocked(token, wipeBytes = !delivered)
            }
        }
    }

    @Suppress("RunBlocking")
    fun consumeUploadBlocking(
        token: String,
        callerUid: Int,
        callerPackage: String,
    ): ByteArray = runBlocking {
        consumeUpload(token, callerUid, callerPackage)
    }

    fun putResponse(
        ownerUid: Int,
        ownerPackage: String,
        bytes: ByteArray,
    ): String {
        val size = bytes.size.toLong()
        if (size > maxSinglePayloadBytes.toLong()) {
            throw IOException("Response exceeds limit of $maxSinglePayloadBytes bytes")
        }
        return synchronized(lock) {
            evictExpiredLocked()
            ensureCallerEntryLimitLocked(ownerUid)
            if (!canFitQuotasLocked(ownerUid, size)) {
                throw IOException("Payload memory quota exceeded")
            }
            val token = nextTokenLocked()
            entries[token] = ResponseEntry(
                ownerUid = ownerUid,
                ownerPackage = ownerPackage,
                createdAtMs = clock(),
                bytes = bytes,
            )
            totalBytes += size
            callerBytes[ownerUid] = (callerBytes[ownerUid] ?: 0L) + size
            callerEntryCounts[ownerUid] = (callerEntryCounts[ownerUid] ?: 0) + 1
            token
        }
    }

    fun consumeResponse(
        token: String,
        callerUid: Int,
        callerPackage: String,
    ): ByteArray = synchronized(lock) {
        evictExpiredLocked()
        val entry = entries[token] as? ResponseEntry
            ?: throw FileNotFoundException("Unknown or expired response token")
        requireOwner(entry.ownerUid, entry.ownerPackage, callerUid, callerPackage)
        if (!entry.consumed.compareAndSet(false, true)) {
            throw FileNotFoundException("Response already consumed")
        }
        removeEntryLocked(token, wipeBytes = false)
        entry.bytes
    }

    fun delete(
        token: String,
        callerUid: Int,
        callerPackage: String,
    ): Boolean = synchronized(lock) {
        evictExpiredLocked()
        val entry = entries[token] ?: return false
        requireOwner(entry.ownerUid, entry.ownerPackage, callerUid, callerPackage)
        removeEntryLocked(token, wipeBytes = true)
        true
    }

    fun openWritePipe(
        token: String,
        callerUid: Int,
        callerPackage: String,
    ): ParcelFileDescriptor {
        synchronized(lock) {
            evictExpiredLocked()
            val entry = entries[token] as? UploadEntry
                ?: throw FileNotFoundException("Unknown or expired upload token")
            requireOwner(entry.ownerUid, entry.ownerPackage, callerUid, callerPackage)
            if (!entry.openStarted.compareAndSet(false, true)) {
                throw IllegalStateException("Upload stream already opened")
            }
        }
        val pipe = ParcelFileDescriptor.createReliablePipe()
        val readSide = pipe[0]
        val writeSide = pipe[1]
        scope.launch(Dispatchers.IO) {
            pumpPipeToUpload(token, callerUid, callerPackage, readSide)
        }
        return writeSide
    }

    fun openReadPipe(
        token: String,
        callerUid: Int,
        callerPackage: String,
    ): ParcelFileDescriptor {
        val bytes = consumeResponse(token, callerUid, callerPackage)
        val pipe = try {
            ParcelFileDescriptor.createReliablePipe()
        } catch (t: Throwable) {
            bytes.fill(0)
            throw t
        }
        val readSide = pipe[0]
        val writeSide = pipe[1]
        scope.launch(Dispatchers.IO) {
            pumpResponseToPipe(bytes, writeSide)
        }
        return readSide
    }

    private fun pumpPipeToUpload(
        token: String,
        callerUid: Int,
        callerPackage: String,
        readSide: ParcelFileDescriptor,
    ) {
        val stream = ParcelFileDescriptor.AutoCloseInputStream(readSide)
        var closed = false
        try {
            readStreamIntoUpload(token, callerUid, callerPackage, stream)
            stream.close()
            closed = true
        } catch (t: Throwable) {
            if (t is CancellationException) {
                runCatching { stream.close() }
                throw t
            }
            runCatching { readSide.closeWithError(t.message ?: "Upload failed") }
            if (!closed) {
                runCatching { stream.close() }
            }
        }
    }

    private fun pumpResponseToPipe(
        bytes: ByteArray,
        writeSide: ParcelFileDescriptor,
    ) {
        val stream = ParcelFileDescriptor.AutoCloseOutputStream(writeSide)
        var closed = false
        try {
            var offset = 0
            while (offset < bytes.size) {
                val count = minOf(PIPE_CHUNK_BYTES, bytes.size - offset)
                stream.write(bytes, offset, count)
                offset += count
            }
            stream.flush()
            stream.close()
            closed = true
        } catch (t: Throwable) {
            if (t is CancellationException) {
                runCatching { stream.close() }
                throw t
            }
            runCatching { writeSide.closeWithError(t.message ?: "Response stream failed") }
            if (!closed) {
                runCatching { stream.close() }
            }
        } finally {
            bytes.fill(0)
        }
    }

    private fun failUpload(
        token: String,
        ownerUid: Int,
        ownerPackage: String,
        cause: Throwable,
    ) {
        synchronized(lock) {
            val entry = entries[token] as? UploadEntry ?: return
            if (entry.ownerUid != ownerUid || entry.ownerPackage != ownerPackage) return
            entry.data.completeExceptionally(cause)
            removeEntryLocked(token, wipeBytes = true)
        }
    }

    private fun discardAndFailUploadLocked(
        token: String,
        entry: UploadEntry,
        cause: IOException,
    ): Nothing {
        entry.data.completeExceptionally(cause)
        removeEntryLocked(token, wipeBytes = true)
        throw cause
    }

    private fun evictExpiredLocked() {
        val now = clock()
        val expiredTokens = entries.entries
            .filter { now - it.value.createdAtMs >= ttlMs }
            .map { it.key }
        for (token in expiredTokens) {
            removeEntryLocked(token, wipeBytes = true)
        }
    }

    private fun removeEntryLocked(token: String, wipeBytes: Boolean): Entry? {
        val entry = entries.remove(token) ?: return null
        val ownerUid = entry.ownerUid
        totalBytes = (totalBytes - entry.accountedBytes).coerceAtLeast(0L)
        val remainingCallerBytes = (callerBytes[ownerUid] ?: 0L) - entry.accountedBytes
        if (remainingCallerBytes > 0L) {
            callerBytes[ownerUid] = remainingCallerBytes
        } else {
            callerBytes.remove(ownerUid)
        }
        val remainingCount = (callerEntryCounts[ownerUid] ?: 0) - 1
        if (remainingCount > 0) {
            callerEntryCounts[ownerUid] = remainingCount
        } else {
            callerEntryCounts.remove(ownerUid)
        }
        if (wipeBytes) {
            wipeEntryLocked(entry)
        }
        return entry
    }

    private fun wipeEntryLocked(entry: Entry) {
        when (entry) {
            is UploadEntry -> {
                entry.completedBytes?.fill(0)
                entry.completedBytes = null
                if (!entry.data.isCompleted) {
                    entry.data.cancel()
                }
            }
            is ResponseEntry -> entry.bytes.fill(0)
        }
    }

    private fun ensureCallerEntryLimitLocked(ownerUid: Int) {
        val count = callerEntryCounts[ownerUid] ?: 0
        if (count >= maxEntriesPerCaller) {
            throw IOException("Too many active payload handles for caller")
        }
    }

    private fun canFitQuotasLocked(ownerUid: Int, incomingBytes: Long): Boolean {
        val nextCaller = (callerBytes[ownerUid] ?: 0L) + incomingBytes
        val nextTotal = totalBytes + incomingBytes
        return nextCaller <= maxPerCallerBytes && nextTotal <= maxTotalBytes
    }

    private fun requireOwner(
        ownerUid: Int,
        ownerPackage: String,
        callerUid: Int,
        callerPackage: String,
    ) {
        if (ownerUid != callerUid || ownerPackage != callerPackage) {
            throw SecurityException("Caller is not authorized to access payload handle")
        }
    }

    private fun nextTokenLocked(): String {
        val raw = ByteArray(TOKEN_BYTES)
        while (true) {
            random.nextBytes(raw)
            val token = raw.joinToString(separator = "") { "%02x".format(it) }
            if (!entries.containsKey(token)) return token
        }
    }

    companion object {
        const val PAYLOADS_AUTHORITY_SUFFIX = "PAYLOADS"
        const val INLINE_THRESHOLD_BYTES = 128 * 1024
        const val MAX_SINGLE_PAYLOAD_BYTES = 16 * 1024 * 1024
        const val MAX_PER_CALLER_BYTES = 24 * 1024 * 1024
        const val MAX_TOTAL_BYTES = 48 * 1024 * 1024
        const val MAX_ENTRIES_PER_CALLER = 32
        const val ENTRY_TTL_MS = 60_000L
        const val UPLOAD_AWAIT_TIMEOUT_MS = 10_000L
        private const val TOKEN_BYTES = 16
        private const val PIPE_CHUNK_BYTES = 16 * 1024
        private val TOKEN_REGEX = Regex("^[0-9a-f]{32}$")

        const val EXTRA_PAYLOAD_URI = "payload_uri"
        const val EXTRA_ACCEPT_RESULT_URI = "accept_result_uri"
        const val EXTRA_RESULT_URI = "result_uri"
        const val EXTRA_EVENT_URI = "event_uri"
        const val EXTRA_RESULTS_URI = "results_uri"

        const val QUERY_PARAM_PAYLOAD_IN_URI = "payload_in_uri"
        const val QUERY_PARAM_ACCEPT_RESULT_URI = "accept_result_uri"
        const val QUERY_PARAM_RETURN_TYPE = "return_type"

        fun buildPayloadUri(appId: String, token: String): String = "content://$appId.$PAYLOADS_AUTHORITY_SUFFIX/$token"

        fun isPayloadsCollectionUri(uriString: String, appId: String): Boolean = uriString == "content://$appId.$PAYLOADS_AUTHORITY_SUFFIX" ||
            uriString == "content://$appId.$PAYLOADS_AUTHORITY_SUFFIX/"

        fun extractToken(uriString: String, appId: String): String? {
            val prefix = "content://$appId.$PAYLOADS_AUTHORITY_SUFFIX/"
            if (!uriString.startsWith(prefix)) return null
            val candidate = uriString.removePrefix(prefix)
            return candidate.takeIf { TOKEN_REGEX.matches(it) }
        }
    }
}

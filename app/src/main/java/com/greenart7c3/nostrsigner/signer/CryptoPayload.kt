package com.greenart7c3.nostrsigner.signer

import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import java.io.IOException

/**
 * Carries a bulk byte payload across the binder boundary between the main
 * process and the isolated `:signer` process.
 *
 * The Android binder transaction buffer is ~1MB and is shared per-process across
 * all in-flight transactions, so large encrypt/decrypt/sign payloads (NIP-44 /
 * NIP-44v3 of file-sized content, long-form events) cannot ride inline as a
 * `byte[]`/`String` — especially on the ContentProvider path, where the binder
 * thread is already holding the inbound transaction buffer when it forwards to
 * `:signer`.
 *
 * Small payloads (<= [INLINE_LIMIT]) travel inline. Larger payloads stream
 * through a reliable pipe whose read end is passed over the binder; a daemon
 * thread writes the bytes while the receiver drains them. Pipes work on every
 * supported API level (minSdk 26), so no `SharedMemory` API gating is needed.
 * No plaintext is ever written to disk.
 */
class CryptoPayload private constructor(
    // Set on the sender side (always) and on the receiver side for inline mode.
    private val bytes: ByteArray?,
    // Set on the receiver side for pipe mode; drained once by [readBytes].
    private val source: ParcelFileDescriptor?,
) : Parcelable {
    /** Reads the full payload. For a pipe-backed payload this drains and closes it (single use). */
    fun readBytes(): ByteArray {
        bytes?.let { return it }
        val pfd = source ?: return EMPTY
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    fun readString(): String = readBytes().toString(Charsets.UTF_8)

    override fun describeContents(): Int = // A receiver pipe instance holds an FD; a sender instance whose payload
        // exceeds the inline limit will create one in writeToParcel — both must
        // advertise it so containers carry the descriptor.
        if (source != null || (bytes != null && bytes.size > INLINE_LIMIT)) Parcelable.CONTENTS_FILE_DESCRIPTOR else 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        // A receiver-side pipe instance being re-parcelled would have null bytes;
        // drain it first. In practice each instance is written exactly once.
        val data = bytes ?: readBytes()
        if (data.size <= INLINE_LIMIT) {
            dest.writeInt(MODE_INLINE)
            dest.writeByteArray(data)
        } else {
            dest.writeInt(MODE_PIPE)
            val pipe = ParcelFileDescriptor.createReliablePipe()
            val readEnd = pipe[0]
            val writeEnd = pipe[1]
            Thread {
                try {
                    ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use { it.write(data) }
                } catch (_: IOException) {
                    // The reader went away before draining; nothing more to do.
                }
            }.apply {
                isDaemon = true
                name = "amber-signer-payload-writer"
                start()
            }
            readEnd.writeToParcel(dest, flags)
            // The parcel holds a dup of the read end; release our copy.
            readEnd.close()
        }
    }

    companion object {
        /** Payloads up to this size ride inline; larger ones stream over a pipe. */
        const val INLINE_LIMIT: Int = 64 * 1024

        private const val MODE_INLINE = 0
        private const val MODE_PIPE = 1
        private val EMPTY = ByteArray(0)

        fun of(bytes: ByteArray): CryptoPayload = CryptoPayload(bytes, null)

        fun of(text: String): CryptoPayload = CryptoPayload(text.toByteArray(Charsets.UTF_8), null)

        @JvmField
        val CREATOR: Parcelable.Creator<CryptoPayload> = object : Parcelable.Creator<CryptoPayload> {
            override fun createFromParcel(source: Parcel): CryptoPayload = when (source.readInt()) {
                MODE_INLINE -> CryptoPayload(source.createByteArray() ?: EMPTY, null)
                else -> CryptoPayload(null, ParcelFileDescriptor.CREATOR.createFromParcel(source))
            }

            override fun newArray(size: Int): Array<CryptoPayload?> = arrayOfNulls(size)
        }
    }
}

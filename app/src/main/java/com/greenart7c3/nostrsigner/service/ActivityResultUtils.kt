package com.greenart7c3.nostrsigner.service

import android.app.Activity
import android.content.Intent
import android.os.Parcel
import android.os.TransactionTooLargeException
import com.greenart7c3.nostrsigner.Amber
import com.greenart7c3.nostrsigner.AmberLog
import com.greenart7c3.nostrsigner.R

/**
 * Binder transactions share a ~1 MB buffer per process, and Activity.finish()
 * sends the pending activity result through it, so a result Intent anywhere
 * near that limit crashes the app with a TransactionTooLargeException instead
 * of being delivered. Results are kept well below the limit to leave room for
 * the rest of the transaction and anything else in flight.
 */
const val MAX_RESULT_INTENT_PARCEL_SIZE = 512 * 1024

fun Intent.parcelSizeBytes(): Int {
    val parcel = Parcel.obtain()
    return try {
        writeToParcel(parcel, 0)
        parcel.dataSize()
    } finally {
        parcel.recycle()
    }
}

fun Intent.fitsInActivityResult(): Boolean = parcelSizeBytes() <= MAX_RESULT_INTENT_PARCEL_SIZE

/**
 * finish() throws before the activity is marked finished, so when the pending
 * result turns out to be too large for the binder transaction it can be
 * replaced with a small error result and finished again — the calling app gets
 * an "error" extra instead of the signer crashing and staying open.
 */
fun Activity.finishAndRemoveTaskSafely() {
    try {
        finishAndRemoveTask()
    } catch (e: RuntimeException) {
        if (e.cause !is TransactionTooLargeException) throw e
        AmberLog.d(Amber.TAG, "Activity result too large to deliver", e)
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra("error", getString(R.string.result_too_large)),
        )
        finishAndRemoveTask()
    }
}

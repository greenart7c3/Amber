package com.greenart7c3.nostrsigner

import android.util.Log

/**
 * Thin wrapper around [android.util.Log] that only emits when running a debug
 * build. Release builds produce no logcat output, keeping potentially sensitive
 * data (npubs, relay URLs, event payloads) out of device logs.
 *
 * Mirrors the subset of the [Log] API used across the app. Prefer this over
 * [android.util.Log] everywhere so the debug gate stays in a single place.
 */
object AmberLog {
    fun v(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.v(tag, message)
    }

    fun v(tag: String, message: String?, throwable: Throwable?) {
        if (BuildConfig.DEBUG) Log.v(tag, message, throwable)
    }

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun d(tag: String, message: String?, throwable: Throwable?) {
        if (BuildConfig.DEBUG) Log.d(tag, message, throwable)
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.i(tag, message)
    }

    fun i(tag: String, message: String?, throwable: Throwable?) {
        if (BuildConfig.DEBUG) Log.i(tag, message, throwable)
    }

    fun w(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.w(tag, message)
    }

    fun w(tag: String, message: String?, throwable: Throwable?) {
        if (BuildConfig.DEBUG) Log.w(tag, message, throwable)
    }

    fun e(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.e(tag, message)
    }

    fun e(tag: String, message: String?, throwable: Throwable?) {
        if (BuildConfig.DEBUG) Log.e(tag, message, throwable)
    }
}

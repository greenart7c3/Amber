package com.greenart7c3.nostrsigner.common

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/** How many times a NIP-46 reply to a connected app is published before giving up. */
const val BUNKER_REPLY_ATTEMPTS = 5

/**
 * Error replies to a sender Amber has not verified (unknown method, refused
 * connect secret, no permission) are published once: a forged or unknown
 * sender must not buy several signed events plus seconds of work per request.
 */
const val UNVERIFIED_REPLY_ATTEMPTS = 1

/**
 * Runs [block] until it returns true, at most [maxRetries] times, waiting
 * [initialDelay] (doubling up to [maxDelay]) before each try. A single try
 * runs at once, without the wait.
 */
suspend fun retryWithBackoff(
    maxRetries: Int = BUNKER_REPLY_ATTEMPTS,
    initialDelay: Duration = 200.milliseconds,
    maxDelay: Duration = 3_200.milliseconds,
    block: suspend () -> Boolean,
): Boolean {
    if (maxRetries <= 1) return block()
    var currentDelay = initialDelay
    repeat(maxRetries) { attempt ->
        delay(currentDelay)
        if (block()) return true
        if (attempt < maxRetries - 1) {
            currentDelay = (currentDelay * 2).coerceAtMost(maxDelay)
        }
    }
    return false
}

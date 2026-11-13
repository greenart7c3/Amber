package com.greenart7c3.nostrsigner.cli

/**
 * Stable process exit codes: scripts and agents branch on these instead of
 * parsing messages. `amber help` lists them for the humans and agents reading it.
 */
object Exit {
    const val OK = 0

    /** Anything unexpected: an invalid answer, a broken saved file, I/O. */
    const val ERROR = 1

    /** Bad arguments: an unknown command or option, missing input, invalid JSON. */
    const val USAGE = 2

    /** No saved connection for this profile; pair first with `login` or `connect`. */
    const val NOT_PAIRED = 3

    /** Amber answered and refused: the user rejected, or it has no grant for the request. */
    const val REJECTED = 4

    /** Amber did not answer in time: it may be offline, or still waiting for a human. */
    const val TIMEOUT = 5

    /** No relay accepted a connection at all. */
    const val UNREACHABLE = 6
}

/** A failure that ends the command with [code] and a [message] for the user. */
class CliError(
    override val message: String,
    val code: Int = Exit.ERROR,
) : Exception(message)

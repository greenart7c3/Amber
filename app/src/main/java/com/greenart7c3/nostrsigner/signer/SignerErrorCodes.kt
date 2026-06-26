package com.greenart7c3.nostrsigner.signer

/**
 * Codes carried across the binder boundary between the `:signer` process and the
 * main process. They let the main-process facade reconstruct the domain
 * semantics each call site expects without ever passing key material or rich
 * exceptions across the boundary.
 *
 * They travel encoded in the message of an [IllegalStateException] — one of the
 * few exception types AIDL marshals natively on every supported API level
 * (ServiceSpecificException would be cleaner but requires API 28; minSdk is 26).
 * Format: "<code>:<detail>".
 */
object SignerErrorCodes {
    /** Generic crypto failure (bad input, MAC/padding, etc.). */
    const val GENERIC = 1

    /** The operation legitimately produced null (e.g. decryptZapEvent). */
    const val NULL_RESULT = 2

    /**
     * The AndroidKeyStore key for the account could not be decrypted
     * (device KeyMint bug). The main process repopulates keystoreFailedAccounts.
     */
    const val KEYSTORE_FAILED = 3

    /** No private key is stored for the requested npub. */
    const val NO_KEY = 4

    fun encode(code: Int, detail: String): String = "$code:$detail"

    /** Parses the leading code from an encoded message; defaults to [GENERIC]. */
    fun codeOf(message: String?): Int {
        if (message == null) return GENERIC
        return message.substringBefore(':', "").toIntOrNull() ?: GENERIC
    }
}

package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.common.relayHostAndPort

object RelayUrlUtils {
    /**
     * Normalizes a relay URL/host into a comparable "host[:port]" form used by
     * the auth whitelist and per-relay permission lookups; shared with the
     * desktop app (see the common [relayHostAndPort]).
     */
    fun extractHostAndPort(input: String?): String = relayHostAndPort(input)
}

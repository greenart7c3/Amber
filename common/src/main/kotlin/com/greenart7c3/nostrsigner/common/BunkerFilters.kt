package com.greenart7c3.nostrsigner.common

import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent

// shortcut: 100 is a guess at what relays accept as #p values per filter; lower it if a relay rejects the filter.
const val BUNKER_P_TAGS_PER_FILTER = 100

/**
 * The filters of the one bunker REQ each relay gets, covering every endpoint pubkey that
 * listens there. One REQ per connection ran into relays' per-socket subscription caps
 * (strfry: 20), which reject the rest with a NOTICE and leave those connections deaf (#644).
 * Several filters in one REQ still count as one subscription.
 */
fun bunkerFilters(keysByRelay: Map<NormalizedRelayUrl, Set<String>>, since: Long): Map<NormalizedRelayUrl, List<Filter>> = keysByRelay.filterValues { it.isNotEmpty() }.mapValues { (_, keys) ->
    keys.chunked(BUNKER_P_TAGS_PER_FILTER).map { chunk ->
        Filter(
            kinds = listOf(NostrConnectEvent.KIND),
            tags = mapOf("p" to chunk),
            limit = chunk.size,
            since = since,
        )
    }
}

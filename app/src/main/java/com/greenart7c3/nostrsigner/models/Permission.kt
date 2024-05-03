package com.greenart7c3.nostrsigner.models

data class Permission(
    val type: String,
    val kind: Int?,
    var checked: Boolean = true,
) {
    override fun toString(): String {
        return when (type) {
            "get_public_key" -> "Read your public key"
            "nip04_encrypt" -> {
                "Encrypt data using nip 4"
            }
            "nip04_decrypt" -> {
                "Decrypt data using nip 4"
            }
            "nip44_decrypt" -> {
                "Decrypt data using nip 44"
            }
            "nip44_encrypt" -> {
                "Encrypt data using nip 44"
            }
            "decrypt_zap_event" -> {
                "Decrypt private zaps"
            }
            "sign_event" -> {
                when (kind) {
                    0 -> "Metadata"
                    1 -> "Short text note"
                    3 -> "Follows"
                    4 -> "Encrypted direct messages"
                    5 -> "Event deletion"
                    6 -> "Repost"
                    7 -> "Reaction"
                    8 -> "Badge award"
                    9 -> "Group chat message"
                    10 -> "Group chat threaded reply"
                    11 -> "Group thread"
                    12 -> "Group thread reply"
                    13 -> "Seal"
                    16 -> "Generic repost"
                    40 -> "Channel creation"
                    41 -> "Channel metadata"
                    42 -> "Channel message"
                    43 -> "Channel hide message"
                    44 -> "Channel mute user"
                    1021 -> "Bid"
                    1022 -> "Bid confirmation"
                    1040 -> "OpenTimestamps"
                    1059 -> "Gift wrap"
                    1063 -> "File metadata"
                    1311 -> "Live chat message"
                    1971 -> "Problem tracker"
                    1984 -> "Reporting"
                    1985 -> "Label"
                    4550 -> "Community post approval"
                    in 5000..5999 -> "Job request"
                    in 6000..6999 -> "Job result"
                    7000 -> "Job feedback"
                    in 9000..9030 -> "Group control events"
                    9041 -> "Zap goal"
                    9734 -> "Zap request"
                    9735 -> "Zap"
                    9802 -> "Highlights"
                    10000 -> "Mute list"
                    10001 -> "Pin list"
                    10002 -> "Relay list metadata"
                    10003 -> "Bookmark list"
                    10004 -> "Communities list"
                    10005 -> "Public chats list"
                    10006 -> "Blocked relays list"
                    10007 -> "Search relays list"
                    10009 -> "User groups"
                    10015 -> "Interests list"
                    10030 -> "User emoji list"
                    10096 -> "File storage server list"
                    13194 -> "Wallet Info"
                    21000 -> "Lightning Pub RPC"
                    22242 -> "Client authentication"
                    23194 -> "Wallet request"
                    23195 -> "Wallet response"
                    24133 -> "Nostr connect"
                    27235 -> "HTTP auth"
                    30000 -> "Follow sets"
                    30001 -> "Generic lists"
                    30002 -> "Relay sets"
                    30003 -> "Bookmark sets"
                    30004 -> "Curation sets"
                    30008 -> "Profile badges"
                    30009 -> "Badge definition"
                    30015 -> "Interest sets"
                    30017 -> "Create or update a stall"
                    30018 -> "Create or update a product"
                    30019 -> "Marketplace UI/UX"
                    30020 -> "Product sold as an auction"
                    30023 -> "Long-form content"
                    30024 -> "Draft Long-form content"
                    30030 -> "Emoji sets"
                    30063 -> "Release artifact sets"
                    30078 -> "Application-specific data"
                    30311 -> "Live event"
                    30315 -> "User statuses"
                    30402 -> "Classified listing"
                    30403 -> "Draft classified listing"
                    31234 -> "Generic draft event"
                    31922 -> "Date-Based calendar event"
                    31923 -> "Time-Based calendar event"
                    31924 -> "Calendar"
                    31925 -> "Calendar event RSVP"
                    31989 -> "Handler recommendation"
                    31990 -> "Handler information"
                    in 39000..39009 -> "Group metadata events"
                    34550 -> "Community definition"
                    else -> "Event kind $kind"
                }
            }
            "connect" -> "would like your permission to read your public key and sign events on your behalf"
            else -> type
        }
    }
}

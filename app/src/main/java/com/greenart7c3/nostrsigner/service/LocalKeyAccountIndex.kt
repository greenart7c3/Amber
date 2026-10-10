package com.greenart7c3.nostrsigner.service

/**
 * Maps a connection's local public key (hex) to the account npub it belongs to
 * and the connection's local private key. Maintained by [NotificationSubscription.updateFilter];
 * read by [EventNotificationConsumer.consume] to avoid an O(accounts × connections)
 * scan with synchronous DB calls and key encoding on every incoming bunker event.
 */
object LocalKeyAccountIndex {
    data class Match(val npub: String, val localPrivKey: String)

    @Volatile
    private var byLocalPubKey: Map<String, Match> = emptyMap()

    fun lookup(localPubKey: String): Match? = byLocalPubKey[localPubKey]

    fun replaceAll(entries: Map<String, Match>) {
        byLocalPubKey = entries.toMap()
    }
}

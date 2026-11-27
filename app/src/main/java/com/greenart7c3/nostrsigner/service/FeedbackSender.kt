package com.greenart7c3.nostrsigner.service

import com.greenart7c3.nostrsigner.Amber.Companion.DEVELOPER_HEX_KEY
import com.greenart7c3.nostrsigner.data.RelayClient
import com.greenart7c3.nostrsigner.data.SettingsStore
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.FeedbackType
import com.greenart7c3.nostrsigner.models.TorMode
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchFirst
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.TimeUtils

/** Files feedback as a NIP-34 issue on Amber's repository, over the app's relay client. */
class FeedbackSender(
    private val relayClient: RelayClient,
    private val settingsStore: SettingsStore,
) {
    private val client get() = relayClient.client
    private val settings get() = settingsStore.settings

    /**
     * Signs the issue with [account] and publishes it to the repository's relays, which the
     * relay client connects to for this publish even when they are not among its own relays,
     * and disconnects from afterwards unless another filter uses them.
     */
    suspend fun send(
        subject: String,
        body: String,
        type: FeedbackType,
        account: Account,
    ): Boolean {
        val usingTor = settings.torMode != TorMode.DISABLED
        val publishTimeoutSeconds =
            if (usingTor) FEEDBACK_TOR_PUBLISH_TIMEOUT_SECONDS else FEEDBACK_PUBLISH_TIMEOUT_SECONDS

        val repositoryEvent = fetchAmberRepositoryEvent(usingTor) ?: standInRepositoryEventForTheIssueATag()
        val relays = repositoryEvent.relays()
            .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?: setOfNotNull(RelayUrlNormalizer.normalizeOrNull(FEEDBACK_FALLBACK_RELAY))

        val template = GitIssueEvent.build(
            subject,
            body,
            EventHintBundle(repositoryEvent),
            listOf(PTag(DEVELOPER_HEX_KEY, null)),
            listOf(if (type == FeedbackType.BUG_REPORT) "bug" else "enhancement"),
        )

        val event = account.sign(
            template,
        )

        return client.publishAndConfirm(event, relays, publishTimeoutSeconds, untilFirstAccept = true)
    }

    /**
     * NIP-34: a repository announcement's `relays` tag lists the relays its
     * issues live on. The announcement itself is published on the author's own
     * relays, so resolve those from their NIP-65 relay list first, then ask
     * for the announcement. Returns null when either step finds nothing.
     */
    @Suppress("NoComments")
    private suspend fun fetchAmberRepositoryEvent(usingTor: Boolean): GitRepositoryEvent? {
        val fetchTimeoutMs = if (usingTor) FEEDBACK_TOR_FETCH_TIMEOUT_MS else FEEDBACK_FETCH_TIMEOUT_MS

        val authorRelays = fetchAuthorRelays(fetchTimeoutMs)
        val discoveryRelays = (authorRelays + settings.defaultRelays)
            .takeIf { it.isNotEmpty() }
            ?: setOfNotNull(RelayUrlNormalizer.normalizeOrNull(FEEDBACK_FALLBACK_RELAY))

        val event = client.fetchFirst(
            filters = discoveryRelays.associateWith {
                listOf(
                    Filter(
                        kinds = listOf(GitRepositoryEvent.KIND),
                        authors = listOf(DEVELOPER_HEX_KEY),
                        tags = mapOf("d" to listOf(AMBER_REPO_IDENTIFIER)),
                        limit = 1,
                    ),
                )
            },
            idleTimeoutMs = fetchTimeoutMs,
        ) ?: return null
        if (!event.verify()) return null

        return GitRepositoryEvent(
            event.id,
            event.pubKey,
            event.createdAt,
            event.tags,
            event.content,
            event.sig,
        )
    }

    private suspend fun fetchAuthorRelays(fetchTimeoutMs: Long): Set<NormalizedRelayUrl> {
        val discoveryRelays = (settings.defaultProfileRelays + settings.defaultRelays)
            .toSet()
            .takeIf { it.isNotEmpty() }
            ?: setOfNotNull(RelayUrlNormalizer.normalizeOrNull(FEEDBACK_FALLBACK_RELAY))

        val event = client.fetchFirst(
            filters = discoveryRelays.associateWith {
                listOf(
                    Filter(
                        kinds = listOf(AdvertisedRelayListEvent.KIND),
                        authors = listOf(DEVELOPER_HEX_KEY),
                        limit = 1,
                    ),
                )
            },
            idleTimeoutMs = fetchTimeoutMs,
        ) ?: return emptySet()
        if (!event.verify()) return emptySet()

        return AdvertisedRelayListEvent(
            event.id,
            event.pubKey,
            event.createdAt,
            event.tags,
            event.content,
            event.sig,
        ).relaysNorm().toSet()
    }

    private fun standInRepositoryEventForTheIssueATag(): GitRepositoryEvent = GitRepositoryEvent(
        "",
        DEVELOPER_HEX_KEY,
        TimeUtils.now(),
        tags = arrayOf(arrayOf("d", AMBER_REPO_IDENTIFIER)),
        "",
        "",
    )

    private companion object {
        const val AMBER_REPO_IDENTIFIER = "Amber"
        const val FEEDBACK_FALLBACK_RELAY = "wss://nos.lol/"
        const val FEEDBACK_PUBLISH_TIMEOUT_SECONDS = 15L
        const val FEEDBACK_TOR_PUBLISH_TIMEOUT_SECONDS = 60L
        const val FEEDBACK_FETCH_TIMEOUT_MS = 30_000L
        const val FEEDBACK_TOR_FETCH_TIMEOUT_MS = 60_000L
    }
}

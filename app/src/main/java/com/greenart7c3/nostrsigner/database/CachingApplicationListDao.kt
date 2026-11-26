package com.greenart7c3.nostrsigner.database

import androidx.paging.PagingSource

/** The cached path's [ApplicationListDao]: it caches nothing, but like the other caching DAOs hides connections past their `deleteAfter` lifetime. */
class CachingApplicationListDao(
    private val delegate: ApplicationListDao,
) : ApplicationListDao {
    override suspend fun getAllNotConnected(): List<ApplicationWithPermissions> = delegate.getAllNotConnected()

    override fun getApplicationListItemsPaging(pubKey: String): PagingSource<Int, ApplicationListItem> = delegate.getApplicationListItemsPaging(pubKey)

    override fun getApplicationListItemsPagingByName(pubKey: String): PagingSource<Int, ApplicationListItem> = delegate.getApplicationListItemsPagingByName(pubKey)

    override fun getAllRelayLists(): List<RelayListWrapper> = delegate.getAllRelayLists()

    override suspend fun getAllWithLocalKey(pubKey: String): List<ApplicationEntity> = delegate.getAllWithLocalKey(pubKey).filterNot { it.isExpired() }

    override fun getAllKeys(): List<String> = delegate.getAllKeys()

    override fun getApplicationKeyNames(): List<ApplicationKeyName> = delegate.getApplicationKeyNames()

    override suspend fun getAllNotConnectedRaw(): List<ApplicationWithPermissions> = delegate.getAllNotConnectedRaw()

    override suspend fun getAllWithLocalKeyRaw(pubKey: String): List<ApplicationEntity> = delegate.getAllWithLocalKeyRaw(pubKey)
}

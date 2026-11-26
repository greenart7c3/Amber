package com.greenart7c3.nostrsigner.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query

/** Pages of the request history for the activity list, optionally searched and filtered by outcome. */
@Dao
interface HistoryPagingDao {
    @Query(
        """
    SELECT * FROM history
    WHERE pkKey = :pk
    AND (:acceptedFilter IS NULL OR accepted = :acceptedFilter)
    ORDER BY time DESC
    """,
    )
    fun getAllHistoryPaging(pk: String, acceptedFilter: Boolean?): PagingSource<Int, HistoryEntity>

    @Query(
        """
    SELECT * FROM history
    WHERE pkKey = :pk
    AND (kind = :query OR LOWER(type) LIKE '%' || :query || '%'
    OR LOWER(translatedPermission) LIKE '%' || :query || '%'
    OR LOWER(content) LIKE '%' || :query || '%')
    AND (:acceptedFilter IS NULL OR accepted = :acceptedFilter)
    ORDER BY time DESC
    """,
    )
    fun searchAllHistoryPaging(pk: String, query: String, acceptedFilter: Boolean?): PagingSource<Int, HistoryEntity>

    @Query(
        """
    SELECT * FROM history
    WHERE (:acceptedFilter IS NULL OR accepted = :acceptedFilter)
    ORDER BY time DESC
    """,
    )
    fun getAllHistoryPaging(acceptedFilter: Boolean?): PagingSource<Int, HistoryEntity>

    @Query(
        """
    SELECT * FROM history
    WHERE (kind = :query OR LOWER(type) LIKE '%' || :query || '%'
    OR LOWER(translatedPermission) LIKE '%' || :query || '%'
    OR LOWER(content) LIKE '%' || :query || '%')
    AND (:acceptedFilter IS NULL OR accepted = :acceptedFilter)
    ORDER BY time DESC
    """,
    )
    fun searchAllHistoryPaging(query: String, acceptedFilter: Boolean?): PagingSource<Int, HistoryEntity>
}

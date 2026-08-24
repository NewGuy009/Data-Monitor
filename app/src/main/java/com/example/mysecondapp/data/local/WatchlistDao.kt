package com.example.mysecondapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {

    @Query(
        """
        SELECT * FROM watchlist
        ORDER BY
            CASE WHEN group_name IS NULL OR group_name = '' THEN 1 ELSE 0 END,
            group_name ASC,
            display_order ASC,
            market ASC,
            code ASC
        """,
    )
    fun observeAll(): Flow<List<WatchlistEntity>>

    @Query(
        """
        SELECT * FROM watchlist
        WHERE group_name = :groupName
        ORDER BY display_order ASC, market ASC, code ASC
        """,
    )
    fun observeByGroup(groupName: String): Flow<List<WatchlistEntity>>

    @Query(
        """
        SELECT * FROM watchlist
        WHERE group_name IS NULL OR group_name = ''
        ORDER BY display_order ASC, market ASC, code ASC
        """,
    )
    fun observeUngrouped(): Flow<List<WatchlistEntity>>

    @Query(
        """
        SELECT DISTINCT group_name FROM watchlist
        WHERE group_name IS NOT NULL AND group_name != ''
        ORDER BY group_name ASC
        """,
    )
    fun observeGroupNames(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchlistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<WatchlistEntity>)

    @Query("DELETE FROM watchlist WHERE market = :market AND code = :code")
    suspend fun deleteByPrimaryKey(
        market: String,
        code: String,
    )

    @Query("UPDATE watchlist SET group_name = :groupName WHERE market = :market AND code = :code")
    suspend fun updateGroup(
        market: String,
        code: String,
        groupName: String?,
    )

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE market = :market AND code = :code)")
    suspend fun exists(
        market: String,
        code: String,
    ): Boolean

    @Query("SELECT COALESCE(MAX(display_order), -1) FROM watchlist")
    suspend fun getMaxDisplayOrder(): Int

    @Query("SELECT COUNT(*) FROM watchlist")
    suspend fun count(): Int
}

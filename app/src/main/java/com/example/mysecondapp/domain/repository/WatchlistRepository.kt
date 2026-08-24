package com.example.mysecondapp.domain.repository

import com.example.mysecondapp.domain.model.WatchlistItem
import kotlinx.coroutines.flow.Flow

interface WatchlistRepository {
    fun observeWatchlistItems(): Flow<List<WatchlistItem>>

    fun observeWatchlistGroup(groupName: String): Flow<List<WatchlistItem>>

    fun observeUngroupedWatchlistItems(): Flow<List<WatchlistItem>>

    fun observeGroupNames(): Flow<List<String>>

    suspend fun addWatchlistItem(item: WatchlistItem)

    suspend fun addWatchlistItems(items: List<WatchlistItem>)

    suspend fun removeWatchlistItem(
        market: String,
        code: String,
    )

    suspend fun updateWatchlistItemGroup(
        market: String,
        code: String,
        groupName: String?,
    )

    suspend fun updateWatchlistOrder(items: List<WatchlistItem>)

    suspend fun containsWatchlistItem(
        market: String,
        code: String,
    ): Boolean

    suspend fun nextDisplayOrder(): Int

    suspend fun seedSampleWatchlistIfEmpty()
}

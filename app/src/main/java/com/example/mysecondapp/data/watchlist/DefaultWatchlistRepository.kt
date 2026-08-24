package com.example.mysecondapp.data.watchlist

import com.example.mysecondapp.data.local.WatchlistDao
import com.example.mysecondapp.domain.model.WatchlistItem
import com.example.mysecondapp.domain.repository.WatchlistRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DefaultWatchlistRepository @Inject constructor(
    private val watchlistDao: WatchlistDao,
) : WatchlistRepository {

    override fun observeWatchlistItems(): Flow<List<WatchlistItem>> =
        watchlistDao.observeAll().map { entities ->
            // Keep Room entities inside the data layer and expose stable domain models.
            entities.map { entity -> entity.toDomainModel() }
        }

    override fun observeWatchlistGroup(groupName: String): Flow<List<WatchlistItem>> =
        watchlistDao.observeByGroup(groupName).map { entities ->
            entities.map { entity -> entity.toDomainModel() }
        }

    override fun observeUngroupedWatchlistItems(): Flow<List<WatchlistItem>> =
        watchlistDao.observeUngrouped().map { entities ->
            entities.map { entity -> entity.toDomainModel() }
        }

    override fun observeGroupNames(): Flow<List<String>> = watchlistDao.observeGroupNames()

    override suspend fun addWatchlistItem(item: WatchlistItem) {
        watchlistDao.upsert(item.toEntity())
    }

    override suspend fun addWatchlistItems(items: List<WatchlistItem>) {
        watchlistDao.upsertAll(items.map { item -> item.toEntity() })
    }

    override suspend fun removeWatchlistItem(
        market: String,
        code: String,
    ) {
        watchlistDao.deleteByPrimaryKey(
            market = market,
            code = code,
        )
    }

    override suspend fun updateWatchlistItemGroup(
        market: String,
        code: String,
        groupName: String?,
    ) {
        watchlistDao.updateGroup(
            market = market,
            code = code,
            groupName = groupName,
        )
    }

    override suspend fun updateWatchlistOrder(items: List<WatchlistItem>) {
        // Rewriting display order in batch keeps drag-and-drop persistence simple.
        watchlistDao.upsertAll(items.map { item -> item.toEntity() })
    }

    override suspend fun containsWatchlistItem(
        market: String,
        code: String,
    ): Boolean = watchlistDao.exists(
        market = market,
        code = code,
    )

    override suspend fun nextDisplayOrder(): Int {
        // Append new items so the existing custom order does not jump unexpectedly.
        return watchlistDao.getMaxDisplayOrder() + 1
    }

    override suspend fun seedSampleWatchlistIfEmpty() {
        if (watchlistDao.count() > 0) return

        // Seed once only when the local table is empty.
        addWatchlistItems(
            listOf(
                WatchlistItem(
                    market = "SH",
                    code = "600000",
                    name = "浦发银行",
                    groupName = "银行",
                    order = 0,
                ),
                WatchlistItem(
                    market = "SZ",
                    code = "000001",
                    name = "平安银行",
                    groupName = "银行",
                    order = 1,
                ),
                WatchlistItem(
                    market = "SH",
                    code = "600519",
                    name = "贵州茅台",
                    groupName = "白马",
                    order = 2,
                ),
            ),
        )
    }
}

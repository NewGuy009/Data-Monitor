package com.example.mysecondapp.data.watchlist

import com.example.mysecondapp.data.local.WatchlistDao
import com.example.mysecondapp.data.local.WatchlistEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchlistRepository @Inject constructor(
    private val watchlistDao: WatchlistDao,
) {
    fun observeWatchlist(): Flow<List<WatchlistEntity>> = watchlistDao.observeAll()

    suspend fun seedSampleWatchlistIfEmpty() {
        if (watchlistDao.count() > 0) return

        watchlistDao.upsertAll(
            listOf(
                WatchlistEntity(market = "SH", code = "600000", name = "浦发银行", sortOrder = 0),
                WatchlistEntity(market = "SZ", code = "000001", name = "平安银行", sortOrder = 1),
                WatchlistEntity(market = "SH", code = "600519", name = "贵州茅台", sortOrder = 2),
            ),
        )
    }
}

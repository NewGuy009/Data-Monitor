package com.example.mysecondapp.di

import com.example.mysecondapp.data.detail.DefaultStockDetailRepository
import com.example.mysecondapp.data.market.DefaultMarketRepository
import com.example.mysecondapp.data.search.DefaultStockSearchRepository
import com.example.mysecondapp.data.search.StockSearchDataSource
import com.example.mysecondapp.data.search.TencentStockSearchDataSource
import com.example.mysecondapp.data.watchlist.DefaultWatchlistRepository
import com.example.mysecondapp.domain.repository.MarketRepository
import com.example.mysecondapp.domain.repository.StockDetailRepository
import com.example.mysecondapp.domain.repository.StockSearchRepository
import com.example.mysecondapp.domain.repository.WatchlistRepository
import com.example.mysecondapp.data.analysis.RoomAnalysisRuleRepository
import com.example.mysecondapp.data.analysis.RoomSignalRepository
import com.example.mysecondapp.domain.repository.AnalysisRuleRepository
import com.example.mysecondapp.domain.repository.SignalRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindWatchlistRepository(
        repository: DefaultWatchlistRepository,
    ): WatchlistRepository

    @Binds
    abstract fun bindMarketRepository(
        repository: DefaultMarketRepository,
    ): MarketRepository

    @Binds
    abstract fun bindStockSearchRepository(
        repository: DefaultStockSearchRepository,
    ): StockSearchRepository

    @Binds
    abstract fun bindStockSearchDataSource(
        dataSource: TencentStockSearchDataSource,
    ): StockSearchDataSource

    @Binds
    abstract fun bindStockDetailRepository(
        repository: DefaultStockDetailRepository,
    ): StockDetailRepository

    @Binds
    abstract fun bindAnalysisRuleRepository(repository: RoomAnalysisRuleRepository): AnalysisRuleRepository

    @Binds
    abstract fun bindSignalRepository(repository: RoomSignalRepository): SignalRepository
}

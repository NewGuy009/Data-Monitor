package com.example.mysecondapp.di

import android.content.Context
import androidx.room.Room
import com.example.mysecondapp.data.local.AppDatabase
import com.example.mysecondapp.data.local.KlineDao
import com.example.mysecondapp.data.local.WatchlistDao
import com.example.mysecondapp.data.local.WatchlistMigrations
import com.example.mysecondapp.data.local.AnalysisRuleDao
import com.example.mysecondapp.data.local.SignalDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "mysecondapp.db",
    )
        // M1 开始扩展自选股分组与排序字段，使用显式迁移保住用户已有数据。
        .addMigrations(WatchlistMigrations.MIGRATION_1_2)
        // M2 新增 K 线缓存表，继续沿用显式迁移链，禁止静默丢弃本地数据。
        .addMigrations(WatchlistMigrations.MIGRATION_2_3)
        .addMigrations(WatchlistMigrations.MIGRATION_3_4)
        .addMigrations(WatchlistMigrations.MIGRATION_4_5)
        .addMigrations(WatchlistMigrations.MIGRATION_5_6)
        .addMigrations(WatchlistMigrations.MIGRATION_6_7)
        .build()

    @Provides
    fun provideWatchlistDao(
        appDatabase: AppDatabase,
    ): WatchlistDao = appDatabase.watchlistDao()

    @Provides
    fun provideKlineDao(
        appDatabase: AppDatabase,
    ): KlineDao = appDatabase.klineDao()

    @Provides
    fun provideAnalysisRuleDao(appDatabase: AppDatabase): AnalysisRuleDao = appDatabase.analysisRuleDao()

    @Provides
    fun provideSignalDao(appDatabase: AppDatabase): SignalDao = appDatabase.signalDao()
}

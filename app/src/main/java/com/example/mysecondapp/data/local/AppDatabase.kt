package com.example.mysecondapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [WatchlistEntity::class, KlineEntity::class, AnalysisRuleEntity::class, SignalEntity::class],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao

    abstract fun klineDao(): KlineDao

    abstract fun analysisRuleDao(): AnalysisRuleDao

    abstract fun signalDao(): SignalDao
}

package com.example.mysecondapp.di

import android.content.Context
import androidx.room.Room
import com.example.mysecondapp.data.local.AppDatabase
import com.example.mysecondapp.data.local.WatchlistDao
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
    ).build()

    @Provides
    fun provideWatchlistDao(
        appDatabase: AppDatabase,
    ): WatchlistDao = appDatabase.watchlistDao()
}

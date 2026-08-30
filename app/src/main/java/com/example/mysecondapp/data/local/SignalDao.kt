package com.example.mysecondapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(signal: SignalEntity): Long

    @Query("SELECT * FROM analysis_signal ORDER BY created_at_millis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SignalEntity>>

    @Query("SELECT * FROM analysis_signal WHERE market = :market AND code = :code ORDER BY signal_bar_timestamp_millis DESC")
    suspend fun getForStock(market: String, code: String): List<SignalEntity>
}

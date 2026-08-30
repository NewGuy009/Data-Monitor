package com.example.mysecondapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisRuleDao {
    @Query("SELECT * FROM analysis_rule WHERE enabled = 1 ORDER BY rule_id")
    fun observeEnabled(): Flow<List<AnalysisRuleEntity>>

    @Query("SELECT * FROM analysis_rule ORDER BY rule_id")
    suspend fun getAll(): List<AnalysisRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: AnalysisRuleEntity)

    @Query("DELETE FROM analysis_rule WHERE rule_id = :ruleId")
    suspend fun delete(ruleId: String)
}

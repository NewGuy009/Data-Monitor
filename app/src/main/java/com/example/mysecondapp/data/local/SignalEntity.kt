package com.example.mysecondapp.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * 历史信号表的联合主键就是幂等身份；重复计算同一根 Bar 时由 SQLite 直接拒绝重复写入。
 */
@Entity(
    tableName = "analysis_signal",
    primaryKeys = [
        "rule_id", "rule_version", "market", "code", "period", "adjustment",
        "provider_id", "signal_bar_timestamp_millis", "direction",
    ],
    indices = [Index(value = ["market", "code", "created_at_millis"])],
)
data class SignalEntity(
    @androidx.room.ColumnInfo(name = "rule_id") val ruleId: String,
    @androidx.room.ColumnInfo(name = "rule_version") val ruleVersion: Int,
    val market: String,
    val code: String,
    val period: String,
    val adjustment: String,
    @androidx.room.ColumnInfo(name = "provider_id") val providerId: String,
    @androidx.room.ColumnInfo(name = "signal_bar_timestamp_millis") val signalBarTimestampMillis: Long,
    val direction: String,
    @androidx.room.ColumnInfo(name = "cutoff_millis") val cutoffMillis: Long,
    @androidx.room.ColumnInfo(name = "evidence_json") val evidenceJson: String,
    @androidx.room.ColumnInfo(name = "created_at_millis") val createdAtMillis: Long,
)

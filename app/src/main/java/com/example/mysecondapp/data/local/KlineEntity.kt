package com.example.mysecondapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * K 线本地缓存表。
 *
 * period 使用 CandlePeriod.name 保存，避免 Room 层依赖 domain 枚举的 TypeConverter；
 * 领域层映射时再恢复为 CandlePeriod。
 */
@Entity(
    tableName = "kline",
    primaryKeys = ["market", "code", "period", "adjustment", "provider_id", "timestamp_millis"],
    indices = [Index(value = ["market", "code", "period", "adjustment", "provider_id", "timestamp_millis"])],
)
data class KlineEntity(
    val market: String,
    val code: String,
    val period: String,
    val adjustment: String = "QFQ",
    @ColumnInfo(name = "provider_id")
    val providerId: String = "tencent",
    @ColumnInfo(name = "timestamp_millis")
    val timestampMillis: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long?,
    val turnover: Double?,
    val currency: String = "CNY",
    @ColumnInfo(name = "volume_unit")
    val volumeUnit: String = "SHARES",
    @ColumnInfo(name = "fetched_at_millis")
    val fetchedAtMillis: Long,
)

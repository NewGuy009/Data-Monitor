package com.example.mysecondapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** K 线缓存的增删查接口，所有查询都限定股票和周期，避免串数据。 */
@Dao
interface KlineDao {

    @Query(
        """
        SELECT * FROM kline
        WHERE market = :market AND code = :code AND period = :period AND adjustment = :adjustment
          AND provider_id = :providerId
        ORDER BY timestamp_millis DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecent(
        market: String,
        code: String,
        period: String,
        adjustment: String = "QFQ",
        providerId: String = "tencent",
        limit: Int,
    ): List<KlineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<KlineEntity>)

    @Query(
        """
        DELETE FROM kline
        WHERE market = :market AND code = :code AND period = :period AND adjustment = :adjustment
          AND provider_id = :providerId
          AND timestamp_millis NOT IN (
              SELECT timestamp_millis FROM kline
              WHERE market = :market AND code = :code AND period = :period AND adjustment = :adjustment
                AND provider_id = :providerId
              ORDER BY timestamp_millis DESC
              LIMIT :keepCount
          )
        """,
    )
    suspend fun deleteExceptRecent(
        market: String,
        code: String,
        period: String,
        adjustment: String = "QFQ",
        providerId: String = "tencent",
        keepCount: Int,
    )

    @Query(
        "DELETE FROM kline WHERE market = :market AND code = :code AND period = :period AND adjustment = :adjustment AND provider_id = :providerId",
    )
    suspend fun deleteSeries(
        market: String,
        code: String,
        period: String,
        adjustment: String = "QFQ",
        providerId: String = "tencent",
    )

    @Query("SELECT COUNT(*) FROM kline")
    suspend fun count(): Int
}

package com.example.mysecondapp.data.detail

import com.example.mysecondapp.data.local.KlineDao
import com.example.mysecondapp.data.local.KlineEntity
import com.example.mysecondapp.domain.model.Candle
import com.example.mysecondapp.domain.model.CandleAdjustment
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.model.DataProviderId
import com.example.mysecondapp.domain.model.DataProviders
import com.example.mysecondapp.domain.model.StockIdentity
import javax.inject.Inject

/** Repository 使用的 K 线缓存端口，隔离 Room 具体 API 便于测试和后续替换存储。 */
interface KlineCache {
    suspend fun getRecent(
        identity: StockIdentity,
        period: CandlePeriod,
        adjustment: CandleAdjustment = CandleAdjustment.QFQ,
        limit: Int,
        providerId: DataProviderId = DataProviders.TENCENT,
    ): List<Candle>

    suspend fun save(
        identity: StockIdentity,
        period: CandlePeriod,
        candles: List<Candle>,
        fetchedAtMillis: Long,
        keepCount: Int,
        providerId: DataProviderId = DataProviders.TENCENT,
    )
}

/** Room K 线缓存适配器，数据库倒序查询后恢复为图表需要的时间正序。 */
class RoomKlineCache @Inject constructor(
    private val klineDao: KlineDao,
) : KlineCache {

    override suspend fun getRecent(
        identity: StockIdentity,
        period: CandlePeriod,
        adjustment: CandleAdjustment,
        limit: Int,
        providerId: DataProviderId,
    ): List<Candle> = klineDao
        .getRecent(identity.market, identity.code, period.name, adjustment.name, providerId.value, limit)
        .map(KlineEntity::toDomainModel)
        .asReversed()

    override suspend fun save(
        identity: StockIdentity,
        period: CandlePeriod,
        candles: List<Candle>,
        fetchedAtMillis: Long,
        keepCount: Int,
        providerId: DataProviderId,
    ) {
        if (candles.isEmpty()) return
        val adjustment = candles.first().adjustment
        // Room 的序列主键包含复权模式；口径混杂或顺序异常的数据不能写入持久缓存。
        if (candles.any { candle -> candle.adjustment != adjustment } || !candles.isChronologicalAndUnique()) {
            return
        }
        klineDao.upsertAll(candles.map { candle ->
            candle.toEntity(identity, period, providerId, fetchedAtMillis)
        })
        klineDao.deleteExceptRecent(
            identity.market,
            identity.code,
            period.name,
            adjustment.name,
            providerId.value,
            keepCount,
        )
    }
}

private fun List<Candle>.isChronologicalAndUnique(): Boolean =
    map { candle -> candle.timestampMillis }.let { timestamps ->
        timestamps.size == timestamps.distinct().size &&
            timestamps.zipWithNext().all { (current, next) -> current < next }
    }

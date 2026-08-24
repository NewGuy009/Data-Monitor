package com.example.mysecondapp.data.detail

import com.example.mysecondapp.data.local.KlineEntity
import com.example.mysecondapp.domain.model.Candle
import com.example.mysecondapp.domain.model.CandleAdjustment
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.model.CurrencyCode
import com.example.mysecondapp.domain.model.DataProviderId
import com.example.mysecondapp.domain.model.QuantityUnit
import com.example.mysecondapp.domain.model.StockIdentity

/** K 线领域模型与 Room 实体之间的单向映射，隔离数据库列命名。 */
internal fun KlineEntity.toDomainModel(): Candle = Candle(
    timestampMillis = timestampMillis,
    open = open,
    high = high,
    low = low,
    close = close,
    volume = volume,
    turnover = turnover,
    adjustment = runCatching { CandleAdjustment.valueOf(adjustment) }
        .getOrDefault(CandleAdjustment.QFQ),
    currency = runCatching { CurrencyCode.valueOf(currency) }
        .getOrDefault(CurrencyCode.UNKNOWN),
    volumeUnit = runCatching { QuantityUnit.valueOf(volumeUnit) }
        .getOrDefault(QuantityUnit.UNKNOWN),
)

internal fun Candle.toEntity(
    identity: StockIdentity,
    period: CandlePeriod,
    providerId: DataProviderId,
    fetchedAtMillis: Long,
): KlineEntity = KlineEntity(
    market = identity.market,
    code = identity.code,
    period = period.name,
    timestampMillis = timestampMillis,
    open = open,
    high = high,
    low = low,
    close = close,
    adjustment = adjustment.name,
    providerId = providerId.value,
    volume = volume,
    turnover = turnover,
    currency = currency.name,
    volumeUnit = volumeUnit.name,
    fetchedAtMillis = fetchedAtMillis,
)

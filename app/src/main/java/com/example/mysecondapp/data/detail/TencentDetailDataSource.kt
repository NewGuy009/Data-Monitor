package com.example.mysecondapp.data.detail

import com.example.mysecondapp.data.network.TencentDetailApi
import com.example.mysecondapp.data.provider.ProviderSymbolMapper
import com.example.mysecondapp.data.provider.TencentSymbolMapper
import com.example.mysecondapp.domain.model.Candle
import com.example.mysecondapp.domain.model.CandleAdjustment
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.model.IntradayPoint
import com.example.mysecondapp.domain.model.IntradaySeries
import com.example.mysecondapp.domain.model.MarketDataResult
import com.example.mysecondapp.domain.model.MarketDataContract
import com.example.mysecondapp.domain.model.MarketDataContracts
import com.example.mysecondapp.domain.model.MarketError
import com.example.mysecondapp.domain.model.MarketSource
import com.example.mysecondapp.domain.model.OrderBook
import com.example.mysecondapp.domain.model.OrderBookLevel
import com.example.mysecondapp.domain.model.OrderBookSide
import com.example.mysecondapp.domain.model.StockIdentity
import com.example.mysecondapp.domain.model.TradeTick
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.SerializationException

/**
 * 腾讯分钟线与前复权 K 线数据源。
 *
 * 腾讯接口的响应字段是非版本化 JSON 数组，因此这里完成全部协议校验和映射，
 * 确保上层只会看到稳定的领域模型与 [MarketDataResult]。
 */
@Singleton
class TencentDetailDataSource @Inject constructor(
    private val tencentDetailApi: TencentDetailApi,
    private val json: Json,
) : StockDetailDataSource {

    private val symbolMapper = TencentSymbolMapper

    override val source: MarketSource = MarketSource.TENCENT

    override fun dataContract(identity: StockIdentity): MarketDataContract =
        MarketDataContracts.TENCENT_A_SHARE

    override suspend fun fetchIntraday(identity: StockIdentity): MarketDataResult<IntradaySeries> {
        val contract = dataContract(identity)
        val providerCode = identity.providerCodeForDetail(
            symbolMapper = symbolMapper,
            supportsCapability = identity.marketCapabilities()?.canLoadIntraday == true,
        ) ?: return MarketDataResult.Failure(identity.unsupportedDetailError("intraday data"))
        val fetchedAtMillis = System.currentTimeMillis()

        return runCatching {
            val rawPayload = tencentDetailApi.getIntraday(providerCode)
            if (rawPayload.isBlank()) {
                return MarketDataResult.Failure(emptyResponse(providerCode))
            }

            val intradayData = json.parseToJsonElement(rawPayload)
                .detailNode(providerCode)
                ?.get("data")
                ?.jsonObject
                ?: return MarketDataResult.Failure(parseFailure(rawPayload))
            val tradingDate = intradayData.stringValue("date")
                ?.takeIf { value -> value.length == 8 && value.all(Char::isDigit) }
                ?: return MarketDataResult.Failure(parseFailure(rawPayload))
            val rawPoints = intradayData["data"]?.jsonArray
                ?: return MarketDataResult.Failure(parseFailure(rawPayload))

            if (rawPoints.isEmpty()) {
                return MarketDataResult.Failure(emptyResponse(providerCode))
            }

            val points = rawPoints.mapNotNull { point ->
                parseIntradayPoint(
                    rawPoint = point.jsonPrimitive.contentOrNull.orEmpty(),
                    tradingDate = tradingDate,
                    contract = contract,
                )
            }
            // 分时点是一个有序的完整快照；丢弃坏点会制造“全天数据”的假象，因此整批拒绝。
            if (points.isEmpty() || points.size != rawPoints.size || !points.isIntradayChronologicalAndUnique()) {
                return MarketDataResult.Failure(parseFailure(rawPayload))
            }

            MarketDataResult.Success(
                value = IntradaySeries(
                    identity = identity,
                    tradingDate = tradingDate.toDisplayDate(),
                    points = points,
                    fetchedAtMillis = fetchedAtMillis,
                    currency = contract.currency,
                    volumeUnit = contract.volumeUnit,
                    marketTimeZone = contract.marketTimeZone,
                    tradingSession = contract.tradingSession,
                ),
                source = source,
                fetchedAtMillis = fetchedAtMillis,
            )
        }.getOrElse { error -> MarketDataResult.Failure(error.toMarketError(rawPayload = null)) }
    }

    override suspend fun fetchCandles(
        identity: StockIdentity,
        period: CandlePeriod,
        limit: Int,
    ): MarketDataResult<List<Candle>> {
        val contract = dataContract(identity)
        val providerCode = identity.providerCodeForDetail(
            symbolMapper = symbolMapper,
            supportsCapability = identity.marketCapabilities()?.canLoadCandles == true,
        ) ?: return MarketDataResult.Failure(identity.unsupportedDetailError("K-line data"))
        val fetchedAtMillis = System.currentTimeMillis()
        val protocolPeriod = period.toTencentKlinePeriod()
            ?: return MarketDataResult.Failure(
                MarketError.Unknown("Tencent minute chart is fetched through the intraday endpoint."),
            )
        val safeLimit = limit.coerceIn(MIN_CANDLE_LIMIT, MAX_CANDLE_LIMIT)

        return runCatching {
            // param 中的前复权标记 qfq 保证日、周、月线的价格口径一致。
            val parameter = "$providerCode,$protocolPeriod,,,$safeLimit,qfq"
            val rawPayload = tencentDetailApi.getForwardAdjustedKlines(parameter)
            if (rawPayload.isBlank()) {
                return MarketDataResult.Failure(emptyResponse(providerCode))
            }

            val node = json.parseToJsonElement(rawPayload).detailNode(providerCode)
                ?: return MarketDataResult.Failure(parseFailure(rawPayload))
            val adjustedKey = period.toTencentResponseKey()
            val rawKey = period.toTencentRawResponseKey()
            val adjustedCandles = node[adjustedKey]?.jsonArray
            val rawCandles = node[rawKey]?.jsonArray
            val response = when {
                !adjustedCandles.isNullOrEmpty() -> adjustedCandles to CandleAdjustment.QFQ
                !rawCandles.isNullOrEmpty() -> rawCandles to CandleAdjustment.RAW
                adjustedCandles != null -> adjustedCandles to CandleAdjustment.QFQ
                rawCandles != null -> rawCandles to CandleAdjustment.RAW
                else -> return MarketDataResult.Failure(parseFailure(rawPayload))
            }
            val responseCandles = response.first
            if (responseCandles.isEmpty()) {
                return MarketDataResult.Failure(emptyResponse(providerCode))
            }

            val candles = responseCandles.mapNotNull { row ->
                parseCandle(row.jsonArray, response.second, contract)
            }
            if (candles.isEmpty()) {
                return MarketDataResult.Failure(parseFailure(rawPayload))
            }
            if (!candles.isChronologicalAndUnique()) {
                return MarketDataResult.Failure(parseFailure(rawPayload))
            }

            MarketDataResult.Success(
                value = candles.takeLast(safeLimit),
                source = source,
                fetchedAtMillis = fetchedAtMillis,
            )
        }.getOrElse { error -> MarketDataResult.Failure(error.toMarketError(rawPayload = null)) }
    }

    override suspend fun fetchOrderBook(identity: StockIdentity): MarketDataResult<OrderBook> {
        val providerCode = identity.providerCodeForDetail(
            symbolMapper = symbolMapper,
            supportsCapability = identity.marketCapabilities()?.canLoadQuote == true,
        ) ?: return MarketDataResult.Failure(identity.unsupportedDetailError("order book data"))
        val fetchedAtMillis = System.currentTimeMillis()

        return runCatching {
            // fqkline/get 的 qt 数组包含快照和十档委托字段，首期只取稳定的五档盘口。
            val rawPayload = tencentDetailApi.getForwardAdjustedKlines(
                "$providerCode,day,,,1,qfq",
            )
            if (rawPayload.isBlank()) {
                return MarketDataResult.Failure(emptyResponse(providerCode))
            }

            val node = json.parseToJsonElement(rawPayload).detailNode(providerCode)
                ?: return MarketDataResult.Failure(parseFailure(rawPayload))
            val quoteFields = node["qt"]?.jsonObject?.get(providerCode)?.jsonArray
                ?: return MarketDataResult.Failure(parseFailure(rawPayload))
            val orderBook = parseOrderBook(identity, quoteFields, fetchedAtMillis)
                ?: return MarketDataResult.Failure(parseFailure(rawPayload))

            if (orderBook.bids.isEmpty() && orderBook.asks.isEmpty()) {
                MarketDataResult.Failure(emptyResponse(providerCode))
            } else {
                MarketDataResult.Success(orderBook, source, fetchedAtMillis)
            }
        }.getOrElse { error -> MarketDataResult.Failure(error.toMarketError(rawPayload = null)) }
    }

    override suspend fun fetchTradeTicks(
        identity: StockIdentity,
        limit: Int,
    ): MarketDataResult<List<TradeTick>> {
        // 腾讯当前公开分钟接口的 mx_price 在实测中为空；分钟汇总不能冒充逐笔成交明细。
        return MarketDataResult.Success(
            value = emptyList(),
            source = source,
            fetchedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun parseIntradayPoint(
        rawPoint: String,
        tradingDate: String,
        contract: MarketDataContract,
    ): IntradayPoint? {
        val fields = rawPoint.trim().split(WHITESPACE)
        if (fields.size < MIN_INTRADAY_FIELD_COUNT) return null

        val timestampMillis = parseShanghaiTimestamp(tradingDate, fields[0]) ?: return null
        val price = fields[1].toDoubleOrNull() ?: return null
        val rawCumulativeVolume = fields[2].toLongOrNull()
        val cumulativeTurnover = fields[3].toDoubleOrNull()
        if (!price.isFinite() || price <= 0.0) return null
        if (rawCumulativeVolume != null && rawCumulativeVolume < 0L) return null
        if (cumulativeTurnover != null && (!cumulativeTurnover.isFinite() || cumulativeTurnover < 0.0)) return null
        val cumulativeVolume = rawCumulativeVolume?.let { volume ->
            if (contract.rawVolumeUnit == com.example.mysecondapp.domain.model.QuantityUnit.LOTS) {
                volume * SHARES_PER_LOT
            } else {
                volume
            }
        }

        return IntradayPoint(
            timestampMillis = timestampMillis,
            price = price,
            cumulativeVolume = cumulativeVolume,
            cumulativeTurnover = cumulativeTurnover,
            // 腾讯分钟线的量以“手”为单位，换算为股后才能正确得到均价。
            averagePrice = cumulativeTurnover?.let { turnover ->
                cumulativeVolume?.takeIf { it > 0 }?.let { volume -> turnover / volume }
            },
        )
    }

    private fun parseCandle(
        row: JsonArray,
        adjustment: CandleAdjustment,
        contract: MarketDataContract,
    ): Candle? {
        if (row.size < MIN_CANDLE_FIELD_COUNT) return null

        val timestampMillis = parseShanghaiDate(row.contentAt(0)) ?: return null
        val open = row.contentAt(1)?.toDoubleOrNull() ?: return null
        val close = row.contentAt(2)?.toDoubleOrNull() ?: return null
        val high = row.contentAt(3)?.toDoubleOrNull() ?: return null
        val low = row.contentAt(4)?.toDoubleOrNull() ?: return null

        if (!open.isFinite() || !high.isFinite() || !low.isFinite() || !close.isFinite()) return null
        if (open <= 0.0 || high <= 0.0 || low <= 0.0 || close <= 0.0) return null
        if (high < maxOf(open, close) || low > minOf(open, close) || high < low) return null

        val rawVolume = row.contentAt(5)?.toDoubleOrNull()?.takeIf { it >= 0.0 }
        return Candle(
            timestampMillis = timestampMillis,
            open = open,
            high = high,
            low = low,
            close = close,
            // 腾讯 K 线成交量以“手”为单位；领域层统一以“股”保存，与 M1 Quote 保持一致。
            // Tencent qfq rows use lots; ordinary day/week/month rows use shares.
            volume = rawVolume?.let { volume ->
                val normalized = if (adjustment == CandleAdjustment.QFQ) volume * SHARES_PER_LOT else volume
                normalized.takeIf { it <= Long.MAX_VALUE }?.toLong()
            },
            adjustment = adjustment,
            currency = contract.currency,
            volumeUnit = contract.volumeUnit,
        )
    }

    private fun parseOrderBook(
        identity: StockIdentity,
        fields: JsonArray,
        fetchedAtMillis: Long,
    ): OrderBook? {
        val bids = parseOrderBookLevels(
            fields = fields,
            side = OrderBookSide.BID,
            priceStartIndex = BID_PRICE_START_INDEX,
        )
        val asks = parseOrderBookLevels(
            fields = fields,
            side = OrderBookSide.ASK,
            priceStartIndex = ASK_PRICE_START_INDEX,
        )
        return OrderBook(
            identity = identity,
            bids = bids,
            asks = asks,
            updatedAtMillis = fetchedAtMillis,
        )
    }

    private fun parseOrderBookLevels(
        fields: JsonArray,
        side: OrderBookSide,
        priceStartIndex: Int,
    ): List<OrderBookLevel> = (0 until ORDER_BOOK_LEVEL_COUNT).mapNotNull { offset ->
        val price = fields.contentAt(priceStartIndex + offset * 2)?.toDoubleOrNull()
            ?: return@mapNotNull null
        val quantityHands = fields.contentAt(priceStartIndex + offset * 2 + 1)?.toLongOrNull()
            ?: return@mapNotNull null
        if (price <= 0.0 || quantityHands < 0L) return@mapNotNull null
        OrderBookLevel(
            side = side,
            level = offset + 1,
            price = price,
            // 腾讯盘口量以手为单位，领域层统一为股。
            quantity = quantityHands * SHARES_PER_LOT,
        )
    }

    private fun emptyResponse(providerCode: String): MarketError.EmptyResponse =
        MarketError.EmptyResponse(source = source, stockCodes = listOf(providerCode))

    private fun parseFailure(rawPayload: String): MarketError.ParseFailure =
        MarketError.ParseFailure(source = source, rawPayloadPreview = rawPayload.take(RAW_PAYLOAD_PREVIEW_LENGTH))

    private companion object {
        const val MIN_CANDLE_LIMIT = 1
        const val MAX_CANDLE_LIMIT = 1_000
        const val MIN_INTRADAY_FIELD_COUNT = 4
        const val MIN_CANDLE_FIELD_COUNT = 6
        const val RAW_PAYLOAD_PREVIEW_LENGTH = 160
        const val ORDER_BOOK_LEVEL_COUNT = 5
        const val BID_PRICE_START_INDEX = 9
        const val ASK_PRICE_START_INDEX = 19
        const val SHARES_PER_LOT = 100L
        val WHITESPACE = Regex("\\s+")
        val SHANGHAI_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }
}

private fun kotlinx.serialization.json.JsonElement.detailNode(providerCode: String): JsonObject? =
    jsonObject["data"]?.jsonObject?.get(providerCode)?.jsonObject

private fun JsonObject.stringValue(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonArray.contentAt(index: Int): String? = getOrNull(index)?.jsonPrimitive?.contentOrNull

private fun CandlePeriod.toTencentKlinePeriod(): String? = when (this) {
    CandlePeriod.DAY -> "day"
    CandlePeriod.WEEK -> "week"
    CandlePeriod.MONTH -> "month"
    CandlePeriod.MINUTE -> null
}

private fun CandlePeriod.toTencentResponseKey(): String = when (this) {
    CandlePeriod.DAY -> "qfqday"
    CandlePeriod.WEEK -> "qfqweek"
    CandlePeriod.MONTH -> "qfqmonth"
    CandlePeriod.MINUTE -> error("Minute candles are not returned by fqkline/get.")
}

private fun CandlePeriod.toTencentRawResponseKey(): String = when (this) {
    CandlePeriod.DAY -> "day"
    CandlePeriod.WEEK -> "week"
    CandlePeriod.MONTH -> "month"
    CandlePeriod.MINUTE -> error("Minute candles are not returned by fqkline/get.")
}

private fun List<Candle>.isChronologicalAndUnique(): Boolean =
    map { candle -> candle.timestampMillis }.let { timestamps ->
        timestamps.size == timestamps.distinct().size &&
            timestamps.zipWithNext().all { (current, next) -> current < next }
    }

private fun List<IntradayPoint>.isIntradayChronologicalAndUnique(): Boolean =
    map { point -> point.timestampMillis }.let { timestamps ->
        timestamps.size == timestamps.distinct().size &&
            timestamps.zipWithNext().all { (current, next) -> current < next }
    }

private fun String.toDisplayDate(): String? =
    takeIf { value -> value.length == 8 && value.all(Char::isDigit) }
        ?.let { value -> "${value.substring(0, 4)}-${value.substring(4, 6)}-${value.substring(6, 8)}" }

private fun parseShanghaiTimestamp(date: String, time: String): Long? =
    time.replace(":", "").takeIf { it.length == 4 && it.all(Char::isDigit) }
        ?.let { normalizedTime -> parseShanghaiDateTime("$date$normalizedTime", "yyyyMMddHHmm") }

private fun parseShanghaiDate(date: String?): Long? = date?.let { parseShanghaiDateTime(it, "yyyy-MM-dd") }

private fun parseShanghaiDateTime(value: String, pattern: String): Long? = runCatching {
    SimpleDateFormat(pattern, Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        isLenient = false
    }.parse(value)?.time
}.getOrNull()

/** 将协议解析异常与传输异常区分开，供 UI 展示更准确的失败状态。 */
private fun Throwable.toMarketError(rawPayload: String?): MarketError = when (this) {
    is SerializationException,
    is IllegalArgumentException,
    is ClassCastException,
    -> MarketError.ParseFailure(
        source = MarketSource.TENCENT,
        rawPayloadPreview = rawPayload?.take(160).orEmpty(),
    )

    else -> MarketError.Network(message)
}

private fun StockIdentity.providerCodeForDetail(
    symbolMapper: ProviderSymbolMapper,
    supportsCapability: Boolean,
): String? = symbolMapper.toProviderSymbol(this)?.takeIf { supportsCapability }

private fun StockIdentity.unsupportedDetailError(capabilityName: String): MarketError.UnsupportedSymbol =
    MarketError.UnsupportedSymbol(
        market = market,
        code = code,
        reason = "$capabilityName is not supported for this market/code.",
    )

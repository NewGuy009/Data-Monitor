package com.example.mysecondapp.data.market

import com.example.mysecondapp.data.network.TencentStockApi
import com.example.mysecondapp.domain.model.MarketDataResult
import com.example.mysecondapp.domain.model.MarketDataContract
import com.example.mysecondapp.domain.model.MarketDataContracts
import com.example.mysecondapp.domain.model.MarketError
import com.example.mysecondapp.domain.model.MarketSource
import com.example.mysecondapp.domain.model.Quote
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class TencentMarketDataSource @Inject constructor(
    private val tencentStockApi: TencentStockApi,
) : MarketDataSource {

    override val source: MarketSource = MarketSource.TENCENT
    override val dataContract: MarketDataContract = MarketDataContracts.A_SHARE

    override suspend fun fetchQuotes(stockCodes: List<String>): MarketDataResult<List<Quote>> {
        val providerCodes = stockCodes.distinct()
        val fetchedAtMillis = System.currentTimeMillis()

        return runCatching {
            val rawPayload = tencentStockApi.getQuotes(providerCodes.joinToString(","))
            if (rawPayload.isBlank()) {
                return MarketDataResult.Failure(
                    MarketError.EmptyResponse(
                        source = source,
                        stockCodes = providerCodes,
                    ),
                )
            }

            val quotes = rawPayload
                .lineSequence()
                .filter { line -> line.isNotBlank() }
                .mapNotNull { line -> parseQuote(line, fetchedAtMillis) }
                .toList()

            if (quotes.isEmpty()) {
                MarketDataResult.Failure(
                    MarketError.ParseFailure(
                        source = source,
                        rawPayloadPreview = rawPayload.take(160),
                    ),
                )
            } else {
                MarketDataResult.Success(
                    value = quotes,
                    source = source,
                    fetchedAtMillis = fetchedAtMillis,
                )
            }
        }.getOrElse { error ->
            MarketDataResult.Failure(
                MarketError.Network(error.message),
            )
        }
    }

    private fun parseQuote(
        line: String,
        fetchedAtMillis: Long,
    ): Quote? {
        val payload = line.substringAfter("=\"").substringBeforeLast("\"")
        val fields = payload.split("~")
        if (fields.size <= 37) return null

        // 腾讯字符串协议字段很多，这里只提取 M1 列表页马上要用到的核心报价字段。
        val latestPrice = fields.getOrNull(3)?.toDoubleOrNull() ?: return null
        val previousClosePrice = fields.getOrNull(4)?.toDoubleOrNull() ?: return null
        val openPrice = fields.getOrNull(5)?.toDoubleOrNull() ?: return null
        val highPrice = fields.getOrNull(33)?.toDoubleOrNull() ?: return null
        val lowPrice = fields.getOrNull(34)?.toDoubleOrNull() ?: return null
        val changeAmount = latestPrice - previousClosePrice
        val changePercent = if (abs(previousClosePrice) < 0.000001) 0.0 else {
            (changeAmount / previousClosePrice) * 100
        }

        return Quote(
            market = inferMarket(fields.getOrNull(2).orEmpty()),
            code = fields.getOrNull(2).orEmpty(),
            name = fields.getOrNull(1).orEmpty(),
            latestPrice = latestPrice,
            previousClosePrice = previousClosePrice,
            openPrice = openPrice,
            highPrice = highPrice,
            lowPrice = lowPrice,
            changeAmount = changeAmount,
            changePercent = changePercent,
            volume = fields.getOrNull(36)?.toLongOrNull()?.times(100),
            turnover = fields.getOrNull(37)?.toDoubleOrNull()?.times(10_000),
            currency = dataContract.currency,
            volumeUnit = dataContract.volumeUnit,
            marketTimeZone = dataContract.marketTimeZone,
            tradingSession = dataContract.tradingSession,
            // 两个外部源的时间格式不同，先统一用抓取时间作为列表刷新时刻。
            updatedAtMillis = fetchedAtMillis,
            source = source,
        )
    }

    private fun inferMarket(code: String): String = when {
        code.startsWith("6") -> "SH"
        code.startsWith("0") || code.startsWith("3") -> "SZ"
        code.startsWith("4") || code.startsWith("8") || code.startsWith("9") -> "BJ"
        else -> "UNKNOWN"
    }
}

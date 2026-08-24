package com.example.mysecondapp.data.market

import com.example.mysecondapp.data.network.SinaStockApi
import com.example.mysecondapp.domain.model.MarketDataResult
import com.example.mysecondapp.domain.model.MarketDataContract
import com.example.mysecondapp.domain.model.MarketDataContracts
import com.example.mysecondapp.domain.model.MarketError
import com.example.mysecondapp.domain.model.MarketSource
import com.example.mysecondapp.domain.model.Quote
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class SinaMarketDataSource @Inject constructor(
    private val sinaStockApi: SinaStockApi,
) : MarketDataSource {

    override val source: MarketSource = MarketSource.SINA
    override val dataContract: MarketDataContract = MarketDataContracts.A_SHARE

    override suspend fun fetchQuotes(stockCodes: List<String>): MarketDataResult<List<Quote>> {
        val providerCodes = stockCodes.distinct()
        val fetchedAtMillis = System.currentTimeMillis()

        return runCatching {
            val body = sinaStockApi.getQuotes(providerCodes.joinToString(","))
            val rawPayload = String(body.bytes(), Charset.forName("GBK"))
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
        val variableName = line.substringBefore("=").removePrefix("var hq_str_")
        val payload = line.substringAfter("=\"").substringBeforeLast("\"")
        val fields = payload.split(",")
        if (fields.size <= 31) return null

        val latestPrice = fields.getOrNull(3)?.toDoubleOrNull() ?: return null
        val previousClosePrice = fields.getOrNull(2)?.toDoubleOrNull() ?: return null
        val openPrice = fields.getOrNull(1)?.toDoubleOrNull() ?: return null
        val highPrice = fields.getOrNull(4)?.toDoubleOrNull() ?: return null
        val lowPrice = fields.getOrNull(5)?.toDoubleOrNull() ?: return null
        val changeAmount = latestPrice - previousClosePrice
        val changePercent = if (abs(previousClosePrice) < 0.000001) 0.0 else {
            (changeAmount / previousClosePrice) * 100
        }

        return Quote(
            market = variableName.take(2).uppercase(),
            code = variableName.drop(2),
            name = fields.getOrNull(0).orEmpty(),
            latestPrice = latestPrice,
            previousClosePrice = previousClosePrice,
            openPrice = openPrice,
            highPrice = highPrice,
            lowPrice = lowPrice,
            changeAmount = changeAmount,
            changePercent = changePercent,
            volume = fields.getOrNull(8)?.toLongOrNull(),
            turnover = fields.getOrNull(9)?.toDoubleOrNull(),
            currency = dataContract.currency,
            volumeUnit = dataContract.volumeUnit,
            marketTimeZone = dataContract.marketTimeZone,
            tradingSession = dataContract.tradingSession,
            // 新浪返回的是日期+时间拆分字段，先统一归一化成抓取时间，后续再细化解析。
            updatedAtMillis = fetchedAtMillis,
            source = source,
        )
    }
}

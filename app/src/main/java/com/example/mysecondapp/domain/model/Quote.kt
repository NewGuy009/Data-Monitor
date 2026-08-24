package com.example.mysecondapp.domain.model

data class Quote(
    val market: String,
    val code: String,
    val name: String,
    val latestPrice: Double,
    val previousClosePrice: Double,
    val openPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val changeAmount: Double,
    val changePercent: Double,
    val volume: Long?,
    val turnover: Double?,
    val updatedAtMillis: Long,
    val source: MarketSource,
    val providerId: DataProviderId? = source.toLegacyProviderId(),
    val currency: CurrencyCode = CurrencyCode.CNY,
    val volumeUnit: QuantityUnit = QuantityUnit.SHARES,
    val marketTimeZone: String? = "Asia/Shanghai",
    val tradingSession: TradingSession? = TradingSession.A_SHARE,
)

package com.example.mysecondapp.domain.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** ISO 4217-style currency codes used by normalized price and turnover values. */
enum class CurrencyCode {
    CNY,
    USD,
    KRW,
    UNKNOWN,
}

/** Unit of the normalized volume field exposed to the domain and UI. */
enum class QuantityUnit {
    SHARES,
    LOTS,
    CONTRACTS,
    UNKNOWN,
}

data class TradingWindow(
    val openMinuteOfDay: Int,
    val closeMinuteOfDay: Int,
) {
    init {
        require(openMinuteOfDay in 0..1_439)
        require(closeMinuteOfDay in 1..1_440)
        require(openMinuteOfDay < closeMinuteOfDay)
    }
}

/** Market-local trading windows used for intraday coverage and future calendars. */
data class TradingSession(
    val timeZoneId: String,
    val regularWindows: List<TradingWindow>,
    val completionMinuteOfDay: Int = regularWindows.maxOfOrNull { it.closeMinuteOfDay } ?: 0,
) {
    init {
        require(timeZoneId.isNotBlank())
        require(regularWindows.isNotEmpty())
        require(completionMinuteOfDay in 1..1_440)
    }

    fun localDateAt(timestampMillis: Long): String = format(
        pattern = "yyyy-MM-dd",
        timestampMillis = timestampMillis,
    )

    fun completionTimestampMillis(tradingDate: String): Long? = runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone(timeZoneId)
            isLenient = false
        }.parse(
            "$tradingDate ${"%02d:%02d".format(
                Locale.ROOT,
                completionMinuteOfDay / 60,
                completionMinuteOfDay % 60,
            )}",
        )?.time
    }.getOrNull()

    private fun format(pattern: String, timestampMillis: Long): String =
        SimpleDateFormat(pattern, Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone(timeZoneId)
        }.format(Date(timestampMillis))

    companion object {
        val A_SHARE = TradingSession(
            timeZoneId = "Asia/Shanghai",
            regularWindows = listOf(
                TradingWindow(9 * 60 + 30, 11 * 60 + 30),
                TradingWindow(13 * 60, 15 * 60),
            ),
        )
        val US_EQUITY = TradingSession(
            timeZoneId = "America/New_York",
            regularWindows = listOf(TradingWindow(9 * 60 + 30, 16 * 60)),
        )
        val KR_EQUITY = TradingSession(
            timeZoneId = "Asia/Seoul",
            regularWindows = listOf(TradingWindow(9 * 60, 15 * 60 + 30)),
        )
    }
}

data class MarketDataContract(
    val currency: CurrencyCode,
    /** Unit exposed after the provider payload has been normalized. */
    val volumeUnit: QuantityUnit,
    /** Unit in the raw provider payload before normalization. */
    val rawVolumeUnit: QuantityUnit = volumeUnit,
    val marketTimeZone: String,
    val tradingSession: TradingSession,
    /** Null means the value has no K-line adjustment mode, such as a live quote. */
    val candleAdjustment: CandleAdjustment? = null,
)

object MarketDataContracts {
    val A_SHARE = MarketDataContract(
        currency = CurrencyCode.CNY,
        volumeUnit = QuantityUnit.SHARES,
        marketTimeZone = "Asia/Shanghai",
        tradingSession = TradingSession.A_SHARE,
    )
    val TENCENT_A_SHARE = A_SHARE.copy(rawVolumeUnit = QuantityUnit.LOTS)
    val US_EQUITY = MarketDataContract(
        currency = CurrencyCode.USD,
        volumeUnit = QuantityUnit.SHARES,
        marketTimeZone = "America/New_York",
        tradingSession = TradingSession.US_EQUITY,
    )
    val KR_EQUITY = MarketDataContract(
        currency = CurrencyCode.KRW,
        volumeUnit = QuantityUnit.SHARES,
        marketTimeZone = "Asia/Seoul",
        tradingSession = TradingSession.KR_EQUITY,
    )
    val UNKNOWN = MarketDataContract(
        currency = CurrencyCode.UNKNOWN,
        volumeUnit = QuantityUnit.UNKNOWN,
        marketTimeZone = "UTC",
        tradingSession = TradingSession(
            timeZoneId = "UTC",
            regularWindows = listOf(TradingWindow(0, 1)),
        ),
    )

    fun forMarket(market: String): MarketDataContract = when {
        market.equals("SH", ignoreCase = true) ||
            market.equals("SZ", ignoreCase = true) ||
            market.equals("BJ", ignoreCase = true) -> A_SHARE
        market.startsWith("US-", ignoreCase = true) -> US_EQUITY
        market.startsWith("KR-", ignoreCase = true) -> KR_EQUITY
        else -> UNKNOWN
    }
}

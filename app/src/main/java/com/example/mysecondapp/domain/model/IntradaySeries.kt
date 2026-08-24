package com.example.mysecondapp.domain.model


/** 一只股票某个交易日的分时序列。 */
data class IntradaySeries(
    val identity: StockIdentity,
    val tradingDate: String?,
    val points: List<IntradayPoint>,
    val fetchedAtMillis: Long,
    val currency: CurrencyCode = CurrencyCode.CNY,
    val volumeUnit: QuantityUnit = QuantityUnit.SHARES,
    val marketTimeZone: String = "Asia/Shanghai",
    val tradingSession: TradingSession = TradingSession.A_SHARE,
) {
    val firstTimestampMillis: Long?
        get() = points.firstOrNull()?.timestampMillis

    val lastTimestampMillis: Long?
        get() = points.lastOrNull()?.timestampMillis

    val pointCount: Int
        get() = points.size

    /**
     * 分时接口只返回已经产生的点，因此状态必须结合查看时刻判断，而不能由点数推断全天完整。
     * tradingDate 早于当前上海日期时，只能标识为最近可用交易日；当天数据在收盘点之后才算完整。
     */
    fun coverageAt(nowMillis: Long): IntradayCoverage {
        val date = tradingDate
        val lastTimestamp = lastTimestampMillis
        return when {
            date == null || lastTimestamp == null -> IntradayCoverage.UNKNOWN
            date != tradingSession.localDateAt(nowMillis) -> IntradayCoverage.LATEST_AVAILABLE_TRADING_DAY
            lastTimestamp >= tradingSession.completionTimestampMillis(date).orElseMinValue() ->
                IntradayCoverage.COMPLETE_TRADING_DAY
            else -> IntradayCoverage.PARTIAL_TRADING_DAY
        }
    }
}

enum class IntradayCoverage {
    PARTIAL_TRADING_DAY,
    COMPLETE_TRADING_DAY,
    LATEST_AVAILABLE_TRADING_DAY,
    UNKNOWN,
}

private fun Long?.orElseMinValue(): Long = this ?: Long.MIN_VALUE

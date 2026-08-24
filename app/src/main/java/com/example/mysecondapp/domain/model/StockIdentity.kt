package com.example.mysecondapp.domain.model

/**
 * 个股在详情链路中的稳定身份。
 *
 * 详情接口、内存缓存和 Room 缓存都会使用同一组市场与代码，避免只用代码时
 * 把不同市场的同号证券误认为同一只股票。
 */
data class StockIdentity(
    val market: String,
    val code: String,
) {
    /** 统一给缓存和日志使用的业务键。 */
    fun cacheKey(): String = "${market.trim().uppercase()}-${code.trim()}"

    /** 腾讯接口使用的小写市场前缀加证券代码。 */
    /**
     * Tencent requires a validated market prefix plus a six-digit code. Callers that can
     * surface an unsupported-symbol state should use [providerCodeOrNull].
     */
     @Deprecated("Use the selected MarketDataProvider.symbolMapper instead.")
     fun providerCodeOrNull(): String? = StockMarket.fromMarketCode(market)?.providerCodeOrNull(code)

     @Deprecated("Use the selected MarketDataProvider.symbolMapper instead.")
     fun providerCode(): String = requireNotNull(providerCodeOrNull()) {
        "Unsupported Tencent symbol: $market $code"
    }

    fun marketCapabilities(): StockMarketCapabilities? = StockMarket.fromMarketCode(market)?.capabilities
}

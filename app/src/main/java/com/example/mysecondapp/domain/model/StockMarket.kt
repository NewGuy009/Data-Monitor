package com.example.mysecondapp.domain.model

/**
 * Supported market identifiers and their Tencent provider capabilities.
 * Keeping this rule in the domain layer prevents each feature from maintaining its own map.
 */
enum class StockMarket(
    val marketCode: String,
    val tencentPrefix: String,
    val capabilities: StockMarketCapabilities,
) {
    SH(
        marketCode = "SH",
        tencentPrefix = "sh",
        capabilities = StockMarketCapabilities(true, true, true, true),
    ),
    SZ(
        marketCode = "SZ",
        tencentPrefix = "sz",
        capabilities = StockMarketCapabilities(true, true, true, true),
    ),
    BJ(
        marketCode = "BJ",
        tencentPrefix = "bj",
        capabilities = StockMarketCapabilities(false, true, false, false),
    ),
    ;

    /** Returns a provider code only when the market/code combination is valid. */
    fun providerCodeOrNull(code: String): String? = code.trim()
        .takeIf(::isValidCode)
        ?.let { validCode -> tencentPrefix + validCode }

    private fun isValidCode(code: String): Boolean = when (this) {
        SH -> code.matches(SH_CODE_PATTERN)
        SZ -> code.matches(SZ_CODE_PATTERN)
        BJ -> code.matches(BJ_CODE_PATTERN)
    }

    companion object {
        fun fromMarketCode(marketCode: String): StockMarket? = entries.firstOrNull { market ->
            market.marketCode.equals(marketCode.trim(), ignoreCase = true)
        }

        fun fromTencentPrefix(prefix: String): StockMarket? = entries.firstOrNull { market ->
            market.tencentPrefix.equals(prefix.trim(), ignoreCase = true)
        }

        private val SH_CODE_PATTERN = Regex("6\\d{5}")
        private val SZ_CODE_PATTERN = Regex("[023]\\d{5}")
        private val BJ_CODE_PATTERN = Regex("[489]\\d{5}")
    }
}

data class StockMarketCapabilities(
    val canSearch: Boolean,
    val canLoadQuote: Boolean,
    val canLoadIntraday: Boolean,
    val canLoadCandles: Boolean,
)

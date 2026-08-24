package com.example.mysecondapp.data.provider

import com.example.mysecondapp.domain.model.StockIdentity
import com.example.mysecondapp.domain.model.StockMarket

/** Converts the app's stable security identity to and from one provider's symbol format. */
interface ProviderSymbolMapper {
    fun toProviderSymbol(identity: StockIdentity): String?

    fun toIdentity(providerSymbol: String): StockIdentity?
}

/** Mapper used only by compatibility constructors and test doubles that already accept cache keys. */
object LegacyMarketSymbolMapper : ProviderSymbolMapper {
    override fun toProviderSymbol(identity: StockIdentity): String =
        identity.market.trim().lowercase() + identity.code.trim()

    override fun toIdentity(providerSymbol: String): StockIdentity? = null
}

/** Tencent's A-share APIs use a lowercase market prefix followed by six digits. */
object TencentSymbolMapper : ProviderSymbolMapper {
    override fun toProviderSymbol(identity: StockIdentity): String? =
        StockMarket.fromMarketCode(identity.market)?.providerCodeOrNull(identity.code)

    override fun toIdentity(providerSymbol: String): StockIdentity? {
        val match = SYMBOL_PATTERN.matchEntire(providerSymbol.trim()) ?: return null
        val market = StockMarket.fromTencentPrefix(match.groupValues[1]) ?: return null
        val code = match.groupValues[2]
        if (market.providerCodeOrNull(code) == null) return null
        return StockIdentity(market.marketCode, code)
    }

    private val SYMBOL_PATTERN = Regex("(?i)(sh|sz|bj)(\\d{6})")
}

/** Sina's quote endpoint currently uses the same symbol shape for mainland A-shares. */
object SinaSymbolMapper : ProviderSymbolMapper {
    override fun toProviderSymbol(identity: StockIdentity): String? =
        TencentSymbolMapper.toProviderSymbol(identity)

    override fun toIdentity(providerSymbol: String): StockIdentity? =
        TencentSymbolMapper.toIdentity(providerSymbol)
}

/** Reserved US provider format. No network endpoint is bound until a real US provider is selected. */
object UsSymbolMapper : ProviderSymbolMapper {
    override fun toProviderSymbol(identity: StockIdentity): String? {
        val market = identity.market.trim().uppercase()
        val symbol = identity.code.trim().uppercase()
        return symbol.takeIf { market in US_MARKETS && it.matches(US_SYMBOL_PATTERN) }
            ?.let { validSymbol -> "$market:$validSymbol" }
    }

    override fun toIdentity(providerSymbol: String): StockIdentity? {
        val match = PROVIDER_SYMBOL_PATTERN.matchEntire(providerSymbol.trim()) ?: return null
        val market = match.groupValues[1].uppercase()
        val symbol = match.groupValues[2].uppercase()
        return symbol.takeIf { market in US_MARKETS && it.matches(US_SYMBOL_PATTERN) }
            ?.let { validSymbol -> StockIdentity(market, validSymbol) }
    }

    private val US_MARKETS = setOf("US-NASDAQ", "US-NYSE")
    private val US_SYMBOL_PATTERN = Regex("[A-Z][A-Z0-9.-]{0,14}")
    private val PROVIDER_SYMBOL_PATTERN = Regex("(?i)(US-NASDAQ|US-NYSE):([A-Z0-9.-]+)")
}

/** Reserved Korean provider format. The six-digit local symbol is intentionally preserved. */
object KoreanSymbolMapper : ProviderSymbolMapper {
    override fun toProviderSymbol(identity: StockIdentity): String? {
        val market = identity.market.trim().uppercase()
        val code = identity.code.trim()
        return code.takeIf { market in KOREAN_MARKETS && it.matches(KOREAN_CODE_PATTERN) }
            ?.let { validCode -> "$market:$validCode" }
    }

    override fun toIdentity(providerSymbol: String): StockIdentity? {
        val match = PROVIDER_SYMBOL_PATTERN.matchEntire(providerSymbol.trim()) ?: return null
        val market = match.groupValues[1].uppercase()
        val code = match.groupValues[2]
        return code.takeIf { market in KOREAN_MARKETS && it.matches(KOREAN_CODE_PATTERN) }
            ?.let { validCode -> StockIdentity(market, validCode) }
    }

    private val KOREAN_MARKETS = setOf("KR-KOSPI", "KR-KOSDAQ")
    private val KOREAN_CODE_PATTERN = Regex("\\d{6}")
    private val PROVIDER_SYMBOL_PATTERN = Regex("(?i)(KR-KOSPI|KR-KOSDAQ):(\\d{6})")
}

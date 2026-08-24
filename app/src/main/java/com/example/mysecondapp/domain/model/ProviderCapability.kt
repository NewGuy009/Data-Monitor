package com.example.mysecondapp.domain.model

enum class ProviderCapability {
    QUOTE,
    SEARCH,
    INTRADAY,
    CANDLES,
    ORDER_BOOK,
    TRADE_TICKS,
}

/** Provider capabilities are market-scoped because one provider may support quote but not detail for a market. */
data class ProviderCapabilities(
    val byMarket: Map<String, Set<ProviderCapability>>,
) {
    fun supports(market: String, capability: ProviderCapability): Boolean =
        capability in byMarket[market.trim().uppercase()].orEmpty()

    fun supportedMarkets(capability: ProviderCapability): Set<String> =
        byMarket.filterValues { capabilities -> capability in capabilities }.keys
}

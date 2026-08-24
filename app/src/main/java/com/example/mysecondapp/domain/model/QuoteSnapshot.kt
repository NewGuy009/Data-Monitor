package com.example.mysecondapp.domain.model

data class QuoteSnapshot(
    val watchlistItem: WatchlistItem,
    val quote: Quote?,
    val refreshedAtMillis: Long?,
    val source: MarketSource?,
    val providerId: DataProviderId? = quote?.providerId,
    val error: MarketError? = null,
) {
    val isLoaded: Boolean
        get() = quote != null
}

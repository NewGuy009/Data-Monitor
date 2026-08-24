package com.example.mysecondapp.domain.model

/** Stable identifier for a provider; provider implementations must not leak API-specific names into domain code. */
@JvmInline
value class DataProviderId(val value: String) {
    init {
        require(value.isNotBlank()) { "Provider id must not be blank." }
    }
}

object DataProviders {
    val TENCENT = DataProviderId("tencent")
    val SINA = DataProviderId("sina")
    val US = DataProviderId("us")
    val KOREA = DataProviderId("korea")
}

/** Keeps existing UI/result contracts usable while provider IDs are introduced incrementally. */
fun DataProviderId.toLegacyMarketSource(): MarketSource? = when (this) {
    DataProviders.TENCENT -> MarketSource.TENCENT
    DataProviders.SINA -> MarketSource.SINA
    else -> null
}

fun MarketSource.toLegacyProviderId(): DataProviderId? = when (this) {
    MarketSource.TENCENT -> DataProviders.TENCENT
    MarketSource.SINA -> DataProviders.SINA
    MarketSource.CACHE,
    MarketSource.MIXED,
    -> null
}

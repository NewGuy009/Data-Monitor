package com.example.mysecondapp.domain.model

sealed interface MarketDataResult<out T> {
    data class Success<T>(
        val value: T,
        val source: MarketSource,
        val fetchedAtMillis: Long,
        val providerId: DataProviderId? = source.toLegacyProviderId(),
    ) : MarketDataResult<T>

    data class Failure(
        val error: MarketError,
    ) : MarketDataResult<Nothing>
}

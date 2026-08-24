package com.example.mysecondapp.domain.model

sealed interface MarketError {
    data class Network(val message: String? = null) : MarketError

    data class EmptyResponse(
        val source: MarketSource,
        val stockCodes: List<String>,
    ) : MarketError

    data class ParseFailure(
        val source: MarketSource,
        val rawPayloadPreview: String,
    ) : MarketError

    data class UnsupportedSymbol(
        val market: String,
        val code: String,
        val reason: String,
    ) : MarketError

    data class Unknown(val message: String? = null) : MarketError
}

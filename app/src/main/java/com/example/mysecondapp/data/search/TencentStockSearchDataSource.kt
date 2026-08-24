package com.example.mysecondapp.data.search

import com.example.mysecondapp.data.decodeUnicodeEscapes
import com.example.mysecondapp.data.network.TencentStockSearchApi
import com.example.mysecondapp.data.provider.TencentSymbolMapper
import com.example.mysecondapp.domain.model.MarketDataResult
import com.example.mysecondapp.domain.model.MarketError
import com.example.mysecondapp.domain.model.MarketSource
import com.example.mysecondapp.domain.model.StockMarket
import com.example.mysecondapp.domain.model.StockSearchItem
import javax.inject.Inject
import javax.inject.Singleton

/** Parses Tencent's `v_hint` suggestion protocol into supported A-share search results. */
@Singleton
class TencentStockSearchDataSource @Inject constructor(
    private val tencentStockSearchApi: TencentStockSearchApi,
) : StockSearchDataSource {

    override suspend fun search(query: String): MarketDataResult<List<StockSearchItem>> {
        val normalizedQuery = query.trim()
        val fetchedAtMillis = System.currentTimeMillis()
        if (normalizedQuery.isBlank()) {
            return MarketDataResult.Success(emptyList(), MarketSource.TENCENT, fetchedAtMillis)
        }

        return runCatching {
            val payload = tencentStockSearchApi.search(query = normalizedQuery)
            if (payload.isBlank()) {
                return MarketDataResult.Failure(
                    MarketError.EmptyResponse(MarketSource.TENCENT, listOf(normalizedQuery)),
                )
            }
            MarketDataResult.Success(parseSuggestions(payload), MarketSource.TENCENT, fetchedAtMillis)
        }.getOrElse { error ->
            MarketDataResult.Failure(MarketError.Network(error.message))
        }
    }

    private fun parseSuggestions(payload: String): List<StockSearchItem> {
        val body = payload.substringAfter("v_hint=\"").substringBeforeLast("\"")
        if (body.isBlank() || body == NO_RESULT_MARKER) return emptyList()

        // Exclude HK shares, warrants, and markets whose detail data is not supported yet.
        return body.split(SUGGESTION_SEPARATOR)
            .mapNotNull { row ->
                val fields = row.split(FIELD_SEPARATOR)
                val providerSymbol = fields.getOrNull(0).orEmpty() + fields.getOrNull(1)?.trim().orEmpty()
                val identity = TencentSymbolMapper.toIdentity(providerSymbol) ?: return@mapNotNull null
                val market = StockMarket.fromMarketCode(identity.market)
                    ?.takeIf { it.capabilities.canSearch }
                    ?: return@mapNotNull null
                // Smartbox escapes Chinese characters as literal \uXXXX sequences.
                val name = fields.getOrNull(2)?.trim().orEmpty().decodeUnicodeEscapes()
                if (name.isBlank()) return@mapNotNull null
                StockSearchItem(identity.market, identity.code, name)
            }
            .distinctBy { item -> "${item.market}-${item.code}" }
    }

    private companion object {
        const val NO_RESULT_MARKER = "N"
        const val SUGGESTION_SEPARATOR = "^"
        const val FIELD_SEPARATOR = "~"
    }
}

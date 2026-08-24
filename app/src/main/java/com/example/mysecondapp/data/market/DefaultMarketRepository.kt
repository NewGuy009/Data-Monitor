package com.example.mysecondapp.data.market

import com.example.mysecondapp.domain.model.MarketDataResult
import com.example.mysecondapp.domain.model.MarketError
import com.example.mysecondapp.domain.model.MarketSource
import com.example.mysecondapp.domain.model.Quote
import com.example.mysecondapp.domain.model.QuoteSnapshot
import com.example.mysecondapp.domain.model.WatchlistItem
import com.example.mysecondapp.data.provider.FixedMarketDataProviderSelector
import com.example.mysecondapp.data.provider.MarketDataProviderSelector
import com.example.mysecondapp.data.provider.MarketDataProvider
import com.example.mysecondapp.domain.model.DataProviderId
import com.example.mysecondapp.domain.model.StockIdentity
import com.example.mysecondapp.domain.model.ProviderCapability
import com.example.mysecondapp.domain.repository.MarketRepository
import com.example.mysecondapp.domain.repository.WatchlistRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlin.math.max

private const val QUOTE_MEMORY_CACHE_TTL_MILLIS = 15_000L

@Singleton
class DefaultMarketRepository @Inject constructor(
    private val watchlistRepository: WatchlistRepository,
    private val providerSelector: MarketDataProviderSelector,
) : MarketRepository {

    constructor(
        watchlistRepository: WatchlistRepository,
        tencentMarketDataSource: TencentMarketDataSource,
        sinaMarketDataSource: SinaMarketDataSource,
    ) : this(
        watchlistRepository = watchlistRepository,
        providerSelector = FixedMarketDataProviderSelector(
            quoteSources = listOf(tencentMarketDataSource, sinaMarketDataSource),
        ),
    )

    private val cachedSnapshots = MutableStateFlow<Map<String, CachedSnapshot>>(emptyMap())
    private val providerSnapshots = mutableMapOf<ProviderQuoteCacheKey, CachedSnapshot>()

    override fun observeSnapshots(): Flow<List<QuoteSnapshot>> = combine(
        watchlistRepository.observeWatchlistItems(),
        cachedSnapshots,
    ) { watchlistItems, cache ->
        watchlistItems.map { item ->
            val cached = cache[item.cacheKey()]
            QuoteSnapshot(
                watchlistItem = item,
                quote = cached?.quote,
                refreshedAtMillis = cached?.refreshedAtMillis,
                source = cached?.source,
                providerId = cached?.providerId,
                error = cached?.error,
            )
        }
    }

    override suspend fun refreshWatchlistQuotes(): MarketDataResult<List<Quote>> {
        val watchlistItems = watchlistRepository.observeWatchlistItems().first()
        if (watchlistItems.isEmpty()) {
            return MarketDataResult.Success(
                value = emptyList(),
                source = MarketSource.CACHE,
                fetchedAtMillis = System.currentTimeMillis(),
            )
        }

        val now = System.currentTimeMillis()
        val requestedKeys = watchlistItems.map { item -> item.cacheKey() }
        val providers = providerSelector.quoteProviders(watchlistItems.map { item -> item.market }.toSet())

        val freshSnapshots = resolveFreshSnapshots(watchlistItems, providers, now)
        if (freshSnapshots != null) {
            cachedSnapshots.value = freshSnapshots
            // 短时间内重复刷新直接复用内存结果，避免两个公开源被连续打爆。
            val cachedQuotes = requestedKeys.mapNotNull { key -> cachedSnapshots.value[key]?.quote }
            return MarketDataResult.Success(
                value = cachedQuotes,
                source = MarketSource.CACHE,
                fetchedAtMillis = now,
            )
        }

        val sourceResults = mutableListOf<MarketDataResult<List<Quote>>>()
        val mergedQuotes = linkedMapOf<String, Quote>()
        var remainingItems = watchlistItems
        providers.forEach { provider ->
            if (remainingItems.isEmpty()) return@forEach
            val source = provider.marketDataSource ?: return@forEach
            val supportedItems = remainingItems.filter { item ->
                provider.capabilities.supports(item.market, ProviderCapability.QUOTE)
            }
            if (supportedItems.isEmpty()) return@forEach
            val providerItems = supportedItems.mapNotNull { item ->
                provider.symbolMapper.toProviderSymbol(StockIdentity(item.market, item.code))?.let { symbol ->
                    item to symbol
                }
            }
            if (providerItems.isEmpty()) return@forEach
            val result = source.fetchQuotes(providerItems.map { (_, symbol) -> symbol })
            sourceResults += result
            result.successQuotes(provider.id).forEach { (key, quote) ->
                if (key !in mergedQuotes) mergedQuotes[key] = quote
            }
            remainingItems = watchlistItems.filter { item -> item.cacheKey() !in mergedQuotes.keys }
        }

        if (mergedQuotes.isEmpty()) {
            val error = sourceResults.asReversed().firstNotNullOfOrNull { result -> result.failureError() }
                ?: watchlistItems.firstOrNull()?.let { item ->
                    MarketError.UnsupportedSymbol(
                        market = item.market,
                        code = item.code,
                        reason = "No selected provider has an executable quote source for this market.",
                    )
                }
                ?: MarketError.Unknown("No market source returned usable quotes.")
            updateFailureSnapshots(
                watchlistItems = watchlistItems,
                error = error,
                refreshedAtMillis = now,
            )
            return MarketDataResult.Failure(error)
        }

        val successfulSources = sourceResults.mapNotNull { result ->
            (result as? MarketDataResult.Success)?.source
        }.distinct()
        val effectiveSource = when {
            successfulSources.size > 1 -> MarketSource.MIXED
            successfulSources.size == 1 -> successfulSources.single()
            else -> MarketSource.CACHE
        }

        val refreshTime = sourceResults.maxOfOrNull { result -> result.fetchedAtMillisOr(now) } ?: now

        updateSuccessSnapshots(
            watchlistItems = watchlistItems,
            quotes = mergedQuotes,
            source = effectiveSource,
            refreshedAtMillis = refreshTime,
            fallbackError = if (remainingItems.isNotEmpty()) {
                sourceResults.asReversed().firstNotNullOfOrNull { result -> result.failureError() }
            } else {
                null
            },
        )

        return MarketDataResult.Success(
            value = watchlistItems.mapNotNull { item -> mergedQuotes[item.cacheKey()] },
            source = effectiveSource,
            fetchedAtMillis = refreshTime,
        )
    }

    private fun resolveFreshSnapshots(
        watchlistItems: List<WatchlistItem>,
        providers: List<MarketDataProvider>,
        now: Long,
    ): Map<String, CachedSnapshot>? {
        if (watchlistItems.isEmpty()) return null

        return buildMap {
            watchlistItems.forEach { item ->
                val cached = providers.asSequence().mapNotNull { provider ->
                    providerSnapshots[ProviderQuoteCacheKey(item.cacheKey(), provider.id)]
                }.firstOrNull { candidate ->
                    candidate.quote != null &&
                        now - candidate.refreshedAtMillis <= QUOTE_MEMORY_CACHE_TTL_MILLIS
                } ?: return null
                put(item.cacheKey(), cached)
            }
        }
    }

    private fun updateSuccessSnapshots(
        watchlistItems: List<WatchlistItem>,
        quotes: Map<String, Quote>,
        source: MarketSource,
        refreshedAtMillis: Long,
        fallbackError: MarketError?,
    ) {
        cachedSnapshots.value = watchlistItems.associate { item ->
            val previous = cachedSnapshots.value[item.cacheKey()]
            val quote = quotes[item.cacheKey()] ?: previous?.quote
            val snapshot = CachedSnapshot(
                quote = quote,
                refreshedAtMillis = refreshedAtMillis,
                source = quote?.source ?: previous?.source ?: source,
                providerId = quote?.providerId ?: previous?.providerId,
                // 某只股票这次没拉到但缓存里还有旧值时，保住旧值并把错误挂在快照上。
                error = when {
                    quote == null -> MarketError.EmptyResponse(
                        source = source,
                        stockCodes = listOf(item.cacheKey()),
                    )
                    item.cacheKey() !in quotes.keys -> fallbackError
                    else -> null
                },
            )
            quote?.providerId?.let { providerId ->
                providerSnapshots[ProviderQuoteCacheKey(item.cacheKey(), providerId)] = snapshot
            }
            item.cacheKey() to snapshot
        }
    }

    private fun updateFailureSnapshots(
        watchlistItems: List<WatchlistItem>,
        error: MarketError,
        refreshedAtMillis: Long,
    ) {
        cachedSnapshots.value = watchlistItems.associate { item ->
            val previous = cachedSnapshots.value[item.cacheKey()]
            item.cacheKey() to CachedSnapshot(
                // 两个源都失败时，若本地内存里还有旧快照，就先保住页面已有数据。
                quote = previous?.quote,
                refreshedAtMillis = refreshedAtMillis,
                source = previous?.source,
                providerId = previous?.providerId,
                error = error,
            )
        }
    }

    private data class CachedSnapshot(
        val quote: Quote?,
        val refreshedAtMillis: Long,
        val source: MarketSource?,
        val providerId: DataProviderId?,
        val error: MarketError?,
    )

    private data class ProviderQuoteCacheKey(
        val identityKey: String,
        val providerId: DataProviderId,
    )
}

private fun WatchlistItem.cacheKey(): String = "$market-$code"

private fun Quote.cacheKey(): String = "$market-$code"

private fun MarketDataResult<List<Quote>>?.successQuotes(providerId: DataProviderId): Map<String, Quote> = when (this) {
    is MarketDataResult.Success -> value
        .map { quote -> quote.copy(providerId = providerId) }
        .associateBy { quote -> quote.cacheKey() }
    is MarketDataResult.Failure -> emptyMap()
    null -> emptyMap()
}

private fun MarketDataResult<List<Quote>>?.failureError(): MarketError? = when (this) {
    is MarketDataResult.Failure -> error
    else -> null
}

private fun MarketDataResult<List<Quote>>?.fetchedAtMillisOr(defaultValue: Long): Long = when (this) {
    is MarketDataResult.Success -> fetchedAtMillis
    else -> defaultValue
}

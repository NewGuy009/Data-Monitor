package com.example.mysecondapp.data.search

import com.example.mysecondapp.data.provider.FixedSearchMarketDataProviderSelector
import com.example.mysecondapp.data.provider.MarketDataProviderSelector
import com.example.mysecondapp.domain.model.MarketDataResult
import com.example.mysecondapp.domain.model.StockSearchItem
import com.example.mysecondapp.domain.repository.StockSearchRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultStockSearchRepository @Inject constructor(
    private val providerSelector: MarketDataProviderSelector,
) : StockSearchRepository {

    /** Compatibility constructor retained for focused search tests and older callers. */
    constructor(remoteDataSource: StockSearchDataSource) : this(
        providerSelector = FixedSearchMarketDataProviderSelector(remoteDataSource),
    )

    override suspend fun search(
        query: String,
        limit: Int,
    ): List<StockSearchItem> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isBlank()) {
            return emptyList()
        }

        val safeLimit = limit.coerceAtLeast(1)
        val localMatches = localStockDictionary
            .mapNotNull { item ->
                val score = item.matchScore(normalizedQuery) ?: return@mapNotNull null
                SearchMatch(item = item.toDomainModel(), score = score)
            }

        // Search follows provider preference; local pinyin/code matches remain usable offline.
        val searchMarkets = setOf("SH", "SZ", "BJ")
        val remoteMatches = providerSelector.searchProviders(searchMarkets)
            .flatMap { provider ->
                val source = provider.searchDataSource ?: return@flatMap emptyList()
                when (val result = source.search(normalizedQuery)) {
                    is MarketDataResult.Success -> result.value
                        .filter { item ->
                            provider.capabilities.supports(item.market, com.example.mysecondapp.domain.model.ProviderCapability.SEARCH) &&
                                provider.symbolMapper.toProviderSymbol(
                                    com.example.mysecondapp.domain.model.StockIdentity(item.market, item.code),
                                ) != null
                        }
                        .mapIndexed { index, item ->
                            SearchMatch(item = item, score = REMOTE_RESULT_SCORE_OFFSET + index)
                        }
                    is MarketDataResult.Failure -> emptyList()
                }
            }

        return (localMatches + remoteMatches)
            .distinctBy { match -> "${match.item.market}-${match.item.code}" }
            .sortedWith(
                compareBy<SearchMatch> { it.score }
                    .thenBy { it.item.market }
                    .thenBy { it.item.code },
            )
            .take(safeLimit)
            .map { match -> match.item }
    }

    private companion object {
        const val REMOTE_RESULT_SCORE_OFFSET = 100
    }
}

private data class SearchMatch(
    val item: StockSearchItem,
    val score: Int,
)

private data class LocalStockDictionaryItem(
    val market: String,
    val code: String,
    val name: String,
    val pinyinFull: String,
    val pinyinShort: String,
) {
    fun toDomainModel(): StockSearchItem = StockSearchItem(
        market = market,
        code = code,
        name = name,
    )

    fun matchScore(query: String): Int? {
        // Lower score means a better hit, so exact code/name matches rank first.
        return when {
            code.equals(query, ignoreCase = true) -> 0
            "${market.lowercase()}$code" == query -> 1
            name == query -> 2
            code.startsWith(query) -> 3
            name.contains(query, ignoreCase = true) -> 4
            pinyinShort.startsWith(query) -> 5
            pinyinFull.startsWith(query) -> 6
            code.contains(query) -> 7
            pinyinShort.contains(query) -> 8
            pinyinFull.contains(query) -> 9
            else -> null
        }
    }
}

private val localStockDictionary = listOf(
    LocalStockDictionaryItem("SH", "600000", "浦发银行", "pufayinhang", "pfyh"),
    LocalStockDictionaryItem("SZ", "000001", "平安银行", "pinganyinhang", "payh"),
    LocalStockDictionaryItem("SH", "600036", "招商银行", "zhaoshangyinhang", "zsyh"),
    LocalStockDictionaryItem("SH", "600519", "贵州茅台", "guizhoumaotai", "gzmt"),
    LocalStockDictionaryItem("SZ", "000858", "五粮液", "wuliangye", "wly"),
    LocalStockDictionaryItem("SZ", "300750", "宁德时代", "ningdeshidai", "ndsd"),
    LocalStockDictionaryItem("SZ", "002594", "比亚迪", "biyadi", "byd"),
    LocalStockDictionaryItem("SH", "601318", "中国平安", "zhongguopingan", "zgpa"),
    LocalStockDictionaryItem("SH", "600887", "伊利股份", "yiligufen", "ylgf"),
    LocalStockDictionaryItem("SH", "688981", "中芯国际", "zhongxinguoji", "zxgj"),
    LocalStockDictionaryItem("SZ", "000333", "美的集团", "meidejituan", "mdjt"),
    LocalStockDictionaryItem("SH", "600276", "恒瑞医药", "hengruiyiyao", "hryy"),
    LocalStockDictionaryItem("SZ", "002415", "海康威视", "haikangweishi", "hkws"),
    LocalStockDictionaryItem("SH", "601398", "工商银行", "gongshangyinhang", "gsyh"),
    LocalStockDictionaryItem("SH", "601288", "农业银行", "nongyeyinhang", "nyyh"),
    LocalStockDictionaryItem("SH", "601988", "中国银行", "zhongguoyinhang", "zgyh"),
    LocalStockDictionaryItem("SH", "600030", "中信证券", "zhongxinzhengquan", "zxzq"),
    LocalStockDictionaryItem("SZ", "300059", "东方财富", "dongfangcaifu", "dfcf"),
    LocalStockDictionaryItem("SH", "601012", "隆基绿能", "longjiluneng", "ljln"),
    LocalStockDictionaryItem("SZ", "000651", "格力电器", "gelidianqi", "gldq"),
)

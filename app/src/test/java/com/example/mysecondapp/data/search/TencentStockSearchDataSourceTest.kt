package com.example.mysecondapp.data.search

import com.example.mysecondapp.data.network.TencentStockSearchApi
import com.example.mysecondapp.domain.model.MarketDataResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TencentStockSearchDataSourceTest {

    @Test
    fun `search parses supported sh sz and star market suggestions`() = runBlocking {
        val dataSource = TencentStockSearchDataSource(
            FakeTencentStockSearchApi(
                """v_hint="sh~688981~\u4e2d\u82af\u56fd\u9645~zxgj~GP-A-KCB^sz~300750~CATL~ndsd~GP^hk~00981~SMIC~zxgj~GP"""",
            ),
        )

        val result = dataSource.search("smic")

        assertTrue(result is MarketDataResult.Success)
        val symbols = (result as MarketDataResult.Success).value
        assertEquals(listOf("SH-688981", "SZ-300750"), symbols.map { "${it.market}-${it.code}" })
        assertEquals("中芯国际", symbols.first().name)
    }

    @Test
    fun `search returns empty result for tencent no-result marker`() = runBlocking {
        val dataSource = TencentStockSearchDataSource(FakeTencentStockSearchApi("v_hint=\"N\""))

        val result = dataSource.search("not-found")

        assertTrue(result is MarketDataResult.Success)
        assertTrue((result as MarketDataResult.Success).value.isEmpty())
    }
}

private class FakeTencentStockSearchApi(
    private val response: String,
) : TencentStockSearchApi {
    override suspend fun search(version: Int, query: String, type: String): String = response
}

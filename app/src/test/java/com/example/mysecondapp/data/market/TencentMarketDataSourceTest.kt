package com.example.mysecondapp.data.market

import com.example.mysecondapp.data.network.TencentStockApi
import com.example.mysecondapp.domain.model.MarketDataResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TencentMarketDataSourceTest {

    @Test
    fun `fetchQuotes parses core quote fields from tencent payload`() = runBlocking {
        val payload = buildTencentLine(
            code = "600519",
            name = "贵州茅台",
            latestPrice = "1450.50",
            previousClosePrice = "1440.00",
            openPrice = "1448.00",
            highPrice = "1462.88",
            lowPrice = "1442.01",
            volumeHands = "3210",
            turnoverWan = "125678.90",
        )
        val dataSource = TencentMarketDataSource(
            tencentStockApi = FakeTencentStockApi(payload),
        )

        val result = dataSource.fetchQuotes(listOf("sh600519"))

        assertTrue(result is MarketDataResult.Success)
        val success = result as MarketDataResult.Success
        assertEquals(1, success.value.size)

        val quote = success.value.first()
        assertEquals("SH", quote.market)
        assertEquals("600519", quote.code)
        assertEquals("贵州茅台", quote.name)
        assertEquals(1450.50, quote.latestPrice, 0.0001)
        assertEquals(1440.00, quote.previousClosePrice, 0.0001)
        assertEquals(1448.00, quote.openPrice, 0.0001)
        assertEquals(1462.88, quote.highPrice, 0.0001)
        assertEquals(1442.01, quote.lowPrice, 0.0001)
        assertEquals(10.50, quote.changeAmount, 0.0001)
        assertEquals(0.729166, quote.changePercent, 0.0001)
        assertEquals(321_000L, quote.volume)
        assertEquals(1_256_789_000.0, quote.turnover ?: 0.0, 0.0001)
    }

    @Test
    fun `fetchQuotes returns parse failure when payload does not contain enough fields`() = runBlocking {
        val dataSource = TencentMarketDataSource(
            tencentStockApi = FakeTencentStockApi("v_sh600519=\"broken~payload\";"),
        )

        val result = dataSource.fetchQuotes(listOf("sh600519"))

        assertTrue(result is MarketDataResult.Failure)
    }

    @Test
    fun `fetchQuotes parses representative sh sz growth and star symbols`() = runBlocking {
        val dataSource = TencentMarketDataSource(
            tencentStockApi = FakeTencentStockApi(
                listOf(
                    buildTencentLine("sh", "600000", "浦发银行"),
                    buildTencentLine("sz", "000001", "平安银行"),
                    buildTencentLine("sz", "300750", "宁德时代"),
                    buildTencentLine("sh", "688981", "中芯国际"),
                ).joinToString("\n"),
            ),
        )

        val result = dataSource.fetchQuotes(listOf("sh600000", "sz000001", "sz300750", "sh688981"))

        assertTrue(result is MarketDataResult.Success)
        assertEquals(
            listOf("600000", "000001", "300750", "688981"),
            (result as MarketDataResult.Success).value.map { quote -> quote.code },
        )
    }

    @Test
    fun `fetchQuotes returns empty response when provider has no quote`() = runBlocking {
        val dataSource = TencentMarketDataSource(
            tencentStockApi = FakeTencentStockApi(""),
        )

        val result = dataSource.fetchQuotes(listOf("sh600000"))

        assertTrue(result is MarketDataResult.Failure)
        assertTrue((result as MarketDataResult.Failure).error is com.example.mysecondapp.domain.model.MarketError.EmptyResponse)
    }
}

private class FakeTencentStockApi(
    private val payload: String,
) : TencentStockApi {
    override suspend fun getQuotes(codes: String): String = payload
}

private fun buildTencentLine(
    marketPrefix: String = "sh",
    code: String,
    name: String = "测试股票",
    latestPrice: String = "10.25",
    previousClosePrice: String = "10.00",
    openPrice: String = "10.10",
    highPrice: String = "10.30",
    lowPrice: String = "9.98",
    volumeHands: String = "3210",
    turnoverWan: String = "125678.90",
): String {
    val fields = MutableList(38) { "" }
    fields[1] = name
    fields[2] = code
    fields[3] = latestPrice
    fields[4] = previousClosePrice
    fields[5] = openPrice
    fields[33] = highPrice
    fields[34] = lowPrice
    fields[36] = volumeHands
    fields[37] = turnoverWan
    return "v_${marketPrefix}$code=\"${fields.joinToString("~")}\";"
}

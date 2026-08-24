package com.example.mysecondapp.data.market

import com.example.mysecondapp.data.network.SinaStockApi
import com.example.mysecondapp.domain.model.MarketDataResult
import java.nio.charset.Charset
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SinaMarketDataSourceTest {

    @Test
    fun `fetchQuotes decodes gbk payload and parses quote fields`() = runBlocking {
        val payload = buildSinaLine(
            market = "sh",
            code = "600000",
            name = "浦发银行",
            openPrice = "10.10",
            previousClosePrice = "10.00",
            latestPrice = "10.25",
            highPrice = "10.30",
            lowPrice = "9.98",
            volume = "87654321",
            turnover = "123456789.12",
        )
        val dataSource = SinaMarketDataSource(
            sinaStockApi = FakeSinaStockApi(payload),
        )

        val result = dataSource.fetchQuotes(listOf("sh600000"))

        assertTrue(result is MarketDataResult.Success)
        val success = result as MarketDataResult.Success
        assertEquals(1, success.value.size)

        val quote = success.value.first()
        assertEquals("SH", quote.market)
        assertEquals("600000", quote.code)
        assertEquals("浦发银行", quote.name)
        assertEquals(10.25, quote.latestPrice, 0.0001)
        assertEquals(10.00, quote.previousClosePrice, 0.0001)
        assertEquals(10.10, quote.openPrice, 0.0001)
        assertEquals(10.30, quote.highPrice, 0.0001)
        assertEquals(9.98, quote.lowPrice, 0.0001)
        assertEquals(0.25, quote.changeAmount, 0.0001)
        assertEquals(2.5, quote.changePercent, 0.0001)
        assertEquals(87_654_321L, quote.volume)
        assertEquals(123_456_789.12, quote.turnover ?: 0.0, 0.0001)
    }

    @Test
    fun `fetchQuotes returns parse failure for malformed payload`() = runBlocking {
        val dataSource = SinaMarketDataSource(
            sinaStockApi = FakeSinaStockApi("var hq_str_sh600000=\"broken,payload\";"),
        )

        val result = dataSource.fetchQuotes(listOf("sh600000"))

        assertTrue(result is MarketDataResult.Failure)
    }
}

private class FakeSinaStockApi(
    private val payload: String,
) : SinaStockApi {
    override suspend fun getQuotes(codes: String): ResponseBody {
        return payload
            .toByteArray(Charset.forName("GBK"))
            .toResponseBody("text/plain".toMediaType())
    }
}

private fun buildSinaLine(
    market: String,
    code: String,
    name: String,
    openPrice: String,
    previousClosePrice: String,
    latestPrice: String,
    highPrice: String,
    lowPrice: String,
    volume: String,
    turnover: String,
): String {
    val fields = MutableList(32) { "0" }
    fields[0] = name
    fields[1] = openPrice
    fields[2] = previousClosePrice
    fields[3] = latestPrice
    fields[4] = highPrice
    fields[5] = lowPrice
    fields[8] = volume
    fields[9] = turnover
    return "var hq_str_${market}${code}=\"${fields.joinToString(",")}\";"
}
